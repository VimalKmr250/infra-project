# Local development

## Prerequisites

| Tool | Version | Note |
|---|---|---|
| JDK | 25 | Or none - Gradle downloads a matching toolchain if `java` is absent, but the wrapper still needs *a* JVM to start. |
| Node | 22.22.3+ or 24.15+ | Only for running `ng serve` directly. The Gradle build ignores your system Node and downloads its own pinned copy. |
| Docker | any recent | For PostgreSQL and for the Testcontainers integration tests. |

Angular 22 rejects odd-numbered Node releases (23.x, 25.x). If `node -v` shows one,
install Node 24 LTS before running `npm start` by hand.

## The fast loop

Three terminals. Postgres in Docker, both applications native, everything hot-reloads.

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

```bash
./gradlew :backend:bootRun -PskipFrontend
```

```bash
cd frontend && npm start
```

Open http://localhost:4200. `ng serve` proxies `/api` to port 8080 (see
`frontend/proxy.conf.json`), so there is no CORS configuration anywhere.

`-PskipFrontend` is what makes this fast: without it, every backend restart also
rebuilds the Angular bundle to pack into the jar, which you do not want while
`ng serve` is already serving the UI.

## Other useful commands

```bash
./gradlew :backend:test -PskipFrontend    # unit + Testcontainers integration tests
```

```bash
cd frontend && npm run test:ci && npm run lint
```

```bash
./gradlew :backend:bootJar && java -jar backend/build/libs/backend-*.jar
```

That last one produces and runs the single artifact that ships: one jar serving
both the API and the Angular bundle on port 8080. Use it to check the
production packaging before pushing.

Add pgAdmin on http://localhost:5050 with:

```bash
docker compose -f infra/local/docker-compose.yml --profile tools up -d
```

Reproduce the Pi's containerised setup locally with:

```bash
docker compose -f infra/local/docker-compose.yml --profile full up -d --build
```

## Configuration

`.env.example` documents every variable. Copy it to `.env` for Docker Compose.
The `local` Spring profile has working defaults for everything, so a fresh clone
runs with no configuration at all.

## Known issue: brackets in the project path

This repository currently lives under a directory containing `[4]`. Square
brackets are glob syntax, and several tools expand them rather than treating them
literally - Vitest and the TypeScript compiler both fail to find spec files,
so `npm run test:ci` reports "No test files found" even though they exist.

The Gradle and Angular builds work regardless. Only the frontend test runner is
affected, and it works correctly in CI, where the path is ordinary.

The fix is to move the repository to a path with no brackets, for example
`E:\AI\infra-project`.

## Known issue: the Gradle daemon dies during `npmInstall`

On this machine the Gradle daemon is killed - silently, with nothing in its log -
immediately after `:frontend:npmInstall` finishes writing `node_modules`. It
happens with and without the daemon, with file watching disabled, and in a
directory with no brackets, so it is environmental rather than a build problem.
The most likely cause is security software reacting to npm creating tens of
thousands of files at once.

Workaround - install dependencies outside Gradle once, then let Gradle skip it:

```bash
cd frontend && npm ci
```

```bash
./gradlew :backend:bootJar -x :frontend:npmInstall
```

Everything else - `buildAngular`, packaging Angular into the jar, `bootJar` -
then runs normally. CI is unaffected.

If you want to find the real cause, check Windows Security -> Protection history
around the time of a failed build, and try excluding the project directory and
your Gradle home from real-time scanning.
