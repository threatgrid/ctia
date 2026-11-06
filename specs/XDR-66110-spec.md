# Spec: XDR-66110
> Status: Draft | Ticket: XDR-66110

## Problem Statement
The deployed ctia image is built by an inline Dockerfile in `build/deploy-actions.sh`, `FROM hardened_alpine:${ALPINE_VERSION}`. `.github/workflows/pr.yml` pins that to `"3.22"`, and the build only runs `apk add`, so base-image packages are never upgraded. OSV has no brotli fix for Alpine v3.22 but lists `brotli 1.2.1-r0` for v3.23/v3.24 (CVE-2026-111497). As a result the int, test, and prod images (digests c12f9e94, a4071549) are flagged in XDR-65082, XDR-65465, XDR-65515, XDR-65575, and XDR-65806.

## Requirements
- **R1**: `.github/workflows/pr.yml:43` sets `ALPINE_VERSION: "3.24"`.
- **R2**: The inline Dockerfile RUN step in `build-and-push-docker-image` runs `apk upgrade --no-cache` after `apk update` and before `apk add`.
- **R3**: The `apk add` installs `'brotli-libs>=1.2.1-r0'` (single-quoted). If the fix is not served, the image build fails instead of shipping an unfixed image.
- **R4**: Everything else in the inline Dockerfile stays byte-for-byte the same (`FROM` template, `ln -s`, `mkdir`, `WORKDIR`, `ADD`, `chmod`, `USER nobody`, `ENTRYPOINT`).
- **R5**: `build/*.sh` still passes `shellcheck -S style` (`scripts/shellcheck-build.sh`).
- **R6**: The image built by the deploy job contains `brotli-libs` >= `1.2.1-r0`.

## Acceptance Criteria
```
AC-1: Workflow pins Alpine 3.24                                   (R1)
  Given .github/workflows/pr.yml
  When  grep -n 'ALPINE_VERSION' .github/workflows/pr.yml
  Then  exactly one line matches: 43:  ALPINE_VERSION: "3.24"

AC-2: RUN step upgrades base packages before installing           (R2, R3)
  Given build/deploy-actions.sh
  When  sed -n '67,71p' build/deploy-actions.sh
  Then  the output is exactly:
          RUN apk update && \
              apk upgrade --no-cache && \
              apk add --no-cache openjdk21-jre libc6-compat tini 'brotli-libs>=1.2.1-r0' && \
              ln -s /usr/bin/java /bin/java && \
              mkdir /ctia
        and the brotli-libs constraint is wrapped in single quotes

AC-3: No other changes                                            (R4)
  Given the branch diff against main
  When  git diff main --stat -- . ':!specs'
  Then  only .github/workflows/pr.yml (1 line changed) and
        build/deploy-actions.sh (1 line changed, 1 line inserted) appear

AC-4: Shellcheck still passes                                     (R5)
  Given the changed build/deploy-actions.sh
  When  the setup job's "Run shellcheck" step runs ./scripts/shellcheck-build.sh
  Then  it exits 0

AC-5: Deploy build pulls 3.24 and resolves the fix                (R6, post-merge CI)
  Given the change is merged to master
  When  the deploy job runs ./build/deploy-actions.sh build
  Then  the log shows a pull of hardened_alpine:3.24, apk resolves
        brotli-libs at >= 1.2.1-r0, and the job succeeds

AC-6: Built image carries the fixed brotli-libs                   (R6, needs docker)
  Given the image int-docker-build/ctia:<build_version> from AC-5
  When  docker run --rm --entrypoint apk <image> info -v brotli-libs
  Then  it prints brotli-libs-<v> with <v> >= 1.2.1-r0

AC-7: Build fails closed when the fix is not served               (R3, needs docker)
  Given the AC-2 RUN step with FROM alpine:3.22 (no 1.2.1-r0 in the index)
  When  docker build runs
  Then  apk add exits non-zero with an unsatisfiable brotli-libs>=1.2.1-r0 constraint
        and the build fails; in deploy-actions.sh, set -Eeuxo pipefail (:16) aborts
        before docker push (:85)
```
The ticket's other acceptance criteria are rollout outcomes that a person checks after merge, so this PR cannot test them: new digests in all five clusters, and the Wiz rescan resolving the five tickets. See Non-Goals.

**Test approach:** no new test files.
- No harness exists for the inline Dockerfile. `scripts-test/` covers only bb scripts under `scripts/actions`, registered in `scripts-test/test_runner.clj:10-13`.
- A test that pins the Alpine version would break on every future bump.
- AC-1 to AC-3 are the shell commands above. AC-4 runs in PR CI.
- AC-5 to AC-7 need docker and Artifactory credentials, which this sandbox lacks (`docker`, `bb`, and `shellcheck` are not installed).

## Constraints & Non-Goals
<details>
<summary>3 constraints, 5 non-goals</summary>

- **Constraints**
  - Change only the two files named in the ticket. The prior bump, e679329a (#1499, 3.20→3.22), is the precedent: it touched only `pr.yml`.
  - The constraint must stay single-quoted. The heredoc at `deploy-actions.sh:64` is unquoted, so quotes pass through to the Dockerfile. Without them, `/bin/sh` in RUN parses `>=1.2.1-r0` as a redirect, and `apk add brotli-libs` succeeds with no version check.
  - No new packages beyond the `brotli-libs` constraint.
- **Non-Goals**
  - The repo-root `Dockerfile` (`FROM clojure:alpine`). No workflow builds it.
  - The `containers/` dev and demo images.
  - Cleaning up the `apk update` index cache left in `/var/cache/apk`. This is existing behavior.
  - Release-branch cherry-pick, ECR re-tag, and moving the ctia/private-ctia deployments in int-use1, test-use1, and prod-use1/euw1/apn1. A person does these after merge, and the manifests live outside this repo.
  - Any other Alpine, JDK, or CI upgrades.
</details>

## Proposed Solution
Bump `ALPINE_VERSION` to `"3.24"`. Change the inline Dockerfile RUN to `apk update && apk upgrade --no-cache && apk add --no-cache openjdk21-jre libc6-compat tini 'brotli-libs>=1.2.1-r0' && …`. The image then gets the fixed brotli whichever way the package arrives, and the build fails if the fix isn't available.

<details>
<summary>Solution details and alternatives</summary>

`brotli-libs` can reach the image in two ways, and we can't tell from here which one applies:
1. **From the hardened base image.** `apk upgrade` replaces the base's copy with the 3.24 repo version.
2. **Through the `openjdk21-jre` dependency chain** (likely freetype). `apk add` resolves it fresh from the 3.24 index.

Both fixes depend on the bump, because Alpine 3.22 has no fixed version. The `>=1.2.1-r0` constraint guards against a stale mirror or index: the build fails at docker build time instead of shipping an unfixed image.

Alternatives rejected:
- **Bump only.** A copy that comes from the base image keeps the base's version.
- **`apk upgrade` on 3.22.** No fix exists for v3.22.
- **Target 3.23.** The 2026-11-06 ticket edit (approved by cduane) moved the target to 3.24.
- **Leave out the pin (the ticket marks it optional).** Recommended to include it: without it, the only failure signal is the next Wiz scan, after the image has already shipped.
</details>

## Detailed Design
<details>
<summary>2 files to modify, 0 to create; 9 verified claims</summary>

### Files to Modify
| File | Change Summary |
|------|---------------|
| `.github/workflows/pr.yml` | Line 43: `ALPINE_VERSION: "3.22"` → `ALPINE_VERSION: "3.24"`. |
| `build/deploy-actions.sh` | Inline Dockerfile heredoc: insert `    apk upgrade --no-cache && \` after line 67. Replace line 68 with `    apk add --no-cache openjdk21-jre libc6-compat tini 'brotli-libs>=1.2.1-r0' && \`. Result as in AC-2. |

### Usage / compatibility
`ALPINE_VERSION` has **1 consumer** (`deploy-actions.sh:65`), found by a repo-wide grep. The inline Dockerfile has **1 builder**, `build-and-push-docker-image` (`:49`). That function's only caller is `build-and-publish-package` (`:130`), which runs for master (`int`, `:151`) and release (`rel`, `:157`). No backward-compatibility work is needed.

### Verified causal claims
1. `pr.yml:43` is workflow-level `env`, so the deploy step (`pr.yml:448-456`) inherits it. `deploy-actions.sh:65` expands it at run time because the heredoc at `:64` is unquoted (`<<EOF`).
2. In the unquoted heredoc, bash drops each `\`+newline, so lines 67-70 reach Docker as **one** RUN line, and single quotes survive. Confirmed by rendering the proposed heredoc with bash: the output was `RUN apk update &&     apk upgrade --no-cache &&     apk add --no-cache … 'brotli-libs>=1.2.1-r0' && …`.
3. The current RUN (`:67-70`) has no `apk upgrade`, so base packages keep the base image's versions.
4. `push-prod-images` (`:95-113`) only re-tags `test-docker-build/ctia:$build_version` (`:96`). Prod therefore gets exactly what the release build produced, which is why test and prod share a4071549.
5. Master builds use `repo_prefix='int'` and release builds use `'test'` (`:51-57`), matching c12f9e94 (int) and a4071549 (test/prod).
6. The `deploy` job runs only when `github.event_name == 'push'` (`pr.yml:392`) on master or `vX.Y[.Z]` (`pr.yml:7-13`). **PR CI never builds this image.**
7. `set -Eeuxo pipefail` (`:16`): a failing `docker build` (`:84`) aborts the script before `docker push` (`:85`).
8. `scripts/shellcheck-build.sh:5` runs `shellcheck -S style build/*.sh` in the setup job (`pr.yml:144-145`), so it covers `deploy-actions.sh` on PRs.
9. The root `Dockerfile` is not in the deploy path. The deploy step writes its own Dockerfile into `mktemp -d` (`:61-64`), and the only other Docker use in `pr.yml` is `docker compose` for `containers/dev` (`:188`, `:259`).

### Data Flow
- **Before:** `pr.yml` (3.22) → `deploy-actions.sh build` → `FROM hardened_alpine:3.22` + `apk add` → `int|test-docker-build/ctia` → (release) re-tag to prod ×3. brotli stays unfixed.
- **After:** same pipeline with `FROM hardened_alpine:3.24` + `apk upgrade` + a pinned `apk add`. Every downstream tag gets brotli-libs >= 1.2.1-r0, or the build fails before push.
</details>

## Dependencies & Configuration
No new dependencies. The only config change is the value of the `ALPINE_VERSION` workflow env var, 3.22 → 3.24.

<details>
<summary>2 external prerequisites (not verifiable in this session)</summary>

- `artifactory.devhub-cloud.cisco.com/sto-ccc-docker/hardened_alpine:3.24` exists and is pullable with the JFrog credentials from `INT-us-east-1-xdr-sre-user-jfrog`.
- `openjdk21-jre`, `libc6-compat`, `tini`, and `brotli-libs>=1.2.1-r0` are in the Alpine 3.24 repositories the hardened image is configured to use.

No migrations, secrets, or env vars are added.
</details>

## Risks & Edge Cases
<details>
<summary>6 risks identified</summary>

- **3.24 tag or packages missing (e.g. `libc6-compat` changes).** PR CI won't catch this (claim 6); the first master deploy fails. *Mitigation:* before merge, someone with Artifactory access runs a local `docker build` of the rendered Dockerfile. If the build fails on master, only int is affected, because release is untouched until the cherry-pick.
- **Unquoted constraint becomes a silent shell redirect.** *Mitigation:* AC-2 requires the single quotes. AC-7 shows the constraint is enforced.
- **`apk upgrade` overrides hardened-image packages.** If the hardened base ships customized or internal-repo packages, upstream versions could replace them. *Mitigation:* reviewers read the upgrade list in the AC-5 deploy log. If hardening packages are touched, narrow the upgrade to `apk upgrade --no-cache brotli-libs`.
- **Two-minor Alpine jump (musl, openjdk21 patch level).** *Mitigation:* soak in int-use1 before the release cherry-pick. The JDK major stays 21.
- **Less reproducible images.** Contents now track the repo state at build time. This is intended, since it is how fixes land.
- **Cherry-pick conflicts on the release branch** if its `pr.yml` or `deploy-actions.sh` differ. A person resolves these during rollout.
</details>

## Verification Checklist
<details>
<summary>Checklist (7 items)</summary>

- [ ] All acceptance criteria have corresponding tests (AC-1 to AC-3 as commands; AC-4 in PR CI; AC-5 to AC-7 post-merge or with docker)
- [ ] No existing tests broken (`scripts-test/test_runner.clj`, shellcheck)
- [ ] Non-goals are respected (diff limited to the 2 files, per AC-3)
- [ ] Dependencies documented if added (none added; external prerequisites listed)
- [ ] Edge cases from Risks section are tested (quoting: AC-2; fail-closed: AC-7)
- [ ] Deploy log reviewed for unexpected `apk upgrade` changes to hardened packages
- [ ] Local `docker build` against `hardened_alpine:3.24` attempted before merge, if credentials are available
</details>
