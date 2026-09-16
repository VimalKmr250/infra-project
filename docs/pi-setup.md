# QA environment - Raspberry Pi 4 Model B (4GB)

The Pi runs the QA environment. It never accepts an inbound connection: it pulls
its image from GHCR and reaches the internet outbound through a Cloudflare
tunnel. No router port is forwarded, and no firewall rule is needed.

A Pi 4 runs this stack fine. It is slower than a Pi 5 - a Cortex-A72 at 1.5GHz
against an A76 at 2.4GHz - so expect the application to take a couple of minutes
to become healthy after a deploy rather than under a minute. Nothing else about
the pipeline changes: the Pi 4 is arm64, so it runs exactly the same image.

## Hardware

| Part | Choice | Note |
|---|---|---|
| Board | Pi 4 Model B, 4GB | The container limits below are sized for it. |
| Power | **Official 15W USB-C supply (5.1V/3A)** | Under-powering a Pi 4 causes silent throttling and filesystem corruption. If `vcgencmd get_throttled` returns anything but `0x0`, suspect the supply before anything else. |
| Cooling | **Heatsink and fan**, e.g. Argon ONE or a Flirc case | A bare Pi 4 throttles at 80C and this box runs a JVM continuously. Passive-only is not enough. |
| Storage | microSD - **high-endurance card** | See below. |
| Network | Wired Ethernet | cloudflared is outbound-only so WiFi works, but WiFi dropouts are the usual reason a QA box goes quiet. |

### About the microSD card

This is the weak point of the setup and worth being honest about: a database
writes constantly, and SD cards wear out by being written to. Expect a consumer
card running Postgres to degrade over months rather than years.

Two mitigations are already in place for you:

- **Postgres is tuned to write less.** `infra/pi/docker-compose.yml` sets
  `synchronous_commit=off`, `wal_compression=on` and a 15-minute checkpoint
  interval. The first is the significant one - commits stop waiting on fsync.
  A crash can lose the last fraction of a second of transactions, which is the
  right trade for QA and would be the wrong one for production.
- **Docker log rotation is capped** at 10MB x 3 per container, so logs cannot
  quietly grind the card down.

Worth doing yourself:

- Buy a **high-endurance** card (SanDisk Max Endurance, Samsung PRO Endurance).
  They are built for dashcams writing continuously and cost little more than a
  normal card.
- Turn off swap. With 4GB and capped containers you do not need it, and swap on
  an SD card is both slow and destructive:

```bash
sudo dphys-swapfile swapoff && sudo systemctl disable --now dphys-swapfile
```

- Cap the systemd journal so it cannot grow without bound:

```bash
sudo mkdir -p /etc/systemd/journald.conf.d
```

```bash
printf '[Journal]\nSystemMaxUse=100M\n' | sudo tee /etc/systemd/journald.conf.d/size.conf && sudo systemctl restart systemd-journald
```

- Rely on the nightly `pg_dump` backups below and copy them off the Pi. Assume
  the card will fail eventually; that assumption is what makes it a non-event.

If you later want a real fix rather than a mitigation, a SATA SSD in a USB 3.0
enclosure on one of the blue ports is a large improvement, and a Pi 4 can boot
from it directly with current firmware.

## Operating system

**Raspberry Pi OS Lite (64-bit)** - Debian Trixie. Two things matter:

- **Lite**, because there is no reason to run a desktop on a headless server.
- **64-bit is mandatory.** A Pi 4 will happily run the 32-bit image, and many
  existing Pi 4 installs are 32-bit, but this project publishes an arm64 image
  only - on a 32-bit OS the container will not start. `bootstrap.sh` checks this
  and warns. Confirm with `uname -m`, which must print `aarch64`, not `armv7l`.

Flash with Raspberry Pi Imager and use its settings dialog (the gear icon)
before writing - it saves a round of manual configuration:

- hostname: `vksiv-qa`
- enable SSH, **public-key only** - paste your key rather than setting a password
- username: your own, not `pi`
- locale, and WiFi only if you are not using Ethernet

## What runs there

| Container | Limit | Purpose |
|---|---|---|
| `vksiv-api` | 768m | The application image, `qa` tag, arm64 |
| `vksiv-db` | 640m | PostgreSQL 17, data on the card |
| `vksiv-tunnel` | 128m | `cloudflared`, exposes the API over HTTPS |

Roughly 1.5GB capped, leaving the rest of the 4GB for the OS, Docker and page
cache. The limits are load-bearing: without a container limit the JVM reads the
*host's* total RAM and sizes its heap from that, so it would try to reserve
around 3GB and leave nothing for Postgres.

A systemd timer runs `deploy.sh` every two minutes. It pulls the `qa` tag and
restarts the stack only when the digest actually changed, so a merge to `main`
is live on the Pi within a few minutes, with no push access to the Pi at all.

## One-time setup

```bash
sudo apt update && sudo apt full-upgrade -y
```

```bash
git clone https://github.com/OWNER/REPO.git ~/vksiv-apps
```

```bash
cd ~/vksiv-apps/infra/pi && cp .env.example .env
```

Generate the two secrets, then paste them into `.env`:

```bash
echo "POSTGRES_PASSWORD=$(openssl rand -base64 24)"; echo "APP_JWT_SECRET=$(openssl rand -base64 48)"
```

Also fill in:

- `IMAGE` - `ghcr.io/OWNER/REPO:qa`, all lowercase
- `GHCR_USER` / `GHCR_TOKEN` - a GitHub PAT with `read:packages`, because GHCR
  packages are private by default. Leave both blank only if you deliberately
  made the package public.
- `CLOUDFLARE_TUNNEL_TOKEN` - leave blank for now (see below)

Then:

```bash
./bootstrap.sh
```

It installs Docker if missing, logs in to GHCR, installs and enables the systemd
timers, and runs the first deploy. It is safe to re-run.

If it just installed Docker, log out and back in before re-running, so your user
picks up the `docker` group.

The first deploy pulls roughly 180MB and then starts a JVM from an SD card, so
give it a few minutes before assuming something has gone wrong.

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
cd ~/vksiv-apps/infra/pi && docker compose ps
```

```bash
docker compose logs -f api
```

```bash
journalctl -u vksiv-deploy.service -f
```

```bash
systemctl list-timers 'vksiv-*'
```

`vcgencmd get_throttled` should return `0x0`. Anything else means the Pi has
been throttled by heat or an inadequate power supply, which on a Pi 4 is the
most common cause of unexplained slowness or corruption.

## Backups

`backup.sh` runs nightly at 03:30 via `vksiv-backup.timer`, writing gzipped
`pg_dump` output to `infra/pi/backups/` and keeping 7 days.

Restore:

```bash
gunzip -c backups/appdb-20260101-033000.sql.gz | docker compose exec -T db psql -U app -d appdb
```

These backups live on the same card as the database, which is exactly the thing
expected to fail. Copy them off the Pi periodically - a cron job doing `scp` to
your laptop is enough.
