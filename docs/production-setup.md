# Production - Cloud Run + Supabase

One-time setup. After this, releases are `git tag v1.0.0 && git push --tags`.

## Why the image is copied between registries

Cloud Run can only pull from Artifact Registry, not GHCR. The release workflow
therefore copies the QA-tested image into Artifact Registry **by digest**
(`docker buildx imagetools create`), which preserves the digest - so production
still runs byte-for-byte what the Pi ran. Nothing is rebuilt.

Only released versions are copied, so Artifact Registry stays small. Add a
cleanup policy keeping the last few versions; storage beyond the 0.5 GB free
allowance costs about $0.10/GB/month, so expect pennies rather than zero.

## 1. Supabase

Create a project and note the database password.

You need two connection strings from Connect -> ORMs/JDBC. Both go through
Supavisor, the connection pooler - Supabase's *direct* connections are IPv6-only
and Cloud Run cannot reach them.

| Purpose | Port | Mode | Used by |
|---|---|---|---|
| Application runtime | 6543 | transaction | Cloud Run |
| Migrations | 5432 | session | The Liquibase CI step |

The runtime URL must end with `?prepareThreshold=0`. Transaction pooling cannot
use server-side prepared statements, and without this you get
"prepared statement S_1 already exists" once traffic ramps up.

```
jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?prepareThreshold=0
```

Migrations use the session pooler because Liquibase takes an advisory lock that
must live across statements, which transaction pooling cannot provide.

## 2. Google Cloud

```bash
export PROJECT_ID=your-project
export REGION=europe-west1

gcloud projects create $PROJECT_ID
gcloud config set project $PROJECT_ID
# Billing must be attached even to stay inside the always-free tier.
gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
    secretmanager.googleapis.com iamcredentials.googleapis.com

gcloud artifacts repositories create app \
    --repository-format=docker --location=$REGION
```

### Secrets

```bash
printf '%s' 'jdbc:postgresql://...:6543/postgres?prepareThreshold=0' \
  | gcloud secrets create app-database-url --data-file=-

openssl rand -base64 48 | tr -d '\n' \
  | gcloud secrets create app-jwt-secret --data-file=-
```

### Service account

```bash
gcloud iam service-accounts create github-deployer

for role in roles/run.admin roles/artifactregistry.writer \
            roles/iam.serviceAccountUser roles/secretmanager.secretAccessor; do
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-deployer@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="$role"
done
```

### Workload Identity Federation

Keyless: GitHub exchanges a short-lived OIDC token for Google credentials, so no
service-account JSON key ever exists.

```bash
gcloud iam workload-identity-pools create github --location=global

gcloud iam workload-identity-pools providers create-oidc github \
  --location=global --workload-identity-pool=github \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping='google.subject=assertion.sub,attribute.repository=assertion.repository' \
  --attribute-condition='assertion.repository=="OWNER/REPO"'
```

The attribute condition matters: without it, any repository on GitHub could
assume this identity.

```bash
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')

gcloud iam service-accounts add-iam-policy-binding \
  github-deployer@$PROJECT_ID.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/OWNER/REPO"
```

## 3. GitHub configuration

Repository **variables** (Settings -> Secrets and variables -> Actions -> Variables):

| Name | Example |
|---|---|
| `GCP_PROJECT_ID` | `your-project` |
| `GCP_REGION` | `europe-west1` |
| `AR_REPOSITORY` | `app` |
| `CLOUD_RUN_SERVICE` | `vksiv-apps` |
| `GCP_SERVICE_ACCOUNT` | `github-deployer@your-project.iam.gserviceaccount.com` |
| `GCP_WIF_PROVIDER` | `projects/<number>/locations/global/workloadIdentityPools/github/providers/github` |

Repository **secrets**:

| Name | Purpose |
|---|---|
| `SUPABASE_SESSION_JDBC_URL` | Session-pooler JDBC URL (port 5432), for migrations |
| `SUPABASE_DB_USER` | Usually `postgres.<project-ref>` |
| `SUPABASE_DB_PASSWORD` | Database password |
| `SUPABASE_SESSION_URL` | `postgresql://...` form, for the keepalive workflow |

Create a GitHub **Environment** named `production`. Add yourself as a required
reviewer if you want a manual gate before each release.

## 4. First deploy

```bash
git tag v0.1.0
git push --tags
```

Watch the run. It verifies the image exists, copies it to Artifact Registry,
applies migrations, deploys, and smoke-tests `/actuator/health`.

## Free-tier notes

- Cloud Run always-free covers 2M requests, 180k vCPU-seconds and 360k GiB-seconds
  a month. `min-instances=0` keeps you there; `max-instances=3` caps the damage
  if something goes wrong.
- `min-instances=0` means the first request after idle pays a cold start of a
  few seconds. Setting it to 1 removes that and leaves the free tier.
- Supabase free projects pause after about a week of inactivity.
  `keepalive-supabase.yml` pings the database twice a week to prevent it.
