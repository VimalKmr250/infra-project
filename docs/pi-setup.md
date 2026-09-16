# QA environment - Raspberry Pi 4 Model B (4GB)

Every merge to `main` lands here within a few minutes. The Pi never accepts an
inbound connection: it pulls its image from GHCR and reaches the outside world
through an outbound Cloudflare tunnel, so no router port is forwarded and no
firewall rule is needed.

---

# Initial setup

Work through these in order. Budget about an hour, most of it spent waiting on
downloads.

## What you need

| | |
|---|---|
| Raspberry Pi 4 Model B, 4GB | The container limits are sized for it |
| microSD card, 32GB+ | Buy **high-endurance** (SanDisk Max Endurance, Samsung PRO Endurance) - a database writes constantly |
| Official 15W USB-C supply (5.1V/3A) | Under-powering a Pi 4 causes silent throttling and filesystem corruption |
| Heatsink and fan | Argon ONE or a Flirc case. This box runs a JVM continuously |
| Ethernet cable | WiFi works, but dropouts are the usual reason a QA box goes quiet |

You also need a **GitHub personal access token** with the `read:packages` scope.
GHCR packages are private by default, and the Pi needs it to pull the image.
Create one at GitHub → Settings → Developer settings → Personal access tokens.

## 1. Flash the operating system

Use **Raspberry Pi OS Lite (64-bit)**. Lite because there is no reason to run a
desktop on a headless server, and 64-bit because this project publishes an
**arm64 image only** - a 32-bit install cannot run it.

In Raspberry Pi Imager, open the settings dialog (the gear icon) *before*
writing. It saves a round of manual configuration:

- hostname: `vksiv-qa`
- enable SSH, **public-key only** - paste your key rather than setting a password
- username: your own, not `pi`
- locale, and WiFi only if you are not using Ethernet

## 2. First boot, and confirm the architecture

Insert the card, connect Ethernet, and power on. Then SSH in:

```bash
ssh <your-user>@vksiv-qa.local
```

Confirm the OS is 64-bit. This must print `aarch64`:

```bash
uname -m
```

If it prints `armv7l`, you have the 32-bit OS. Stop here and reflash with the
64-bit image - nothing later in this guide will work otherwise.

Confirm power and cooling are adequate. This must print `throttled=0x0`:

```bash
vcgencmd get_throttled
```

Anything else means the Pi is being throttled by heat or an inadequate power
supply. Fix that before continuing; on a Pi 4 it is the most common cause of
unexplained slowness and card corruption.

## 3. Update and prepare the system

```bash
sudo apt update && sudo apt full-upgrade -y
```

Raspberry Pi OS Lite ships without git, so install it now - step 4 needs it:

```bash
sudo apt install -y git
```

Check what swap exists. Current Pi OS Lite images often have none at all, and
`dphys-swapfile` is no longer installed by default - if you get
`command not found`, that is why.

```bash
swapon --show
```

Act on what it prints:

- **Nothing at all** - there is no swap. Nothing to do; move on.
- **`/dev/zram0`** - swap lives in compressed RAM, not on the card. **Leave it
  alone.** It costs the card no writes at all and is genuinely useful on a 4GB
  board.
- **A file path such as `/var/swap`** - that is a real file on the SD card, and
  it is both slow and destructive. Turn it off:

```bash
sudo swapoff -a && sudo systemctl disable --now dphys-swapfile
```

Confirm the kernel's memory cgroup is enabled. This matters more than it looks:
the container memory limits this environment depends on are **silently ignored**
without it, and the JVM goes back to sizing its heap against the host's full
4GB. This should print `OK`:

```bash
grep -qw memory /sys/fs/cgroup/cgroup.controllers && echo OK || echo MISSING
```

If it prints `MISSING`, enable it and reboot. `cmdline.txt` must stay a single
line, which is why this appends rather than adding one:

```bash
sudo sed -i '1 s/$/ cgroup_memory=1 cgroup_enable=memory/' /boot/firmware/cmdline.txt && sudo reboot
```

Cap the systemd journal so it cannot grow without bound:

```bash
sudo mkdir -p /etc/systemd/journald.conf.d
```

```bash
printf '[Journal]\nSystemMaxUse=100M\n' | sudo tee /etc/systemd/journald.conf.d/size.conf && sudo systemctl restart systemd-journald
```

## 4. Clone the repository

```bash
git clone https://github.com/OWNER/REPO.git ~/vksiv-apps
```

```bash
cd ~/vksiv-apps/infra/pi && cp .env.example .env
```

## 5. Fill in the secrets

Generate the two secrets and copy the output:

```bash
echo "POSTGRES_PASSWORD=$(openssl rand -base64 24)"; echo "APP_JWT_SECRET=$(openssl rand -base64 48)"
```

Open `.env` and set:

| Variable | Value |
|---|---|
| `IMAGE` | `ghcr.io/OWNER/REPO:qa` - all lowercase |
| `POSTGRES_PASSWORD` | from the command above |
| `APP_JWT_SECRET` | from the command above |
| `GHCR_USER` | your GitHub username |
| `GHCR_TOKEN` | the `read:packages` token |
| `CLOUDFLARE_TUNNEL_TOKEN` | leave **blank** for now |

```bash
nano .env && chmod 600 .env
```

## 6. Run the bootstrap

```bash
./bootstrap.sh
```

It installs Docker if missing, logs in to GHCR, installs and enables both
systemd timers, and runs the first deploy. It is safe to re-run at any time.

**If it just installed Docker, log out and back in, then run it again** - your
user needs to pick up the `docker` group first.

The first deploy pulls roughly 180MB and then starts a JVM from an SD card. Give
it a few minutes before assuming something has gone wrong. Watch it with:

```bash
docker compose logs -f api
```

## 7. Find the QA URL

With `CLOUDFLARE_TUNNEL_TOKEN` blank you get a free *quick tunnel*.
`deploy.sh` writes the current address to a file:

```bash
cat ~/vksiv-apps/infra/pi/tunnel-url.txt
```

Open it. You should get the Angular UI, and be able to register an account and
create a note.

This URL **changes every time the tunnel container restarts**, including on
every deploy. That is the free tier's limitation, not a misconfiguration - see
[Reaching it from outside](#reaching-it-from-outside) for the fix.

## 8. Confirm it survives a reboot

The point of this box is that you never touch it again, so prove that now:

```bash
sudo reboot
```

Wait a minute, SSH back in, and check everything came back on its own:

```bash
cd ~/vksiv-apps/infra/pi && docker compose ps
```

```bash
systemctl list-timers 'vksiv-*'
```

Both timers should be listed and all containers up. Setup is done - from here,
merging to `main` is all it takes to deploy.

---

# Reference

## What runs on the Pi

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
is live within a few minutes with no push access to the Pi at all.

## Reaching it from outside

**Quick tunnel (default, no domain).** A free `*.trycloudflare.com` address that
changes whenever the tunnel restarts. Fine for ad-hoc testing, poor as a stable
QA address.

**Named tunnel (recommended).** Needs a domain on Cloudflare. Create a tunnel in
Zero Trust → Networks → Tunnels, point a hostname such as `qa.example.com` at
`http://api:8080`, and put the token in `.env` as `CLOUDFLARE_TUNNEL_TOKEN`.
`deploy.sh` detects it and switches profiles automatically - the URL then never
changes. A domain costs roughly $10/year and is the single best improvement you
can make to this environment. You can also put Cloudflare Access in front of the
hostname so only your own account can load QA.

**Private only.** Install Tailscale and skip cloudflared entirely; QA is then
reachable only from your own devices.

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
./deploy.sh
```

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

## Why the microSD needs care

A database writes constantly and SD cards wear out by being written to. Expect a
consumer card running Postgres to degrade over months rather than years.

Two mitigations are already configured for you:

- **Postgres is tuned to write less.** `infra/pi/docker-compose.yml` sets
  `synchronous_commit=off`, `wal_compression=on` and a 15-minute checkpoint
  interval. The first is the significant one - commits stop waiting on fsync.
  A crash can lose the last fraction of a second of transactions, which is the
  right trade for QA and would be the wrong one for production.
- **Docker log rotation is capped** at 10MB x 3 per container, so logs cannot
  quietly grind the card down.

Step 3 covers the rest: no disk-backed swap, and a capped journal. Note that
zram swap is fine to keep - it is compressed RAM and never touches the card.

If this becomes a nuisance, the real fix is a SATA SSD in a USB 3.0 enclosure on
one of the blue ports. A Pi 4 can boot from it directly with current firmware,
and it is a large improvement over any card.
