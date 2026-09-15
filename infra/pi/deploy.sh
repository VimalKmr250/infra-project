#!/usr/bin/env bash
# Pulls the latest QA image and restarts the stack if it changed.
# Run by the vksiv-deploy systemd timer; safe to run by hand at any time.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

if [[ ! -f .env ]]; then
    echo "No .env here. Copy .env.example to .env and fill it in." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

# A named tunnel needs a domain on Cloudflare; without a token we fall back to
# a free quick tunnel with a URL that changes whenever the container restarts.
PROFILE=quick
if [[ -n "${CLOUDFLARE_TUNNEL_TOKEN:-}" ]]; then
    PROFILE=named
fi

compose() { docker compose --profile "$PROFILE" "$@"; }

before="$(docker image inspect --format '{{.Id}}' "${IMAGE}" 2>/dev/null || echo none)"

compose pull --quiet api
after="$(docker image inspect --format '{{.Id}}' "${IMAGE}" 2>/dev/null || echo none)"

if [[ "$before" == "$after" ]] && [[ -n "$(docker compose ps --quiet api 2>/dev/null)" ]]; then
    echo "Already up to date ($after); nothing to do."
    exit 0
fi

echo "Deploying ${IMAGE} (${before} -> ${after})"
compose up -d --remove-orphans
docker image prune -f >/dev/null

# Record the quick tunnel's current URL so it can be looked up without
# trawling container logs.
if [[ "$PROFILE" == "quick" ]]; then
    sleep 8
    url="$(docker logs vksiv-tunnel 2>&1 \
        | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' \
        | tail -1 || true)"
    if [[ -n "$url" ]]; then
        printf '%s\n' "$url" > tunnel-url.txt
        echo "QA URL: $url"
    else
        echo "Tunnel URL not visible yet; check: docker logs vksiv-tunnel" >&2
    fi
fi

echo "Deployed."
