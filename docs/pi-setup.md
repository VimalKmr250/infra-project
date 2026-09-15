# QA environment - Raspberry Pi 5

The Pi runs the QA environment. It never accepts an inbound connection: it pulls
its image from GHCR and reaches the internet outbound through a Cloudflare
tunnel. No router port is forwarded, and no firewall rule is needed.

## Hardware

| Part | Choice | Why |
|---|---|---|
| Board | **Raspberry Pi 5, 8GB** | 4GB runs this stack but leaves no room; 16GB buys nothing here. The capped containers use ~2.1GB, the rest becomes page cache Postgres actually benefits from. |
| Storage | **NVMe SSD, 256GB**, via the official M.2 HAT+ or an Argon NEO 5 M.2 case | Do not run Postgres on a microSD. Sustained small writes wear it out and it is roughly an order of magnitude slower. |
| Cooling | **Official Active Cooler** (or the case's built-in fan) | A JVM under sustained load will thermal-throttle a passively cooled Pi 5. This is a 24/7 machine. |
| Power | **Official 27W USB-C PD supply (5.1V/5A)** | Anything less and the Pi limits peripheral power, which an NVMe drive will notice. Third-party 15W supplies cause boot loops under load. |
| Network | **Wired Ethernet** | Not strictly required - cloudflared only makes outbound connections - but WiFi dropouts are the most common cause of a QA box going quiet. |

Budget roughly $130-170 all in. If you want one box rather than parts, an
Argon NEO 5 M.2 NVMe case bundles the NVMe adapter and cooling.

## Operating system

**Raspberry Pi OS Lite (64-bit)** - Debian Trixie. Lite because there is no
reason to run a desktop, and **64-bit is mandatory**: the image is arm64 only.

Flash with Raspberry Pi Imager and use its settings dialog (the gear icon)
before writing - it saves a round of manual configuration:

- hostname: `vksiv-qa`
- enable SSH, **public-key only** - paste your key rather than setting a password
- username: your own, not `pi`
- locale and WiFi only if you are not using Ethernet

To boot from NVMe, write the image to the SSD directly over a USB-NVMe
enclosure, then tell the bootloader to prefer it:

```bash
sudo raspi-config    # Advanced Options -> Boot Order -> NVMe/USB Boot
```

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

## Sizing

The compose file caps every container, and those limits are load-bearing.
Without a container memory limit the JVM reads the *host's* total RAM and
sizes its heap from that - on an 8GB Pi it would reserve around 6GB and
starve Postgres.

| Container | Limit | Heap |
|---|---|---|
| `api` | 1g | ~614MB (60% of the limit) |
| `db` | 1g | `shared_buffers=256MB` |
| `cloudflared` | 128m | - |

On a 4GB Pi, drop to api `768m` with `MaxRAMPercentage=55`, and db `640m`
with `shared_buffers=192MB`.

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
