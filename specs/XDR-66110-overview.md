# Implementation Overview: XDR-66110
> Generated 2026-11-06 | Spec: specs/XDR-66110-spec.md (removed from the branch in 7cf01413; original in commit f92169ec)

## What Changed (TL;DR)
The deployed ctia image is now built on `hardened_alpine:3.24` instead of `3.22`. The inline Dockerfile in `build/deploy-actions.sh` now runs `apk upgrade` and requires `brotli-libs>=1.2.1-r0`, which fixes CVE-2026-111497. If apk can't supply the fixed brotli, the image build fails before anything is pushed.

## Mental Model: Before and After
**Before:** The image used Alpine 3.22 with only `apk add`, so the base image's vulnerable brotli shipped unchanged. | **After:** The image uses Alpine 3.24, upgrades base packages, and fails the build unless `brotli-libs` is at least 1.2.1-r0.

<details>
<summary>Full before/after details</summary>

- **Before**: `.github/workflows/pr.yml:43` set the workflow-level env `ALPINE_VERSION: "3.22"`. The deploy job's `./build/deploy-actions.sh build` step inherited it. `build-and-push-docker-image` (`deploy-actions.sh:49`) wrote a Dockerfile from an unquoted heredoc (`<<EOF`, `:64`) starting with `FROM …/hardened_alpine:${ALPINE_VERSION}`. Its RUN step was `apk update && apk add --no-cache openjdk21-jre libc6-compat tini && …`. Base-image packages were never upgraded, and Alpine 3.22 has no fixed brotli. As a result, the int, test, and prod images (digests c12f9e94, a4071549) were flagged in XDR-65082, XDR-65465, XDR-65515, XDR-65575, and XDR-65806.
- **After**: `ALPINE_VERSION` is `"3.24"`, and the RUN step is:
  ```
  RUN apk update && \
      apk upgrade --no-cache && \
      apk add --no-cache openjdk21-jre libc6-compat tini 'brotli-libs>=1.2.1-r0' && \
      ln -s /usr/bin/java /bin/java && \
      mkdir /ctia
  ```
  brotli can reach the image in two ways, and both are now covered. If it comes from the hardened base, `apk upgrade` replaces it. If it comes through the `openjdk21-jre` dependency chain, `apk add` resolves it from the 3.24 index. The version constraint is a hard floor on top of both. If it isn't met, `docker build` (`:84`) fails, `set -Eeuxo pipefail` (`:16`) aborts the script, and `docker push` (`:85`) never runs.
- **Unchanged**: tagging, registry logins, `int`/`test` repo selection, and `push-prod-images`. Prod images are re-tags of the release image, not rebuilds, so prod gets the fix through the release build. The rest of the Dockerfile (`WORKDIR`, `ADD`, `chmod`, `USER nobody`, `ENTRYPOINT`) is byte-for-byte the same.

</details>

## Architecture & Design
- No structural changes: one config value in `pr.yml` and one RUN step in a shell heredoc.
- The deploy image's base OS moves up two Alpine minor versions (3.22 → 3.24), which pulls in newer musl and `openjdk21-jre` builds. The JDK major stays 21.
- The image build now fails closed when the `brotli-libs` floor isn't met.

<details>
<summary>Config value bump + one RUN step rewrite; no interface changes</summary>

- **Structural changes**: none. `ALPINE_VERSION` has exactly one consumer (`deploy-actions.sh:65`). The inline Dockerfile has one builder, `build-and-push-docker-image`, called only from `build-and-publish-package` for master (`int`) and release (`rel`) builds.
- **API / config surface**: no new keys, secrets, or env vars. Only the value of `env.ALPINE_VERSION` changed.
- **Not touched**: the repo-root `Dockerfile` (`FROM clojure:alpine`, which no workflow builds) and the `containers/` dev and demo images.

</details>

## Key Decisions
<details>
<summary>4 decisions</summary>

| Decision | Alternatives Considered | Rationale |
|----------|------------------------|----------|
| Bump to Alpine 3.24 | Stay on 3.22 with `apk upgrade`; target 3.23 | 3.22 has no fixed brotli. The ticket was edited on 2026-11-06 (approved by cduane) to target 3.24. |
| Add `apk upgrade --no-cache` before `apk add` | Version bump only | If brotli comes from the hardened base layer, a bump alone keeps the base's version. The upgrade covers that case. |
| Pin `'brotli-libs>=1.2.1-r0'` | Omit the pin (the ticket marks it optional) | Without the pin, a stale mirror or index would only show up at the next Wiz scan, after the image ships. With it, the build fails at `docker build`. |
| Single-quote the constraint | Unquoted | The heredoc is unquoted, so the quotes pass through to the Dockerfile. Without them, `/bin/sh` in RUN treats `>=1.2.1-r0` as a redirect, and `apk add brotli-libs` succeeds with no version check. |

</details>

## File-by-File Changes
<details>
<summary>2 files changed (+3 / −2)</summary>

| File | Change Type | Summary |
|------|------------|--------|
| `.github/workflows/pr.yml` | Modified (1 line) | `ALPINE_VERSION: "3.22"` → `"3.24"` (line 43). |
| `build/deploy-actions.sh` | Modified (1 line changed, 1 inserted) | Inline Dockerfile RUN step: inserts `apk upgrade --no-cache && \` and adds `'brotli-libs>=1.2.1-r0'` to the `apk add` list. |

</details>

## Dependencies & Configuration
No new dependencies; the only config change is the `ALPINE_VERSION` value, 3.22 → 3.24.

<details>
<summary>2 external prerequisites, manual rollout after merge</summary>

- **Prerequisites (not verified in-session)**:
  - `artifactory.devhub-cloud.cisco.com/sto-ccc-docker/hardened_alpine:3.24` exists and can be pulled with the existing JFrog credentials.
  - `openjdk21-jre`, `libc6-compat`, `tini`, and `brotli-libs>=1.2.1-r0` are available in the Alpine 3.24 repos that the hardened image uses.
- **Migrations / secrets**: none.
- **Rollout (manual, outside this repo)**:
  - Cherry-pick to the release branch.
  - Re-tag the ECR images.
  - Move the ctia/private-ctia deployments in int-use1, test-use1, prod-use1, prod-euw1, and prod-apn1.
  - Confirm that the Wiz rescan resolves the five tickets.

</details>

## Testing
No test files were added; verification is by shell checks plus post-merge CI, and the docker-dependent criteria (AC-5 to AC-7) are not yet verified.

<details>
<summary>AC-1–AC-4 verified locally; AC-5–AC-7 pending (need docker/Artifactory)</summary>

- **Verified** (per commit 49272d59):
  - AC-1 and AC-2 checked with grep and sed against the exact expected lines.
  - AC-3 checked by diffing against `019e0c93`, the merge-base on master.
  - AC-4: `scripts/shellcheck-build.sh` exits 0 with shellcheck 0.11.0.
  - The rendered heredoc keeps the single quotes, and the RUN step comes out as one line.
  - `bash -n` and a YAML parse both pass.
- **Not verified**:
  - AC-5, deploy log: pulls 3.24 and resolves `brotli-libs>=1.2.1-r0`.
  - AC-6: `apk info -v brotli-libs` inside the built image.
  - AC-7, fail-closed on 3.22: needs docker and Artifactory credentials.
  - The Clojure and bb suites were not run. They don't cover these files.
- **Coverage gap**: the deploy job runs only on push to master or `vX.Y[.Z]` (`pr.yml:392`). **PR CI never builds this image**, so the first real build happens after merge.

</details>

## Risks & Review Flags
<details>
<summary>5 flags</summary>

- **First real build is post-merge.** If the 3.24 tag or a package is missing, the master deploy fails. Only int is affected until the release cherry-pick. If someone has Artifactory access, they should run a local `docker build` of the rendered Dockerfile before merge.
- **`apk upgrade` can override hardened-image packages.** If the hardened base ships custom or internal-repo packages, upstream versions may replace them. Check the upgrade list in the first deploy log. If hardening packages are affected, narrow it to `apk upgrade --no-cache brotli-libs`.
- **Two-minor Alpine jump.** musl and the openjdk21 patch level change. Soak in int-use1 before cherry-picking to release.
- **Less reproducible images.** Image contents now track the repo state at build time. This is intentional, since it's how fixes land, but successive builds of the same commit can differ.
- **Quoting is load-bearing.** Any future edit that drops the single quotes around the constraint silently disables the version check without breaking the build.

</details>
