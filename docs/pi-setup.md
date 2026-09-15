# QA environment - Raspberry Pi 5

The Pi runs the QA environment. It never accepts an inbound connection: it pulls
its image from GHCR and reaches the internet outbound through a Cloudflare
tunnel. No router port is forwarded, and no firewall rule is needed.

## What runs there

| Container | Purpose |
|---|---|
| `vksiv-api` | The application image, `qa` tag, `arm64` |
| `vksiv-db` | PostgreSQL 17, data on the Pi's disk |
| `vksiv-tunnel` | `cloudflared`, exposes the API over HTTPS |

A systemd timer runs `deploy.sh` every two minutes. It pulls the `qa` tag and
restarts the stack only when the digest actually changed, so a merge to `main`
is live on the Pi within about two minutes with no push access to the Pi at all.

## One-time setup

Use 64-bit Raspberry Pi OS. Boot from SSD rather than an SD card if you can -
Postgres on an SD card wears it out and is slow.

```bash
sudo apt update && sudo apt full-upgrade -y
git clone https://github.com/OWNER/REPO.git ~/vksiv-apps
cd ~/vksiv-apps/infra/pi
cp .env.example .env
nano .env
```

Fill in `.env` before going further:

- `IMAGE` - `ghcr.io/OWNER/REPO:qa`, all lowercase
- `POSTGRES_PASSWORD` - `openssl rand -base64 24`
- `APP_JWT_SECRET` - `openssl rand -base64 48`
- `GHCR_USER` / `GHCR_TOKEN` - a GitHub PAT with `read:packages`, because GHCR
  packages are private by default. Leave both blank only if you deliberately
  made the package public.
- `CLOUDFLARE_TUNNEL_TOKEN` - leave blank for now (see below)

Then:

```bash
./bootstrap.sh
```

It installs Docker if missing, logs in to GHCR, installs and enables the
systemd timers, and runs the first deploy. It is safe to re-run.

If it just installed Docker, log out and back in before re-running, so your
user picks up the `docker` group.

## Reaching it

**Without a domain (default).** `cloudflared` opens a *quick tunnel* and prints
a URL like `https://random-words-1234.trycloudflare.com`. `deploy.sh` writes it
to `infra/pi/tunnel-url.txt`:

```bash
cat ~/vksiv-apps/infra/pi/tunnel-url.txt
```

This URL **changes every time the tunnel container restarts** - including on
every deploy. That is the free tier's limitation, not a misconfiguration.

**With a domain on Cloudflare (recommended).** Create a named tunnel in
Zero Trust -> Networks -> Tunnels, point a hostname such as `qa.example.com` at
`http://api:8080`, and put the tunnel token in `.env` as
`CLOUDFLARE_TUNNEL_TOKEN`. `deploy.sh` detects it and switches profiles
automatically - the URL then never changes. A domain costs roughly $10/year and
is the single best improvement you can make to this environment.

You can also put Cloudflare Access in front of the hostname so only your own
Google or GitHub account can load QA.

**Not wanting anything public at all.** Install Tailscale on the Pi instead and
skip cloudflared entirely; QA is then reachable only from your own devices.

## Day-to-day

```bash
cd ~/vksiv-apps/infra/pi

docker compose ps                        # what is running
docker compose logs -f api               # application logs
journalctl -u vksiv-deploy.service -f    # what the auto-deploy is doing
./deploy.sh                              # force a deploy now
systemctl list-timers 'vksiv-*'          # when things next run
```

## Backups

`backup.sh` runs nightly at 03:30 via `vksiv-backup.timer`, writing gzipped
`pg_dump` output to `infra/pi/backups/` and keeping 7 days.

Restore:

```bash
gunzip -c backups/appdb-20260101-033000.sql.gz \
  | docker compose exec -T db psql -U app -d appdb
```

These backups live only on the Pi. If the QA data matters to you, copy them off
periodically - QA data is normally reproducible, so this is deliberately simple.
