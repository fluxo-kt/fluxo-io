# fluxo-io — Agents Guide

Kotlin Multiplatform read-only random-access I/O lib. Single published
module `:fluxo-io-rad`. **Alpha** — public API may shift. Apache-2.0.

## Vibe & principles
- **Small, fast, allocation-careful, no deps.** CONTRIBUTING.md says: don't
  add deps or major new functionality. `kotlinx-coroutines` and
  `androidx-annotation` are `compileOnly` — consumers opt in.
- **Tests use real temp files, not mocks.** Every JVM impl is exercised
  through `AbstractRandomAccessDataTest` with concurrency + randomized
  reads. Tests live ONLY in `jvmTest`.
- **Public API is locked** by JVM/KLIB Binary Compatibility Validator and TS
  API dumps. Dumps under
  `fluxo-io-rad/api/`. Any intended ABI change must be reflected via
  `apiDump`. CI fails on drift.
- **Java surface is shaped by file-level
  `@file:JvmName("Rad") @file:JvmMultifileClass`** on every
  `*RadAccessor.kt`. Kotlin `RadByteBufferAccessor(…)` becomes Java
  `Rad.forByteBuffer(…)`. Renaming a factory or its `@JvmName("forX")` is
  binary-incompatible even if the Kotlin name is unchanged. Cross-check
  `api/jvm/fluxo-io-rad.api` before any rename. (No custom lint enforces
  this — BCV catches the ABI side, naming is review-gated.)
- `explicitApi()` is on. New public symbols need explicit `public` + KDoc.
- `optInInternal = true` (fluxo-kmp-conf) auto-opts the project's own code
  into `InternalFluxoIoApi`, so internal accessors don't sprinkle `@OptIn`.

## Layout
- `:fluxo-io-rad` — only published module.
  - `commonMain` — `expect interface RandomAccessData` + `ByteArrayRad`.
  - `commonJvmMain` — JVM+Android impl set (`ByteBuffer`, mmap,
    `FileChannel`, `RandomAccessFile`, `SeekableByteChannel`, stream
    factories, async).
  - `nonJvmMain` — JS / Native / Wasm-JS, `ByteArray`-only.
    Wasm-WASI is **explicitly disabled** (`allDefaultTargets(wasmWasi = false)`).
- Root `build.gradle.kts` — umbrella via `fkcSetupRaw {…}`, Kover
  aggregation, resolves `extra["androidJar"]` from `local.properties` /
  `ANDROID_SDK_ROOT` / `ANDROID_HOME` so non-Android source sets can
  `compileOnly` the SDK jar.
- `gradle/libs.versions.toml` — single source of truth for versions and
  toolchain (Kotlin lang/JVM target, AGP, minSdk because `AutoCloseable`
  requires API 19).
- `config/{detekt.yml,lint.xml}`; `.editorconfig` (ktlint_official,
  line=100, indent=4 for `.kt`/`.kts`).

## Core architecture
- `RandomAccessData` is `expect interface` annotated
  `@SubclassOptInRequired(InternalFluxoIoApi::class)`. JVM `actual` adds
  `Closeable`, `ByteBuffer` reads, `asInputStream()`, `transferTo`,
  `readByteAt`.
- **`SharedCloseable`** (atomicfu refcount) is the keystone:
  `subsection()` calls `retain()` on the underlying
  `SharedDataAccessor`; closing decrements; resource frees on the last
  close. **Each subsection MUST be closed independently** — otherwise the
  underlying handle leaks.
- `SharedDataAccessor` owns the JVM resource and the only
  `read(bytes, position, offset, length)` primitive.
  `AccessorAwareRad<A>` wraps it with offset/size + bounds checks
  (`fluxo.io.util.IoUtil`). `BasicRad` (expect/actual) carries the JVM
  common impl: `readByteAt`, `transferTo`, `read(ByteBuffer, position)`,
  suspend wrappers, `Java8BufferCompat`-based buffer handling.
- **`Thread.interrupt()` cooperation**: `BasicRad.readFully` and
  `readByteAt` check `Thread.interrupted()` and throw
  `IOException("Thread interrupted")`. Bare `read()` does not.
- Suspend reads (`readAsync` / `readFullyAsync`) **do NOT switch to
  `Dispatchers.IO`** — caller must wrap.
- `@Blocking` is `expect annotation @OptionalExpectation`; JVM
  typealiases `org.jetbrains.annotations.Blocking`.
- Logging: single global hook
  `setFluxoIoLogger((String, Throwable?) -> Unit)`. No SLF4J. Don't add
  one.
- Public coroutine wrappers around JDK NIO async channels live in
  `fluxo.io.nio` (`aLock`, `aRead`, `aWrite`, `aAccept`, `aConnect`) — they
  cancel-and-close-on-coroutine-cancellation, so reusing the underlying
  `Async*Channel` after cancellation is unsafe.

## Build, test, regen baselines
```
./gradlew check                    # full verify (BCV apiCheck, kover, detekt, AGP lint wiring, depGuard, tests)
./gradlew :fluxo-io-rad:jvmTest    # JVM unit tests
./gradlew :fluxo-io-rad:apiDump    # refresh BCV after intentional API change
./updateBaseline                   # CANONICAL regen: verification metadata+yarnLock+apiDump+depGuardBaseline+detektBaselineMerge (CI=true RELEASE=true, no build/config cache, isolated .gradle/update-baseline home unless GRADLE_USER_HOME is set)
```
- Configuration cache is on with `problems=fail` and `max-problems=0`.
  Don't capture `Project` or build-script instances in task actions.
- Tests use `runTest(timeout = 9.seconds)`. Concurrency is exercised by
  `AbstractRandomAccessDataTest.testConcurrency` against a real temp file.
- Lincheck models must keep each `@Operation` to one API action. Do not
  combine mutation plus later observation (for example `close(); isOpen`) in
  one operation; expose the observation as a separate operation or deterministic
  regression test.
- CI runs across macOS/Windows/Ubuntu on JDK 21. SNAPSHOT publishing is a
  separate macOS job after the matrix succeeds, only on default-branch pushes
  when the catalog version ends with `-SNAPSHOT`. `pr-baseline.yml` is a
  manual same-repository baseline-refresh workflow, not automatic Dependabot
  handling.

## Adding a new RAD impl (canonical recipe)
1. JVM: `internal class FooRad(access, offset, size) :
   AccessorAwareRad<FooAccess>(access, offset, size)`. Inner
   `FooAccess(api, resources) : SharedDataAccessor(resources)` exposes
   `size: Long` + `read(bytes, position, offset, length)`.
   `getSubsection0` returns `FooRad(access, globalPosition, length)` —
   same `access`, parent retains.
2. Optional perf overrides: `read(ByteBuffer, position)` and
   `transferTo(WritableByteChannel, …)` (see `FileChannelRad`).
3. Public factory in `FooRadAccessor.kt` with
   `@file:JvmName("Rad") @file:JvmMultifileClass` and `@JvmName("forFoo")`
   per overload. Mark `@Blocking` if the constructor opens resources.
4. Test: extend `AbstractRandomAccessDataTest(factory)`.
5. Run `./updateBaseline`. Inspect `api/jvm/fluxo-io-rad.api` diff
   before committing.

## Conventions and traps
- **Conventional commits required**, strict type set listed in
  CONTRIBUTING.md. Keep history flat (`--ff-only`); fast-forward merges
  are triggered by an exact `/ff` or `/fast-forward` PR comment after the
  workflow verifies the commenter has write, maintain, or admin permission.
- **Adding a new submodule** → also update `.github/workflows/build.yml`
  (called out in `settings.gradle.kts`).
- **Mmap limit**: only files < 2 GiB (`Int.MAX_VALUE`) work with
  `RadByteBufferAccessor(File|FileChannel|FileDescriptor|FileInputStream)`.
- **Don't recommend `RadAsyncFileChannelAccessor`** — deprecated, slow,
  direct-buffer OOM-prone. Same for `RadMemoryMappedAccessor` →
  `RadByteBufferAccessor`.
- **`fluxo.io.nio.Java8BufferCompat`** (`flipCompat`, `clearCompat`, …)
  must be used in JVM source instead of raw `Buffer.flip()` to dodge
  the JDK 9 covariant-override `NoSuchMethodError`.
- **Coroutines is `compileOnly`** in lib code. Consumers using suspend
  APIs must depend on `kotlinx-coroutines-core`.
- **AGP 9 Android-KMP trap:** `androidMain` must explicitly depend on
  `commonJvmMain` here, otherwise Android compilation cannot see JVM
  actuals. Consumer keep rules are not published by default; use
  `android.optimization.consumerKeepRules { publish = true; file(...) }`
  and verify `bundleAndroidMainAar` contains `proguard.txt`.
- **No `updateLintBaseline` task under AGP 9 Android-KMP** here. Keep
  `updateBaseline` to executable tasks only; verify available lint tasks with
  `./gradlew tasks --all --quiet | rg -i 'lint|baseline'` before adding one.
- `updateBaseline` uses an isolated `.gradle/update-baseline` Gradle user home
  when `GRADLE_USER_HOME` is unset. The shared global daemon registry can receive
  stop signals from other worktrees and kill long baseline runs.
- `useDokka = true` only applies Dokka for non-SNAPSHOT publications in
  fluxo-kmp-conf. Verify release docs with a temporary non-SNAPSHOT version or
  release config; keep `dokka-base`/`templating-plugin` in verification metadata.
- **Central Portal only.** S01/OSSRH and legacy host APIs are dead here;
  workflows must use `publishToMavenCentral`, with manual Portal release.
- **Never re-add JitPack.** It is Linux-only and does not support KMP
  (jitpack#3853); this lib has Apple/Native targets, so a JitPack build emits a
  broken metadata-incomplete artifact. Removal + the JitPack repository ban are
  correct — keep both.
- **`publishSnapshot` (build.yml) auto-fires on a `dev` push** when the catalogue
  version ends `-SNAPSHOT` (gated `event==push && repo==fluxo-kt/fluxo-io &&
  ref==default_branch`). Merging to `dev` *is* a publish attempt; it reds (in
  isolation, matrix stays green) until the five publish secrets exist. Pushing a
  feature branch does not publish.
- **dep-submission graph is an allowlist, not a denylist.**
  `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS=".*(Compile|Runtime)Classpath"` (full-
  string `String.matches`) ships only consumer-facing resolved classpaths. The
  denylist `^(?!(classpath)).*` failed because project-qualified names like
  `:classpath` don't start with the literal, leaking AGP/protobuf/bouncycastle
  build tooling into the submitted graph → phantom
  `security_update_dependency_not_found` Dependabot jobs. Excluding build tooling
  from the *consumer* graph is accurate (consumers never see it), not vuln-hiding.
- **Action pins rot.** Every remote action needs a 40-char SHA + `# vX.Y.Z`
  comment (policy-enforced); when bumping a major, verify Node-runtime compat
  (e.g. github-script v7/Node20 → v9/Node24). Pinned-action rot is real: a pinned
  action whose *own* deps are unpinned can break later (actionlint v1.0.3 crashed
  `ERR_PACKAGE_PATH_NOT_EXPORTED` via an unpinned transitive `@actions/tool-cache`
  under github-script@v7). `setup-gradle` is special — its allowed SHA is the
  hardcoded `SETUP_GRADLE_PINNED_REF` constant in `VerifyBuildPolicyTask.java`;
  bumping the workflow pin requires editing that constant in the same change.
- **harden-runner `egress-policy: block` allowlists rot too.** Any host an action
  *transitively* reaches must be listed, and GitHub migrates those hosts. The
  actionlint job downloads its binary via `@actions/tool-cache` from a release
  asset `browser_download_url`, which 302-redirects off `github.com` to
  **`release-assets.githubusercontent.com`** (the current asset host, replacing the
  legacy `objects.githubusercontent.com` — keep both listed). Cache-hit runs (push)
  hide the gap; cache-miss (PR) reds with `ECONNREFUSED`. To re-derive the real host
  for any release download: `curl -sIL <browser_download_url> | grep -i ^location`.
  Don't reverse-DNS the blocked IP — harden-runner reports the destination IP, not
  the hostname. Never "fix" this by downgrading `block`→`audit` (disables blocking).
- **Don't strand local-only branches.** Push WIP to `origin` (bus-factor; a
  single local copy is what stranded the toolchain modernization). Divergence
  happens — expect to rebase onto `origin/dev` before an `--ff-only` land.
- Build floors are deliberate: `javaLangTarget=17`, `androidMinSdk=21`,
  `kotlinLangVersion=2.1`. Do not raise Kotlin language without fresh
  fluxo-kmp-conf/Detekt compatibility evidence.
- `Java8BufferCompat` is intentionally kept for source compatibility.
- `kotlinx-io`, Okio, and JMH are catalogue-reserved; don't wire them without
  an actual feature need.
- JSR305 stays at `3.0.2`; upstream has no newer release.
- `kotlin.concurrent.atomics` is still experimental; keep AtomicFU.
- TS API checks, KLIB/JVM BCV, and Dokka publication jars are enforced.
- Keep the explicit `apiValidation { klib { enabled = true } }` in
  `:fluxo-io-rad`; fluxo-kmp-conf `klibValidationEnabled = true` alone did
  not enable BCV 0.18 KLIB tasks here.
- Dependency verification metadata is regenerated by `./updateBaseline`; dep
  or plugin updates must refresh it with the dependency change.
- **PGP key material is committed, not keyserver-fetched.** `updateBaseline`
  passes `--export-keys`, writing `gradle/verification-keyring.keys` (ASCII,
  tracked, reviewable; the binary `.gpg` is gitignored). CI verifies signatures
  against this local keyring — never public keyservers. **Dirty-home trap:** a
  green local `./gradlew check` does NOT prove CI will pass. Verification fires
  only when an artifact *enters* a `GRADLE_USER_HOME` (download time), so a
  populated home silently passes artifacts CI would reject; a warm daemon and a
  stored config-cache entry further skip re-resolution. Three masking layers →
  the ONLY faithful local repro is a **fresh empty `GRADLE_USER_HOME`** (copy in
  `wrapper/` to skip the distro download, leave `modules-2` empty) run with
  `--no-daemon` and cleared `.gradle/configuration-cache`. The same trap hides
  missing keys: without the committed keyring CI must fetch every `<trusted-key>`
  from flaky keyservers and reds on ~126 buildscript-classpath artifacts. Keep keyservers *enabled* (no
  `<key-servers enabled="false"/>`): the keyring satisfies verify-time, and the
  fallback is what lets `--refresh-keys` fetch material for newly-added deps —
  disabling it would break the regen writer path. After a dep change, re-run
  `./updateBaseline` and confirm every metadata `<trusted-key>` id has matching
  key material in the keyring before pushing.
- **`junit-bom` is trusted by coordinate** (`<trusted-artifacts>`), not
  checksummed. It is a transitive BOM *platform* on the buildscript classpath;
  its per-version `.module` variants resolve only during config-cache
  instrumentation, which `--write-verification-metadata` (forced `--no-CC`)
  cannot reach, so `updateBaseline` *cannot* auto-generate their sha256. Trust
  the BOM by coordinate (metadata-only, no code, version-proof). Do NOT add
  per-version sha256 — it recreates the catch-22 on the next junit bump.
  **Same class, same fix for `kotlinx-coroutines-bom` and `jackson-base`**
  (Jackson parent POM): BOM/parent metadata POMs the CC path drags onto the
  buildscript classpath at versions `updateBaseline` (`--no-CC`) never resolves.
  Symptom is `checksum is missing from verification metadata` for a `.pom` on the
  Plugin Portal. The fragile fix is per-version `<component>` entries — coroutines-bom
  had 1.7.3/1.9.0/1.11.0 yet CI pulled 1.8.0 → red. Trust the coordinate instead;
  code JARs stay checksum-verified. The same single-OS limitation hits **toolchain
  binaries** (`org.nodejs:node`, `com.github.webassembly:binaryen`): platform-
  multiplied + version-churned, so `updateBaseline` captures only the runner's own
  variant (was darwin-arm64-only → linux/windows CI red). Trust these by coordinate
  too — non-shipped build infra from official immutable sources; shipped artifacts
  stay checksummed. General rule: **verify shipped/library artifacts by checksum;
  trust non-shipped build infrastructure (BOM/parent POMs + toolchain binaries) by
  coordinate.**
- `verifyBuildPolicy` enforces non-negotiable build/security invariants,
  including pinned actions/runners, Central Portal, TS API checks, and Dokka.
  **Threat model is accidental-regression, not anti-malicious:** it scans text
  for required/forbidden literals, so it is bypassable by string concat
  (`"jit"+"pack.io"`) and asserts literal *presence*, not effective value (both
  `useDokka = true` and a later `= false` present would pass). Don't mistake it
  for tamper-proof; don't gold-plate it either. It scans tracked **docs** too
  (incl. AGENTS.md/CLAUDE.md), so never write a forbidden literal verbatim in
  Markdown — name it descriptively (e.g. "the JitPack repository"); a bare
  occurrence reds the gate (it bit the JitPack-ban note once).
- AGP 9 Android-KMP has no `:fluxo-io-rad:lint` task here; use the discovered
  lint packaging tasks (`compileLint`, `androidCompileLintChecks`,
  `bundleAndroidMainLocalLintAar`) plus `check`.
- Current warning debt is upstream/plugin-shaped: Detekt calls deprecated
  Gradle `ReportingExtension.file`, Kotlin/JS resolves `*NpmAggregated` during
  configuration, and `Java8BufferCompat` keeps Kotlin internal `InlineOnly` for
  source-compatible Java 8 buffer wrappers.
- Generated/build outputs (`build/`, `.gradle/`, `.kotlin/`) — never edit;
  never commit. `.kotlin-js-store/yarn.lock` is tracked baseline output and is
  regenerated via `./gradlew kotlinUpgradeYarnLock` (run by `updateBaseline`);
  do not edit other `.kotlin-js-store/` files. GitHub auto-detects this lockfile
  for security advisories with no per-path graph exclusion (inherent limit); any
  flagged npm transitives are Kotlin/JS *dev-toolchain* deps, never shipped —
  benign. If the jobs get noisy, the only lever is an `npm` ecosystem block in
  `dependabot.yml` scoped to `/.kotlin-js-store` with a catch-all `ignore`.
  The PR-time `dependency-review` gate sees the same lockfile and fails only on
  **added** vulnerable deps. GitHub scopes every yarn.lock entry as `runtime`
  (mocha, typescript included), so `fail-on-scopes` can't filter them; waive a
  proven dev-toolchain advisory per-GHSA via `allow-ghsas` (keeps low+ strictness
  for shipped JVM/actions deps) rather than blanket-raising `fail-on-severity`.
  Verify scope/manifest with the dependency-graph compare API
  (`gh api repos/<o>/<r>/dependency-graph/compare/<base>...<head>`).
- Project flags in `gradle.properties` (`MAX_DEBUG`, `COMPOSE_METRICS`,
  `USE_KOTLIN_DEBUG`, `LOAD_KMM_CODE_COMPLETION`) are read by
  `fluxo-kmp-conf`; semantics live in that plugin.

## Surprises rule (READ THIS)
**If anything in this repo surprises you — a build flag, a hidden opt-in,
a deprecated alias still wired up, an ABI-dump diff that looks innocent
but isn't — TELL THE USER and append a brief note here in AGENTS.md so
the next agent doesn't have to relearn it.** Memory > recovery.

## Pointers
- User-facing usage / platform matrix → `README.md`
- Commit + PR rules → `CONTRIBUTING.md`
- Roadmap / known gaps → `ROADMAP.md`
- Build behaviour comes from `io.github.fluxo-kt.fluxo-kmp-conf`
  (`fkcSetupRaw`/`fkcSetupMultiplatform`); when something Gradle-side is
  mysterious, read that plugin's source — not this repo.
