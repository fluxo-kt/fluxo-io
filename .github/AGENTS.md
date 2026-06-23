# `.github/` — Workflow, release & verification traps

Scope: `.github/workflows/`, `dependabot.yml`, dependency-graph submission,
verification-metadata, Central Portal release. Library architecture and
language traps live in the **root `AGENTS.md`** — read that first.

## Commit & PR

- **Conventional commits required** (strict type set: see `CONTRIBUTING.md`).
- Keep history flat (`--ff-only`). FF merges are triggered by an exact `/ff`
  or `/fast-forward` PR comment; `pr-fast-forward.yml` first verifies the
  commenter has write/maintain/admin permission.
- **Adding a new submodule** → also update `.github/workflows/build.yml`
  (also called out in `settings.gradle.kts`).

## `/ff` recursion-guard (load-bearing)

A `/ff` land is a `git push` authored by `GITHUB_TOKEN`. GitHub suppresses
workflow triggers for `GITHUB_TOKEN`-authored pushes (recursion guard). That
silently breaks two pipelines after a `/ff` to `dev`:

1. **`publishSnapshot` does NOT fire.** To publish a snapshot, push `dev`
   with a real user/PAT, or dispatch the workflow manually — not via `/ff`.
2. **`dependency-submission.yml` does NOT fire** → dep-graph stays stale →
   lingering Security-tab alerts. After a `/ff` that should refresh deps:
   `gh workflow run dependency-submission.yml --ref dev`.

## `publishSnapshot` (in `build.yml`)

- Auto-fires on `dev` push when the catalog version ends `-SNAPSHOT`, gated
  `event==push && repo==fluxo-kt/fluxo-io && ref==default_branch`. A
  feature-branch push does **not** publish.
- The job already has a per-step `is_snapshot == 'true'` gate, so it fires
  but no-ops when the catalogue version is non-SNAPSHOT.
- Without the five publish secrets the publish steps **hard-fail** → false-red
  (secrets absent, not publish broken). If noisy, gate via a preflight
  `has_secrets` job — secrets are unreadable in a job-level `if:`. Do **not**
  paper over with `continue-on-error` (masks real publish failures).

## Release publication

- **Central Portal only.** S01/OSSRH and legacy host APIs are dead;
  workflows must use `publishToMavenCentral` with **manual Portal release**
  (`automaticRelease = false`). Do not call `publishAndReleaseToMavenCentral`.
- **Never re-add `jit` + `pack` (the well-known Linux-only Gradle build
  service).** It does not support KMP (`jitpack#3853`); this lib has
  Apple/Native targets → broken metadata-incomplete artifact. The
  repository ban must stay.
- **`useDokka = true`** applies Dokka only for non-SNAPSHOT publications in
  fluxo-kmp-conf. Verify release docs with a temporary non-SNAPSHOT version
  or release config; keep `dokka-base`/`templating-plugin` in verification
  metadata.

### Signing-secrets gap (open, maintainer-action)

`release.yml` + `build.yml` reference `SIGNING_IN_MEMORY_KEY[_ID|_PASSWORD]`
(also documented in `RELEASING.md`). The fluxo-kt **org** secrets store
holds only `SIGNING_KEY` + `SIGNING_PASSWORD` (no `_ID` of any name). Latent
since `b968333` because `release.yml` has never run and `publishSnapshot` is
suppressed on `/ff`. Two unblock routes:

1. Create org secrets matching workflow refs (`gpg --list-secret-keys
   --keyid-format=long` derives the `_ID`).
2. Edit workflow refs to use existing names + add only `SIGNING_KEY_ID`.
   Keep `ORG_GRADLE_PROJECT_signingInMemoryKey` env-var name (vanniktech
   reads that property).

Until fixed, do not push a non-SNAPSHOT release tag — it surfaces here.

## `pr-baseline.yml`

Manual same-repository baseline-refresh workflow, **not** automatic
Dependabot handling. It hard-restricts to `dependabot/*` head branches
(`case "$head_ref" in dependabot/*) ;; *) exit 1 ;;`). Consequence: bumps
that need a metadata regen on a non-Dependabot branch (e.g. an already-
closed PR a maintainer wants to revive) require either reopening a
Dependabot PR or modifying the branch filter (cross-cutting). See the
Lincheck 3.6 case in memory.

## Dependabot proposes untagged re-publishes

Dependabot reads a registry's `<versions>` *list*, not `<latest>`/upstream
tags — it can suggest an artifact the maintainer never blessed. **Rule:**
before folding a dep/plugin bump, confirm a matching upstream tag/release
AND registry `<latest>`; else keep the endorsed version and close the PR.
Dep-verification checksums the bytes you declare, NOT "endorsed" — so this
review is the only gate. Reference case in memory: `com.osacky.doctor 0.12.1`
— no `v0.12.1` tag, Portal `<latest>` still 0.12.0, identical POM → re-cut.

## Dependency-submission graph

`DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS=".*(Compile|Runtime)Classpath"`
(full-string `String.matches`) is an **allowlist**, not a denylist. It ships
only consumer-facing resolved classpaths. The earlier denylist
`^(?!(classpath)).*` failed because project-qualified names like
`:classpath` don't start with the literal, leaking AGP/protobuf/bouncycastle
build tooling → phantom `security_update_dependency_not_found` Dependabot
jobs. Excluding build tooling from the *consumer* graph is **accurate**
(consumers never see it), not vuln-hiding.

### Zombie maven alerts

The pre-#29 `setup-gradle` auto-graph submitted a `settings.gradle.kts`
manifest (buildscript classpath incl. tooling) under a *different
correlator* than the live submitter. GitHub keeps the last snapshot per
correlator indefinitely, never auto-evicts a stopped producer, and exposes
**no API to read a submitted snapshot's correlator** (SBOM + legacy GraphQL
are blind to submitted snapshots) — a targeted empty-snapshot eviction
can't reach the orphan, so **dismiss `not_used`** (build tooling, never
shipped). New orphans are structurally prevented (auto-graph off via
`GITHUB_DEPENDENCY_GRAPH_ENABLED: false` + single stable-correlator
submitter). Diagnose via the submitted-run artifact (ground truth, not the
cached SBOM) + `gh api …/dependabot/alerts --jq '…manifest_path…'`.

### `.kotlin-js-store/yarn.lock` npm alerts (benign, persistent)

GitHub auto-detects the lockfile for security advisories — no per-path
graph exclusion exists (inherent limit). Any flagged npm transitives are
Kotlin/JS **dev-toolchain** deps, never shipped. If jobs get noisy, the
only lever is an `npm` ecosystem block in `dependabot.yml` scoped to
`/.kotlin-js-store` with a catch-all `ignore`. The PR-time
`dependency-review` gate sees the same lockfile and fails only on **added**
vulnerable deps; GitHub scopes every yarn.lock entry as `runtime` (mocha,
typescript included), so `fail-on-scopes` can't filter them. Waive a proven
dev-toolchain advisory per-GHSA via `allow-ghsas` (keeps low+ strictness
for shipped JVM/actions deps) rather than blanket-raising
`fail-on-severity`. Verify scope/manifest with the dependency-graph compare
API (`gh api repos/<o>/<r>/dependency-graph/compare/<base>...<head>`).

Regenerate the lockfile via `./gradlew kotlinUpgradeYarnLock` (run by
`./updateBaseline`); do not hand-edit other `.kotlin-js-store/` files.

## Action pins rot

- Every remote action needs a 40-char SHA + `# vX.Y.Z` comment
  (policy-enforced). When bumping a major, verify Node-runtime compat
  (e.g. `actions/github-script` v7/Node20 → v9/Node24).
- **Transitive rot is real:** a pinned action whose own deps are unpinned
  can break later. `actionlint v1.0.3` crashed `ERR_PACKAGE_PATH_NOT_EXPORTED`
  via an unpinned transitive `@actions/tool-cache` under
  `actions/github-script@v7`.
- **`setup-gradle` is special** — its allowed SHA is the hardcoded
  `SETUP_GRADLE_PINNED_REF` constant in `VerifyBuildPolicyTask.java`.
  Bumping the workflow pin requires editing that constant in the same
  change.

## `harden-runner` `egress-policy: block`

Any host an action **transitively** reaches must be listed, and GitHub
migrates those hosts. The actionlint job downloads its binary via
`@actions/tool-cache` from a release asset `browser_download_url`, which
302-redirects off `github.com` to **`release-assets.githubusercontent.com`**
(current asset host, replacing legacy `objects.githubusercontent.com` —
keep both listed). Cache-hit runs (push) hide the gap; cache-miss (PR) reds
with `ECONNREFUSED`. Re-derive the real host for any release download:
`curl -sIL <browser_download_url> | grep -i ^location`. Don't reverse-DNS
the blocked IP — harden-runner reports the destination IP, not the
hostname. Never "fix" this by downgrading `block`→`audit` (disables
blocking).

## Verification metadata (`gradle/verification-metadata.xml`)

Regenerated by `./updateBaseline`; dep or plugin updates **must** refresh
it with the dependency change.

### PGP keyring committed, not keyserver-fetched

`updateBaseline` passes `--export-keys`, writing
`gradle/verification-keyring.keys` (ASCII, tracked, reviewable; the binary
`.gpg` is gitignored). CI verifies signatures against this local keyring —
never public keyservers.

**Dirty-home trap:** a green local `./gradlew check` does NOT prove CI will
pass. Verification fires only when an artifact *enters* a
`GRADLE_USER_HOME` (download time), so a populated home silently passes
artifacts CI would reject; a warm daemon and a stored config-cache entry
further skip re-resolution. Three masking layers → the ONLY faithful local
repro is a **fresh empty `GRADLE_USER_HOME`** (copy in `wrapper/` to skip
the distro download, leave `modules-2` empty) run with `--no-daemon` and
cleared `.gradle/configuration-cache`. The same trap hides missing keys:
without the committed keyring CI must fetch every `<trusted-key>` from
flaky keyservers and reds on ~126 buildscript-classpath artifacts. Keep
keyservers **enabled** (no `<key-servers enabled="false"/>`): the keyring
satisfies verify-time, and the fallback is what lets `--refresh-keys` fetch
material for newly-added deps — disabling it would break the regen writer
path. After a dep change, re-run `./updateBaseline` and confirm every
metadata `<trusted-key>` id has matching key material in the keyring before
pushing.

### Trust-by-coordinate vs. checksum (general rule)

**Verify shipped/library artifacts by checksum; trust non-shipped build
infrastructure (BOM/parent POMs + toolchain binaries) by coordinate.**

- `junit-bom`, `kotlinx-coroutines-bom`, `jackson-base` (Jackson parent
  POM): BOM/parent metadata POMs the CC path drags onto the buildscript
  classpath at versions `updateBaseline` (forced `--no-CC`) never resolves
  → `checksum is missing from verification metadata` for a `.pom` on the
  Plugin Portal. Per-version `<component>` entries are fragile —
  coroutines-bom had 1.7.3/1.9.0/1.11.0 yet CI pulled 1.8.0 → red. Trust
  by coordinate (`<trusted-artifacts>`).
- Toolchain binaries (`org.nodejs:node`, `com.github.webassembly:binaryen`):
  platform-multiplied + version-churned, so `updateBaseline` captures only
  the runner's own variant. Trust by coordinate too — non-shipped build
  infra from official immutable sources.

Code JARs stay **checksum-verified**.

### `updateBaseline` script gotchas

- Uses an isolated `.gradle/update-baseline` Gradle user home when
  `GRADLE_USER_HOME` is unset. Reason: the shared global daemon registry
  can receive stop signals from other worktrees and kill long baseline
  runs.
- Forces `--no-CC` to write metadata. Versions only resolved under
  config-cache instrumentation (CI path) are unreachable to the write-pass
  → some transitives (lincheck-on-bump, parent POMs) need a primed cache
  before `./updateBaseline` can capture them.

## Repository policy enforcement

`verifyBuildPolicy` enforces non-negotiable build/security invariants
including pinned actions/runners, Central Portal, TS API checks, and Dokka.
**Threat model is accidental-regression, not anti-malicious:** it scans
text for required/forbidden literals, so it is bypassable by string concat
and asserts literal *presence*, not effective value (both `useDokka = true`
and a later `= false` present would pass). Don't mistake it for
tamper-proof; don't gold-plate it either. **It scans tracked docs too**
(incl. `AGENTS.md` / `CLAUDE.md`), so never write a forbidden literal
verbatim in Markdown — name the referenced service descriptively; a bare
URL occurrence reds the gate.

## Pointers

- Library architecture, RAD recipe, Java surface → root `AGENTS.md`
- Release flow → `../RELEASING.md`
- Security policy / vuln reporting → `../SECURITY.md`
- Commit + PR rules → `../CONTRIBUTING.md`
