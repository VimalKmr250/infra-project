# Runbook

## Deploy flow

```
merge to main  ->  CI tests  ->  multi-arch image  ->  ghcr :qa  ->  Pi pulls (<=2 min)
git tag v1.2.3 ->  copy digest to Artifact Registry -> migrate -> Cloud Run -> smoke test
```

Production always runs an image that already ran on QA. A tag pointing at a
commit that never reached `main` fails fast, by design.

## Roll back production

Re-run the release workflow against the previous tag:

Actions -> Release to production -> Run workflow -> enter `v1.1.0`.

Or straight from Cloud Run, which is faster and needs no CI:

```bash
gcloud run services update-traffic vksiv-apps --region <REGION> --to-revisions <PREVIOUS>=100
```

```bash
gcloud run revisions list --service vksiv-apps --region <REGION>
```

**Migrations do not roll back with the image.** Every changeset has a
`--rollback` line, but reverting the schema is a deliberate act:

```bash
docker run --rm -v "$PWD/backend/src/main/resources/db/changelog:/changelog:ro" \
  liquibase/liquibase:5.0.3 --search-path=/changelog \
  --changelog-file=db.changelog-master.yaml --url="$SESSION_URL" \
  --username="$USER" --password="$PASS" rollbackCount 1
```

Prefer forward fixes. Write migrations so the previous image still works against
the new schema (add columns, don't rename in place), and rollback stays a
last resort rather than a routine.

## Logs

| Where | How |
|---|---|
| Production | `gcloud run services logs read vksiv-apps --region <REGION> --limit 100` |
| QA | `docker compose logs -f api` on the Pi |
| QA deploys | `journalctl -u vksiv-deploy.service -f` |
| CI | The GitHub Actions run |

QA and production emit ECS-formatted JSON, so Cloud Logging parses fields
rather than treating each line as opaque text.

## Which version is running?

```bash
curl -s https://<service>.run.app/actuator/info
```

`springBoot { buildInfo() }` publishes the version there, so you never have to
guess whether a deploy actually took effect.

## Health

- `/actuator/health` - overall
- `/actuator/health/readiness` - ready for traffic
- `/actuator/health/liveness` - process alive

Nothing else is exposed. Do not widen `management.endpoints.web.exposure.include`
on a service with `--allow-unauthenticated`.

## Database

**QA restore** (backups in `infra/pi/backups`, nightly, 7 days retained):

```bash
gunzip -c backups/appdb-YYYYMMDD-HHMMSS.sql.gz | docker compose exec -T db psql -U app -d appdb
```

**Production backups** are Supabase's own. Free-tier retention is short - check
what your plan actually provides before relying on it, and take a manual
`pg_dump` before anything irreversible.

## Common failures

**Production returns 401/500 right after a release.** Check `/actuator/health`.
If the container never started, the usual cause is a missing secret - confirm
`app-database-url` and `app-jwt-secret` exist and the runtime service account
has `secretmanager.secretAccessor`.

**`prepared statement "S_1" already exists`.** The runtime URL lost
`?prepareThreshold=0`, or it points at the session pooler rather than the
transaction pooler on 6543.

**Production is down and Supabase says "paused".** The project idled for a week.
Resume it in the dashboard and check `keepalive-supabase.yml` is still running.

**The Pi stopped updating.** `systemctl status vksiv-deploy.timer`, then
`journalctl -u vksiv-deploy.service -n 50`. Most often the GHCR token expired -
re-run `docker login ghcr.io` with a fresh PAT.

**The QA URL stopped working.** With a quick tunnel this is expected: the URL
changes whenever the tunnel restarts. `cat infra/pi/tunnel-url.txt` for the
current one, or move to a named tunnel.

**A release fails at "Verify the tagged commit has a QA-tested image".** The tag
is on a commit that never went through `main` CI. Tag a merged commit instead.
