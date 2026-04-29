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
./gradlew check                    # full verify (BCV apiCheck, kover, detekt, lint, depGuard, tests)
./gradlew :fluxo-io-rad:jvmTest    # JVM unit tests
./gradlew :fluxo-io-rad:apiDump    # refresh BCV after intentional API change
./updateBaseline                   # CANONICAL regen: yarnLock+apiDump+depGuardBaseline+detektBaselineMerge+updateLintBaseline (CI=true RELEASE=true, no cache)
```
- Configuration cache is on with `problems=warn`. CC-unsafe build script
  edits silently warn, not fail. Don't capture `Project` at execution
  time.
- Tests use `runTest(timeout = 9.seconds)`. Concurrency is exercised by
  `AbstractRandomAccessDataTest.testConcurrency` against a real temp file.
- CI runs across macOS/Windows/Ubuntu and multiple JDKs; SNAPSHOT
  publishes only on macOS push to default branch (see
  `.github/workflows/build.yml`). CodeQL is gated by the `CODE_QL` env
  var. Dependabot PRs are auto-amended by `pr-baseline.yml`.

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
- **`SECURITY.md` and `RELEASING.md` are stubs** ("_To be written_").
  Don't trust them.
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
