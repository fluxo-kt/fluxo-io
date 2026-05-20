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
- **Public API is BCV-locked** (Binary Compatibility Validator; JVM + KLIB
  enforced, TS dumps exist but unchecked). Dumps under
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
  are triggered by an OWNER/MEMBER posting `/ff` or `/fast-forward` in a
  PR comment.
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
- Build floors are deliberate: `javaLangTarget=17`, `androidMinSdk=21`,
  `kotlinLangVersion=2.1`. Do not raise Kotlin language without fresh
  fluxo-kmp-conf/Detekt compatibility evidence.
- `Java8BufferCompat` is intentionally kept for source compatibility.
- `kotlinx-io`, Okio, and JMH are catalogue-reserved; don't wire them without
  an actual feature need.
- JSR305 stays at `3.0.2`; upstream has no newer release.
- `kotlin.concurrent.atomics` is still experimental; keep AtomicFU.
- TS API checks, KLIB/JVM BCV, and Dokka publication jars are enforced.
- Dependency verification metadata is regenerated by `./updateBaseline`; dep
  or plugin updates must refresh it with the dependency change.
- `verifyBuildPolicy` enforces non-negotiable build/security invariants,
  including pinned actions/runners, Central Portal, TS API checks, and Dokka.
- AGP 9 Android-KMP has no `:fluxo-io-rad:lint` task here; use the discovered
  lint packaging tasks (`compileLint`, `androidCompileLintChecks`,
  `bundleAndroidMainLocalLintAar`) plus `check`.
- Current warning debt is upstream/plugin-shaped: Detekt calls deprecated
  Gradle `ReportingExtension.file`, Kotlin/JS resolves `*NpmAggregated` during
  configuration, and `Java8BufferCompat` keeps Kotlin internal `InlineOnly` for
  source-compatible Java 8 buffer wrappers.
- Generated/build outputs (`build/`, `.gradle/`, `.kotlin/`,
  `.kotlin-js-store/`) — never edit; never commit. JS yarn lockfile
  regenerated via `./gradlew kotlinUpgradeYarnLock` (run by
  `updateBaseline`).
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
