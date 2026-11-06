# Implementation Overview: XDR-66110
> Generated 2026-11-06 | Spec: specs/XDR-66110-spec.md (removed from the branch in 7cf01413; see commit f92169ec)

## What Changed (TL;DR)
The deployed ctia image is now built on `hardened_alpine:3.24` instead of `3.22`. The inline Dockerfile in `build/deploy-actions.sh` now runs `apk upgrade` on the base image's packages and requires `brotli-libs>=1.2.1-r0`, which fixes CVE-2026-111497. If apk can't supply the fixed brotli, the image build fails and nothing is pushed.

## Mental Model: Before and After
**Before:** The image was built on Alpine 3.22 with only `apk add`, so base-image packages, including a vulnerable brotli, shipped unchanged. | **After:** The image is built on Alpine 3.24, runs `apk upgrade` before `apk add`, and enforces a minimum `brotli-libs` version that fails the build if not met.

<details>
<summary>Full before/after details</summary>

- **Before**: `.github/workflows/pr.yml:43` set the workflow-level env `ALPINE_VERSION: "3.22"`. The deploy job's `./build/deploy-actions.sh build` step inherited it. `build-and-push-docker-image` (`deploy-actions.sh:49`) wrote a Dockerfile from an unquoted heredoc (`<<EOF`, `:64`) with `FROM …/hardened_alpine:${ALPINE_VERSION}`, and its single RUN step was `apk update && apk add --no-cache openjdk21-jre libc6-compat tini && …`. Base-image packages were never upgraded, and Alpine 3.22 has no fixed brotli. Wiz flagged the int, test, and prod images (digests c12f9e94, a4071549) in XDR-65082, XDR-65465, XDR-65515, XDR-65575, and XDR-65806.
- **After**: `ALPINE_VERSION` is `"3.24"`. The RUN step is now:
  ```
  RUN apk update && \
      apk upgrade --no-cache && \
      apk add --no-cache openjdk21-jre libc6-compat tini 'brotli-libs>=1.2.1-r0' && \
      ln -s /usr/bin/java /bin/java && \
      mkdir /ctia
  ```
  It covers both ways brotli can end up in the image:
  1. **From the hardened base image.** `apk upgrade` replaces the base's copy with the 3.24 version.
  2. **Through the `openjdk21-jre` dependency chain** (likely freetype). `apk add` resolves it fresh from the 3.24 index.

  The version constraint is a hard check on top of both. If it can't be satisfied, `docker build` (`:84`) exits non-zero, `set -Eeuxo pipefail` (`:16`) aborts the script, and `docker push` (`:85`) never runs.
- **Unchanged**: `build_version` tagging, the ECR and Artifactory logins, the `int`/`test` repo selection, the `push-prod-images` path (which re-tags the release image and doesn't rebuild it, so prod gets the fix through the release build), and the rest of the Dockerfile (`FROM` template, `WORKDIR`, `ADD`, `chmod`, `USER nobody`, `ENTRYPOINT`).

</details>

## Architecture & Design
- There are no structural changes: one config value changed in `pr.yml` and one RUN step changed in a shell heredoc.
- The deploy image's base OS moves up two Alpine minor versions (3.22 → 3.24), which brings a newer package set, including newer `openjdk21-jre` and musl builds.
- The image build now fails closed when the `brotli-libs` floor isn't met. Before, it shipped whatever versions apk resolved.

<details>
<summary>Config value bump + one RUN step rewrite; no interface changes</summary>

- **Structural changes**: none. `ALPINE_VERSION` has one consumer (`deploy-actions.sh:65`). The inline Dockerfile has one builder, `build-and-push-docker-image`, and its only caller is `build-and-publish-package`, which runs for master (`int`) and release (`rel` → `test-docker-build`) builds.
- **API surface / config keys**: no new keys, secrets, or env vars. Only the value of `env.ALPINE_VERSION` changed.
- **Heredoc mechanics**: the heredoc is unquoted, so bash removes each `\`+newline and the RUN block reaches Docker as one logical line. The single quotes around `'brotli-libs>=1.2.1-r0'` pass through literally into the Dockerfile, and they matter: without them, `/bin/sh` inside RUN would treat `>=1.2.1-r0` as an output redirect. apk would then install plain `brotli-libs` with no version check, and the build would still succeed.
- **When it runs**: the `deploy` job runs only on `push` events to master or release tags. **PR CI never builds this image**, so the first real build of these changes is the first master deploy after merge.
- **Out of scope**: the repo-root `Dockerfile` (`FROM clojure:alpine`) and the `containers/` dev/demo images. Neither is in the deploy path.

</details>

## Key Decisions
<details>
<summary>4 decisions</summary>

| Decision | Alternatives Considered | Rationale |
|----------|------------------------|----------|
| Target Alpine 3.24 | 3.23 (which also ships brotli 1.2.1-r0) | The ticket was edited on 2026-11-06 (approved by cduane) to target 3.24. |
| Add `apk upgrade --no-cache` before `apk add` | Bump the version only | If brotli comes from the hardened base image, a bump alone keeps the base's version. The upgrade fixes it whichever way it arrives. |
| Pin `'brotli-libs>=1.2.1-r0'` in `apk add` | Leave the pin out (the ticket marks it optional) | Without the pin, a stale mirror or index would only show up in the next Wiz scan, after the image has shipped. With it, the build fails before push. |
| Upgrade all packages, not just brotli | `apk upgrade --no-cache brotli-libs` | Upgrading everything also picks up other base-image fixes. The trade-off is that it may touch hardened-image packages (see Risks). The narrower form is the fallback. |

</details>

## File-by-File Changes
<details>
<summary>2 files changed (+3 / -2)</summary>

| File | Change Type | Summary |
|------|------------|--------|
| `.github/workflows/pr.yml` | Modified (1 line) | Line 43: `ALPINE_VERSION: "3.22"` → `"3.24"`. This workflow-level env var is inherited by the deploy step. |
| `build/deploy-actions.sh` | Modified (1 line changed, 1 inserted) | In the inline Dockerfile heredoc in `build-and-push-docker-image`: inserted `apk upgrade --no-cache && \` after `apk update`, and appended `'brotli-libs>=1.2.1-r0'` to the `apk add` package list. |

The branch also added and then removed `specs/XDR-66110-spec.md` and an earlier overview (7cf01413, "remove specs/ per review"). The net diff against master contains only the two files above.

</details>

## Dependencies & Configuration
No new dependencies; the only config change is `ALPINE_VERSION` 3.22 → 3.24, and nothing needs to be migrated.

<details>
<summary>2 external prerequisites, 1 manual rollout</summary>

- **Packages**: no new packages besides the `brotli-libs` version constraint. Package versions in the image will now follow the 3.24 repositories at build time.
- **External prerequisites** (not verified in this session):
  - `artifactory.devhub-cloud.cisco.com/sto-ccc-docker/hardened_alpine:3.24` exists and can be pulled with the CI JFrog credentials.
  - `openjdk21-jre`, `libc6-compat`, `tini`, and `brotli-libs>=1.2.1-r0` are available in the Alpine 3.24 repositories the hardened image uses.
- **Deployment**: someone has to do these by hand after merge. Cherry-pick to the release branch so test/prod get a rebuilt image, then roll the ctia/private-ctia deployments in int-use1, test-use1, and prod-use1/euw1/apn1. The manifests for those live outside this repo. Then confirm the Wiz rescan closes the five tickets.

</details>

## Testing
No test files were added or changed. AC-1 to AC-4 (grep/sed/diff checks plus `scripts/shellcheck-build.sh`) were verified locally; AC-5 to AC-7 need Docker and Artifactory and will only be exercised by the post-merge deploy.

<details>
<summary>4 of 7 ACs verified pre-merge; image-level checks deferred</summary>

- **Verified locally** (per commit 49272d59):
  - AC-1: `ALPINE_VERSION` appears once in `pr.yml`, at line 43, set to "3.24".
  - AC-2: the RUN block matches exactly, with the single quotes kept.
  - AC-3: only the two files changed (diffed against 019e0c93, because no local `main` ref exists).
  - AC-4: `shellcheck -S style build/*.sh` exits 0 with shellcheck 0.11.0.
  - The rendered heredoc was checked: the quotes survive and the RUN block reaches Docker as one line.
  - `bash -n` and the YAML parse are clean.
- **Not verified**:
  - AC-5: the deploy log shows a pull of 3.24 and apk resolves brotli-libs >= 1.2.1-r0.
  - AC-6: `apk info -v brotli-libs` inside the built image.
  - AC-7: the build fails closed when run against 3.22.

  All three need Docker and registry credentials.
- **Why no automated tests**: no harness exists for the inline Dockerfile. `scripts-test/` only covers bb scripts. A test that pinned the Alpine version would break on every future bump. The Clojure and bb suites weren't run (lein/java/bb aren't installed), but they don't cover these files.
- **Gap**: PR CI doesn't build the image, so a missing 3.24 tag or an unresolvable package only shows up when the first master deploy fails.

</details>

## Risks & Review Flags
<details>
<summary>6 flags</summary>

- **First real build happens after merge.** If the `hardened_alpine:3.24` tag is missing or a package such as `libc6-compat` was renamed or changed in 3.24, the master deploy to int fails. Release and prod aren't affected until the cherry-pick. If possible, have someone with Artifactory access run a local `docker build` of the rendered Dockerfile before merge.
- **The quoting is load-bearing.** A future edit that drops the single quotes around `brotli-libs>=1.2.1-r0` would silently turn the version check into a shell redirect, and the build would still succeed. Reviewers should keep this in mind for later edits to this heredoc.
- **`apk upgrade` may override hardened-image packages.** If the hardened base ships customized or internal-repo packages, upstream 3.24 versions could replace them. Check the upgrade list in the first deploy log. If hardening packages are touched, narrow the step to `apk upgrade --no-cache brotli-libs`.
- **Two-minor Alpine jump.** The musl version and the openjdk21 patch level both change, though the JDK major stays at 21. Let it soak in int-use1 before cherry-picking to release.
- **Less reproducible images.** Image contents now depend on the state of the package repositories at build time rather than only on the base tag. This is intended, since it is how fixes land, but two builds of the same commit can differ.
- **Release cherry-pick could conflict** if `pr.yml` or `deploy-actions.sh` differ on the release branch. Whoever does the rollout resolves that.

</details>
