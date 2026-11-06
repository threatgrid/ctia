# Implementation Overview: XDR-66110
> Generated 2026-11-06 | Spec: specs/XDR-66110-spec.md

## What Changed (TL;DR)
The deployed ctia image is now built on `hardened_alpine:3.24` instead of `3.22`. The inline Dockerfile also upgrades all base-image packages and requires `brotli-libs>=1.2.1-r0`, which fixes CVE-2026-111497. If the fixed brotli isn't available, the image build fails and nothing is pushed, so an unfixed image can't ship.

## Mental Model: Before and After
**Before:** The image used Alpine 3.22 and only ran `apk add`, so base-image packages, including a vulnerable brotli, shipped at whatever version the base had. | **After:** The image uses Alpine 3.24, runs `apk upgrade` before `apk add`, and enforces a minimum `brotli-libs` version that fails the build if not met.

<details>
<summary>Full before/after details</summary>

- **Before**: `.github/workflows/pr.yml` set the workflow-level env `ALPINE_VERSION: "3.22"`. The deploy job's `./build/deploy-actions.sh build` step (`pr.yml:450`) inherited it. `build-and-push-docker-image` (`deploy-actions.sh:49`) wrote a Dockerfile from an unquoted heredoc (`<<EOF`, `:64`) with `FROM …/hardened_alpine:${ALPINE_VERSION}`. Its single RUN step ran `apk update && apk add --no-cache openjdk21-jre libc6-compat tini && …`. Packages already in the base image were never upgraded, and Alpine 3.22 has no fixed brotli anyway. As a result the int, test, and prod images were flagged by Wiz (XDR-65082, -65465, -65515, -65575, -65806).
- **After**: `ALPINE_VERSION` is `"3.24"`. The RUN step is now `apk update && apk upgrade --no-cache && apk add --no-cache openjdk21-jre libc6-compat tini 'brotli-libs>=1.2.1-r0' && ln -s … && mkdir /ctia`. Both ways brotli can reach the image are covered:
  1. A copy that comes with the hardened base image is replaced by `apk upgrade`.
  2. A copy pulled in by the `openjdk21-jre` dependency chain is resolved fresh from the 3.24 index.

  In both cases the explicit version constraint is a hard check. If apk can't satisfy it, `docker build` exits non-zero, `set -Eeuxo pipefail` (`:16`) aborts the script, and `docker push` (`:85`) never runs.
- Everything else is unchanged: the build version tag, the ECR/Artifactory logins, the `push-prod` re-tag path (`pr.yml:474`, which re-tags rather than rebuilds), and the rest of the Dockerfile (`WORKDIR`, `ADD`, `chmod`, `USER nobody`, `ENTRYPOINT`).
</details>

## Architecture & Design
- There are no architectural changes. The edits are one config value and one shell heredoc.
- The deploy image's base OS moves up two Alpine minor versions (3.22 → 3.24), which brings a newer package set, including the `openjdk21-jre` build.
- The image build now fails closed when the `brotli-libs` floor isn't met. Before, it shipped whatever apk resolved.

<details>
<summary>Details</summary>

- **Structural changes**: none. The repo has one consumer of `ALPINE_VERSION` (`deploy-actions.sh:65`), confirmed by a repo-wide grep. The inline Dockerfile has one builder, `build-and-push-docker-image`, which is called only from `build-and-publish-package` for the `int` (master) and `rel` (release) builds.
- **API surface / config keys**: no new keys. Only the value of `env.ALPINE_VERSION` in `pr.yml` changed.
- **Heredoc mechanics**: the heredoc is unquoted, so bash removes each `\`+newline and the RUN block reaches Docker as one logical line. The single quotes around `'brotli-libs>=1.2.1-r0'` pass through to the Dockerfile literally. They matter because without them, `/bin/sh` inside RUN would read `>=1.2.1-r0` as an output redirect. apk would then install plain `brotli-libs` with no version check and leave a stray file behind.
</details>

## Key Decisions
<details>
<summary>4 decisions</summary>

| Decision | Alternatives Considered | Rationale |
|----------|------------------------|----------|
| Target Alpine 3.24 | 3.23 (also has `brotli 1.2.1-r0`) | The ticket edit of 2026-11-06, approved by cduane, set the target to 3.24. |
| Add `apk upgrade --no-cache` | Bump the version only | If brotli comes from the hardened base image, a bump alone keeps the base's copy. Upgrading covers that case no matter which route the package takes. |
| Pin `'brotli-libs>=1.2.1-r0'` in `apk add` | Leave out the pin (the ticket marks it optional) | Without the pin, a stale mirror or index would only be caught by the next Wiz scan, after the image has shipped. With it, the build fails before `docker push`. |
| Change only `pr.yml` and `deploy-actions.sh` | Also update the repo-root `Dockerfile` and the `containers/` images | Matches the precedent of the previous bump, e679329a (#1499), which touched only `pr.yml`. No workflow builds the other images (see Non-Goals). |
</details>

## File-by-File Changes
<details>
<summary>3 files changed (2 source, 1 spec)</summary>

| File | Change Type | Summary |
|------|------------|--------|
| `.github/workflows/pr.yml` | Modified (1 line) | Line 43: `ALPINE_VERSION: "3.22"` → `"3.24"`. |
| `build/deploy-actions.sh` | Modified (+2 / −1) | Inline Dockerfile RUN step: added `apk upgrade --no-cache && \` after `apk update`, and appended `'brotli-libs>=1.2.1-r0'` to the `apk add` line. The resulting lines 67-71 match AC-2 exactly. |
| `specs/XDR-66110-spec.md` | Added | The spec for this ticket (from commit f92169ec). |
</details>

## Dependencies & Configuration
The base image moves to `hardened_alpine:3.24` and `brotli-libs>=1.2.1-r0` becomes required. No application or Clojure dependencies change.

<details>
<summary>Details</summary>

- **Packages**: no new packages. `brotli-libs` was already installed (transitively or from the base). The change only sets a minimum version for it. Because `apk upgrade` now runs, every base package moves to the newest 3.24 version on each build, so builds are less reproducible than before.
- **Config/env**: workflow-level `ALPINE_VERSION` is now `"3.24"`.
- **Deployment steps (manual, outside this PR)**: after merge, someone must cherry-pick to the release branch, re-tag and push to ECR (`push-prod`), and move the ctia/private-ctia deployments in int-use1, test-use1, and prod-use1/euw1/apn1 to the new digests. The deployment manifests live outside this repo. After that, a Wiz rescan should resolve the five linked tickets.
</details>

## Testing
No test files were added or changed. AC-1 to AC-3 are checked with shell commands, AC-4 runs in PR CI, and AC-5 to AC-7 need a real docker build after merge.

<details>
<summary>Details</summary>

- **AC-1 to AC-3 (checked locally)**:
  - `grep ALPINE_VERSION` finds only `pr.yml:43` and its single consumer at `deploy-actions.sh:65`.
  - `sed -n '67,71p'` prints the AC-2 RUN block, single quotes included.
  - `git diff master --stat -- . ':!specs'` lists only the two expected files.
- **AC-4 (shellcheck)**: runs in the setup job's "Run shellcheck" step (`pr.yml:144-145`, `./scripts/shellcheck-build.sh`). It wasn't run locally because shellcheck isn't installed in the sandbox. The change is inside heredoc content, which shellcheck doesn't lint, so a new warning is unlikely.
- **AC-5 to AC-7 (not checked)**: these need docker and Artifactory credentials. Whether the `hardened_alpine:3.24` tag exists, whether apk resolves the fixed brotli, and whether the build fails closed on 3.22 have not been confirmed. The first evidence will be the deploy job on master.
- **Why no automated test**: there's no harness for the inline Dockerfile. `scripts-test/` covers only the bb scripts under `scripts/actions`. A test that pinned the Alpine version would break on every future bump.
- **Note for reviewers**: the requested `git diff feature/XDR-66110...HEAD` is empty because HEAD *is* that branch. This overview was written from `git diff master...HEAD`.
</details>

## Risks & Review Flags
<details>
<summary>6 flags</summary>

- **`hardened_alpine:3.24` must exist in Artifactory.** If the security team hasn't published that tag, `docker build` fails on the `FROM` pull and master deploys stop until it does. Check the `sto-ccc-docker` registry before merging.
- **Two-minor-version jump for the JRE and libc.** 3.24 ships a different `openjdk21-jre` patch level and newer musl and other libraries. `libc6-compat` behavior or JVM patch changes could affect runtime. Nothing in CI checks the image at runtime, so smoke-test int after deploy.
- **`apk upgrade` makes builds less reproducible.** Every package in the image now moves to the newest 3.24 version available at build time, so two builds of the same commit can differ. This is intended for CVE hygiene, but it can make "it worked yesterday" debugging harder.
- **Fail-closed can block deploys.** If a mirror is stale or a future 3.24 index somehow drops `1.2.1-r0` or later, the master and release builds fail outright. This is intended, but the pin should be relaxed or removed once brotli is no longer a concern, or it becomes a leftover constraint.
- **The quoting is fragile.** If someone later quotes the heredoc (`<<'EOF'`), `${ALPINE_VERSION}` stops expanding. If someone drops the single quotes around the constraint, the version check silently disappears because the shell treats it as a redirect. Both would pass shellcheck.
- **Out-of-scope images stay vulnerable.** The repo-root `Dockerfile` (`clojure:alpine`) and the `containers/` dev and demo images aren't changed. No workflow builds them, but local or dev builds of those images may still carry the old brotli.
</details>
