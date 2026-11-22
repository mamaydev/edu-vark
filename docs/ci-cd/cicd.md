# CI/CD pipeline

Two workflows live in `.github/workflows/`:

| Workflow | Trigger | Runner | Purpose |
| --- | --- | --- | --- |
| `ci.yml` | every push, every pull request | GitHub-hosted | build, test, lint, image build |
| `deploy-homelab.yml` | `workflow_dispatch` only | self-hosted `eduvark-deploy` | guarded release to LXC `10100` |

There is intentionally **no `on: push` deployment**. Merging to `main` never deploys by
itself. That was an explicit requirement: automatic deployment stays off until it is
explicitly requested and reviewed.

## Continuous integration (`ci.yml`)

Three independent jobs, all with `permissions: contents: read`:

1. **Android build & unit tests** (`ubuntu-latest`, 45 min timeout)
   - JDK 17 via `actions/setup-java`, Gradle cache via `gradle/actions/setup-gradle`
   - installs `platform-tools`, `platforms;android-37.0`, `build-tools;37.0.0`
   - runs `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug`
   - uploads the debug APK as an artifact (7 days)

   The Android SDK is *not* installed with `android-actions/setup-android`: that action runs
   `sdkmanager tools`, a package Google removed from the repository, and the job failed at
   that step even though the project itself was fine. The runner image already ships
   `cmdline-tools`, so the workflow uses it directly. This was the cause of the failing
   first CI run and is fixed here.

2. **Backend typecheck, lint, test, build** (`ubuntu-latest`, 20 min)
   - `postgres:17` service container with health check
   - `npm ci` → `typecheck` → `lint` → `db:migrate` (real migration against a real
     PostgreSQL) → `test` (vitest) → `build`

3. **Backend image build** (`ubuntu-latest`, 20 min)
   - `docker build -t eduvark-backend:<sha12> ./backend`
   - inspects the image to confirm the runtime user is the non-root `node` user

CI does not push images to a registry. The release artifact is built on the deployment host
from the exact commit, which keeps registry credentials out of the picture; the Dockerfile
build is still validated in CI so a broken image fails before a release is attempted.

## Continuous delivery (`deploy-homelab.yml`)

### Gates

1. **Manual dispatch** with inputs `sha` and `mode` (`verify` / `dry-run` / `deploy`).
2. **`validate` job** (GitHub-hosted, `contents: read`, `actions: read`):
   - input must match `^[0-9a-f]{7,40}$` (short SHAs are resolved against the API);
   - the commit must be an ancestor of `main` (compare API status `identical` or `behind`);
   - there must be a **successful `CI` run on `main`** for that SHA.
3. **Protected environment `homelab-testing`**:
   - required reviewer: `@mamaydev`;
   - deployment branch policy: only `main`;
   - the `deploy` job pauses until the review is approved.
4. **Runner-side guards** in the job itself:
   - `GITHUB_REPOSITORY` must be `mamaydev/edu-vark`;
   - `GITHUB_REF` must be `refs/heads/main`;
   - `/usr/local/sbin/eduvark-deploy` must exist and be executable;
   - no `actions/checkout`, so no repository code runs on the runner.
5. **Deploy engine re-validation** (root): SHA format, `origin/main` ancestry, clean
   checkout, env file permissions, Docker/compose availability, PostgreSQL health, free
   disk ≥3 GB, available memory.

### Concurrency and permissions

```yaml
concurrency:
  group: deploy-homelab
  cancel-in-progress: false
permissions:
  contents: read
  actions: read
```

Serialised releases; a second dispatch waits instead of racing. `GITHUB_TOKEN` is
read-only and is never exposed to the deployment steps as a secret with write scope.

### Modes

| Mode | What it does | Changes production? |
| --- | --- | --- |
| `verify` | ref/ancestry checks, env/compose/disk/memory checks | no |
| `dry-run` | verify + fresh database backup + `docker compose build` | no (backup file only) |
| `deploy` | backup → build → migrate → switch → health → smoke → record | yes |

### Release artefact

The image is tagged `eduvark-backend:<sha12>` and built from the checked-out commit on the
host. `BACKEND_TAG` in `/opt/eduvark/try-pam/.env` is updated after a successful release so
a manual `docker compose up -d` reproduces the same image.

## Repository settings that support the pipeline

- Default `GITHUB_TOKEN` permissions: **read**.
- Actions enabled, allowed actions: all (hosted actions are used by CI).
- Runner: repo-scoped, label `eduvark-deploy`, never used by CI jobs (CI uses
  `ubuntu-latest`), so pull-request code never touches the homelab.
- Environment `homelab-testing`: required reviewer + branch policy `main`.
- Ruleset `main-protection` on `refs/heads/main`:
  - block branch deletion;
  - block force pushes;
  - require the three CI checks (`Android build & unit tests`,
    `Backend typecheck, lint, test, build`, `Backend image build`) before merging;
  - repository admins may bypass (needed for the single-maintainer workflow), documented in
    [`security.md`](security.md).
- `.github/CODEOWNERS` flags `.github/`, `scripts/`, the homelab compose file and the
  Dockerfile for owner review.

## How to release

1. Merge the change to `main` and wait for `CI` to be green.
2. Actions → **Deploy homelab** → *Run workflow*.
3. Enter the full 40-character commit SHA (or a unique short SHA), pick `mode`.
4. Start with `verify`, then `dry-run`, then `deploy` (see
   [`deployment.md`](deployment.md)).
5. Approve the `homelab-testing` environment when prompted.
6. Watch the job; the deploy engine prints the backup path, migration delta, health checks
   and final state. On failure it rolls the backend image back automatically.

## Failure behaviour

| Stage | Behaviour |
| --- | --- |
| validate | job fails, nothing runs on the runner |
| environment review | job waits; no timeout by default, cancel if stale |
| engine: verify | exits before touching anything |
| engine: build | running service untouched, state recorded as `failed_build` |
| engine: migrate | running service untouched, backup path recorded (`failed_migration`) |
| engine: switch/health/smoke | automatic image rollback to the previous tag, state recorded |
| public health check | logged as warning only (ingress/tunnel is out of this repo's control) |

Detailed procedures: [`deployment.md`](deployment.md), [`rollback.md`](rollback.md).