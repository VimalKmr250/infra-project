# vksiv-apps

Spring Boot + Angular + PostgreSQL, with three environments and a deployment
path that runs the same image in all of them.

| | Local | QA | Production |
|---|---|---|---|
| Runs on | Your laptop | Raspberry Pi 4 | Google Cloud Run |
| Arch | amd64 | arm64 | amd64 |
| Database | Postgres container | Postgres container | Supabase |
| Reached via | `localhost:4200` | Cloudflare Tunnel | `*.run.app` |
| Deployed by | you | merge to `main` | `git tag v*` |

Angular is compiled into the Spring Boot jar, so each environment runs a single
container serving both the API and the UI - one artifact, one service, no CORS.

## Quick start

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

```bash
./gradlew :backend:bootRun -PskipFrontend
```

```bash
cd frontend && npm start
```

Then open http://localhost:4200, register an account, and add a note.

Full details in [docs/local-development.md](docs/local-development.md).

## Layout

```
backend/     Spring Boot 4, Java 25, Liquibase, JWT auth
frontend/    Angular 22, built into the jar by Gradle
infra/
  local/     compose: Postgres (+ optional pgAdmin, full stack)
  pi/        compose, bootstrap, self-updating deploy, backups
  cloudrun/  reference service definition
docs/        setup per environment, plus the runbook
```

`backend/.../note/` and its Liquibase changeset are a sample feature that exists
to prove the whole path works. Delete both once you have real features.

## How a change reaches production

```
merge to main  ->  CI  ->  multi-arch image  ->  ghcr :qa  ->  Pi pulls (<=2 min)
git tag v1.2.3 ->  copy that digest to Artifact Registry -> migrate -> Cloud Run
```

Tagging never rebuilds. It copies the image the Pi already ran, by digest, so
production runs exactly what was tested. Tagging a commit that never passed CI
on `main` fails immediately.

## Documentation

- [Local development](docs/local-development.md)
- [QA on the Raspberry Pi](docs/pi-setup.md)
- [Production setup](docs/production-setup.md) - GCP, Supabase, keyless CI auth
- [Runbook](docs/runbook.md) - rollback, logs, common failures

## Versions

Pinned in `gradle/libs.versions.toml`.

| | |
|---|---|
| Java | 25 (LTS) |
| Spring Boot | 4.1.1 |
| Gradle | 9.7.1 |
| Node | 24.21.0 (LTS) |
| Angular | 22 |
| PostgreSQL | 17 |

Node is pinned because Angular 22 rejects odd-numbered Node releases; the Gradle
build downloads its own copy and ignores whatever is on your `PATH`.

## Cost

$0/month for GHCR, GitHub Actions, Cloudflare Tunnel, Supabase and Cloud Run's
always-free tier. Artifact Registry storage for released images costs pennies
past the 0.5 GB free allowance. A domain (~$10/year) is optional but gives QA a
stable hostname instead of a URL that changes on every restart.
