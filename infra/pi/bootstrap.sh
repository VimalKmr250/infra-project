#!/usr/bin/env bash
#
# One-time setup for the Raspberry Pi 5 QA environment.
#
#   git clone <repo> ~/vksiv-apps
#   cd ~/vksiv-apps/infra/pi
#   cp .env.example .env && nano .env      # fill in secrets first
#   ./bootstrap.sh
#
# Safe to re-run: every step checks before acting.
set -euo pipefail

HERE="$(dirname "$(readlink -f "$0")")"
APP_DIR="$(readlink -f "${HERE}/../..")"
APP_USER="${SUDO_USER:-$USER}"

say() { printf '\n==> %s\n' "$*"; }

if [[ "$(uname -m)" != "aarch64" ]]; then
    echo "Warning: expected aarch64 (Pi 5 running 64-bit Raspberry Pi OS); found $(uname -m)." >&2
fi

# ---------------------------------------------------------------- docker -----
if ! command -v docker >/dev/null 2>&1; then
    say "Installing Docker"
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$APP_USER"
    echo "Added $APP_USER to the docker group. Log out and back in before continuing."
else
    say "Docker already installed: $(docker --version)"
fi

if ! docker compose version >/dev/null 2>&1; then
    say "Installing the Docker Compose plugin"
    sudo apt-get update -qq
    sudo apt-get install -y docker-compose-plugin
fi

# ------------------------------------------------------------------ .env -----
cd "$HERE"
if [[ ! -f .env ]]; then
    echo "No .env found. Copy .env.example to .env and fill it in first." >&2
    exit 1
fi
chmod 600 .env

set -a
# shellcheck disable=SC1091
source .env
set +a

for required in IMAGE POSTGRES_PASSWORD APP_JWT_SECRET; do
    if [[ -z "${!required:-}" ]]; then
        echo "$required is not set in .env" >&2
        exit 1
    fi
done

# ------------------------------------------------------------------ GHCR -----
# GHCR packages are private by default. A read-only PAT keeps the image private
# rather than making the package public.
if [[ -n "${GHCR_TOKEN:-}" && -n "${GHCR_USER:-}" ]]; then
    say "Logging in to ghcr.io as $GHCR_USER"
    printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
else
    say "No GHCR credentials in .env - assuming the package is public"
fi

# --------------------------------------------------------------- systemd -----
say "Installing systemd units"
for unit in vksiv-deploy.service vksiv-deploy.timer vksiv-backup.service vksiv-backup.timer; do
    sed -e "s#__APP_DIR__#${APP_DIR}#g" -e "s#__APP_USER__#${APP_USER}#g" \
        "systemd/${unit}" | sudo tee "/etc/systemd/system/${unit}" >/dev/null
done

sudo systemctl daemon-reload
sudo systemctl enable --now vksiv-deploy.timer vksiv-backup.timer

# -------------------------------------------------------------- first run ----
say "Running the first deploy"
./deploy.sh

say "Done"
echo "  Status:    systemctl status vksiv-deploy.timer"
echo "  Logs:      journalctl -u vksiv-deploy.service -f"
echo "  App logs:  docker compose logs -f api"
if [[ -f tunnel-url.txt ]]; then
    echo "  QA URL:    $(cat tunnel-url.txt)"
fi
