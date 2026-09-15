#!/usr/bin/env bash
# Nightly logical backup of the QA database, keeping the last 7 days.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

set -a
# shellcheck disable=SC1091
source .env
set +a

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
target="backups/${POSTGRES_DB:-appdb}-${stamp}.sql.gz"

docker compose exec -T db \
    pg_dump -U "${POSTGRES_USER:-app}" -d "${POSTGRES_DB:-appdb}" \
    | gzip > "$target"

echo "Wrote $target ($(du -h "$target" | cut -f1))"

# Keep 7 days. -mtime +7 is deliberate: a same-day rerun never deletes today's.
find backups -name '*.sql.gz' -type f -mtime +7 -delete
