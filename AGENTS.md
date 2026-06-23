# fluxo-io — Agents Guide

Kotlin Multiplatform read-only random-access I/O lib. Single published
module `:fluxo-io-rad`. **Alpha** — public API may shift. Apache-2.0.

Workflow / release / verification-metadata traps live in
[`.github/AGENTS.md`](.github/AGENTS.md). Auxiliary docs:
[`README.md`](README.md), [`CONTRIBUTING.md`](CONTRIBUTING.md),
[`RELEASING.md`](RELEASING.md), [`SECURITY.md`](SECURITY.md),
[`ROADMAP.md`](ROADMAP.md).

## Vibe & principles

- **Small, fast, allocation-careful, no deps.** `CONTRIBUTING.md` says:
  don't add deps or major new functionality. `kotlinx-coroutines` and
  `androidx-annotation` are `compileOnly` — consumers opt in.
- **Tests use real temp files, not mocks.** Every JVM impl is exercised
  through `AbstractRandomAccessDataTest` with concurrency + randomized
  reads. Tests live ONLY in `jvmTest`.
- **Public API is locked** by JVM/KLIB Binary Compatibility Validator and
  TS API dumps. Dumps under `fluxo-io-rad/api/`. Any intended ABI change
  must be reflected via `apiDump`. CI fails on drift.
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
    Wasm-WASI is **explicitly disabled**
    (`allDefaultTargets(wasmWasi = false)`).
- Root `build.gradle.kts` — umbrella via `fkcSetupRaw {…}`, Kover
  aggregation, resolves `extra["androidJar"]` from `local.properties` /
  `ANDROID_SDK_ROOT` / `ANDROID_HOME` so non-Android source sets can
  `compileOnly` the SDK jar.
- `gradle/libs.versions.toml` — version+toolchain SoT; per-pin rationale
  lives in catalog comments.
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
  underlying handle leaks. Conversely, **`close()` is idempotent per holder**
  (`AccessorAwareRad` has a private atomicfu `closed` guard, `final`
  override): a holder owns exactly one retain, so it must release at most
  once. Without the guard a `Closeable`-legal double-close (`use{}` +
  manual, defensive close) would decrement the *shared* refcount twice and
  prematurely free the resource still used by parent/siblings —
  `IOException` for file impls, a **JVM `SIGABRT` use-after-free** for
  direct/mmap `ByteBuffer`. Regression:
  `AbstractRandomAccessDataTest.doubleClosingSubsectionKeepsSharedResourceForParent`
  runs across every impl, so a new RAD that drops the guard can't pass CI.
- **Read-after-(last-)close is guarded at the resource-owner layer, not
  `AccessorAwareRad`.** Once the shared resource is freed (`isOpen` false),
  a read must fail with a clean `IOException`, never touch freed state.
  The guard CANNOT live in `AccessorAwareRad.read`/`readFrom`: per-impl
  perf overrides (`readByteAt0`, `read(ByteBuffer,position)`, `transferTo`)
  bypass it and hit `access`/`access.api` directly. It lives in
  `SharedDataAccessor.checkOpen()` (`internal` extension in `commonJvmMain`
  — the common `expect class IOException` has no message ctor, and the
  hazard is JVM-only). Only **ByteBuffer** (mmap/direct → native
  use-after-free **SIGABRT** after unmap) and **StreamFactory** (re-pool
  into an already-drained pool → silent stream **leak**) need it; the 4
  file/channel impls self-throw `ClosedChannelException`/`IOException` on a
  closed handle. **ByteArray is deliberately exempt** — no releasable
  resource, `close()` is a no-op, guarding the fastest impl's hot path
  buys no safety (`RandomAccessDataArrayTest` overrides the test to assert
  reads stay valid). **Concurrent close-during-read is safe by
  construction, not by a flaky timing test:** `isOpen` flips `false` in
  `SharedCloseable.releaseRetain()` *before* `onSharedClose()` runs
  (verified `SharedCloseable.kt` `releaseRetain`→`onSharedClose`; locked
  by `SharedCloseableTest.resourceReleaseObservesClosedState`), and reads
  share the resource monitor with the release — ByteBuffer's unmap is
  `synchronized(api)` (this made `readByteAt0` go `synchronized` — a
  deliberate hot-path cost for memory safety) and StreamFactory rechecks
  `isOpen` **under the pool lock** (also where the drain runs). So no
  interleaving lets a read touch freed state — and it's not just argued:
  `RadConcurrentCloseTest` latch-freezes a real in-flight read mid-close
  (no sleeps), RED without each guard (`unmapWaitsForInFlightRead` →
  close finishes mid-read; `streamRePooled…` → leaked stream). Regression:
  `AbstractRandomAccessDataTest.readingClosedHolderThrowsNotCrashes` runs
  across every impl and exercises **all four** resource-touching entry
  points (`readByteAt`, `readFrom`, `read(ByteBuffer)`, `transferTo`) —
  RED-bisected, so dropping the guard from any one override reds it
  (covering fewer paths would let a UAF leak through the rest). NB: this
  rejects reads after the *shared* resource is freed, NOT per-holder —
  reading a closed subsection whose parent is still open succeeds
  (by design).
- `SharedDataAccessor` owns the JVM resource and the only
  `read(bytes, position, offset, length)` primitive.
  `onSharedClose()` is `final`; release the API in
  `protected open fun releaseApi()` — the template always closes the
  `resources` array even if release throws. Direct `onSharedClose`
  overrides are compile-blocked (see `SharedDataAccessorReleaseApiTest`).
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
./updateBaseline                   # CANONICAL regen: verification metadata+yarnLock+apiDump+depGuardBaseline (CI=true RELEASE=true, no build/config cache, isolated .gradle/update-baseline home unless GRADLE_USER_HOME is set)
```

- Configuration cache is on with `problems=fail` and `max-problems=0`.
  Don't capture `Project` or build-script instances in task actions.
- Tests use `runTest(timeout = 9.seconds)`. Concurrency is exercised by
  `AbstractRandomAccessDataTest.testConcurrency` against a real temp file.
- Lincheck models must keep each `@Operation` to one API action. Do not
  combine mutation plus later observation (for example `close(); isOpen`)
  in one operation; expose the observation as a separate operation or
  deterministic regression test.
- CI runs across macOS/Windows/Ubuntu on JDK 21.
- For workflow / release / verification-metadata traps see
  [`.github/AGENTS.md`](.github/AGENTS.md).

## Adding a new RAD impl (canonical recipe)

1. JVM: `internal class FooRad(access, offset, size) :
   AccessorAwareRad<FooAccess>(access, offset, size)`. Inner
   `FooAccess(api, resources) : SharedDataAccessor(resources)` exposes
   `size: Long` + `read(bytes, position, offset, length)`.
   `getSubsection0` returns `FooRad(access, globalPosition, length)` —
   same `access`, parent retains.
2. Optional perf overrides: `read(ByteBuffer, position)` and
   `transferTo(WritableByteChannel, …)` (see `FileChannelRad`). **Any
   override that touches `access`/`access.api` for a releasable resource
   must call `checkOpen()` first** (read-after-close UAF/leak), unless the
   handle itself self-throws once closed (channels/`RandomAccessFile`).
   The cross-impl `readingClosedHolderThrowsNotCrashes` exercises these
   four entry points, so a missing guard reds CI.
3. Public factory in `FooRadAccessor.kt` with
   `@file:JvmName("Rad") @file:JvmMultifileClass` and `@JvmName("forFoo")`
   per overload. Mark `@Blocking` if the constructor opens resources.
4. Test: extend `AbstractRandomAccessDataTest(factory)`.
5. Run `./updateBaseline`. Inspect `api/jvm/fluxo-io-rad.api` diff
   before committing.
6. **Adding a new submodule** → also update
   `.github/workflows/build.yml` (called out in `settings.gradle.kts`).

## Conventions and traps (library / build)

- **Mmap limit**: only files < 2 GiB (`Int.MAX_VALUE`) work with
  `RadByteBufferAccessor(File|FileChannel|FileDescriptor|FileInputStream)`.
- **Don't recommend `RadAsyncFileChannelAccessor`** — deprecated, slow,
  direct-buffer OOM-prone. Same for `RadMemoryMappedAccessor` →
  `RadByteBufferAccessor`.
- **`fluxo.io.nio.Java8BufferCompat`** (`flipCompat`, `clearCompat`, …)
  must be used in JVM source instead of raw `Buffer.flip()` to dodge the
  JDK 9 covariant-override `NoSuchMethodError`. It's intentionally kept
  for source compatibility — see `ROADMAP.md` "triggered modernization".
- **Coroutines is `compileOnly`** in lib code. Consumers using suspend
  APIs must depend on `kotlinx-coroutines-core`.
- **AGP 9 Android-KMP trap:** `androidMain` must explicitly depend on
  `commonJvmMain` here, otherwise Android compilation cannot see JVM
  actuals. Consumer keep rules are not published by default; use
  `android.optimization.consumerKeepRules { publish = true; file(...) }`
  and verify `bundleAndroidMainAar` contains `proguard.txt`. There is **no
  `:fluxo-io-rad:lint` task** under AGP 9 Android-KMP here; use the
  discovered lint packaging tasks (`compileLint`,
  `androidCompileLintChecks`, `bundleAndroidMainLocalLintAar`) plus
  `check`. There is **no `updateLintBaseline` task** either — keep
  `updateBaseline` to executable tasks only; verify available lint tasks
  with `./gradlew tasks --all --quiet | rg -i 'lint|baseline'` before
  adding one.
- **Never re-add the well-known Linux-only Gradle build service** (`jit` +
  `pack`). It does not support KMP (`jitpack#3853`); this lib has
  Apple/Native targets, so its build emits a broken metadata-incomplete
  artifact. Removal + the repository ban are correct — keep both. (See
  also `.github/AGENTS.md` "Release publication".)
- Build floors are deliberate: `javaLangTarget=17`, `androidMinSdk=21`,
  `kotlinLangVersion=2.1`. Do not raise Kotlin language without fresh
  fluxo-kmp-conf/Detekt compatibility evidence.
- `kotlinx-io`, Okio, and JMH are catalogue-reserved; don't wire them
  without an actual feature need.
- JSR305 stays at `3.0.2`; upstream has no newer release.
- `kotlin.concurrent.atomics` is still experimental; keep AtomicFU.
- Keep the explicit `apiValidation { klib { enabled = true } }` in
  `:fluxo-io-rad`; fluxo-kmp-conf `klibValidationEnabled = true` alone did
  not enable BCV 0.18 KLIB tasks here.
- Current warning debt is upstream/plugin-shaped: Detekt calls deprecated
  Gradle `ReportingExtension.file`, Kotlin/JS resolves `*NpmAggregated`
  during configuration, and `Java8BufferCompat` keeps Kotlin internal
  `InlineOnly` for source-compatible Java 8 buffer wrappers.
- Generated/build outputs (`build/`, `.gradle/`, `.kotlin/`) — never edit;
  never commit. `.kotlin-js-store/yarn.lock` is tracked baseline output
  regenerated via `./gradlew kotlinUpgradeYarnLock` (run by
  `./updateBaseline`); do not edit other `.kotlin-js-store/` files.
- Project flags in `gradle.properties` (`MAX_DEBUG`, `COMPOSE_METRICS`,
  `USE_KOTLIN_DEBUG`, `LOAD_KMM_CODE_COMPLETION`) are read by
  `fluxo-kmp-conf`; semantics live in that plugin.

## Process

- **Conventional commits required** (strict type set: `CONTRIBUTING.md`).
  Keep history flat (`--ff-only`); FF merges are triggered by an exact
  `/ff` or `/fast-forward` PR comment after the workflow verifies the
  commenter has write/maintain/admin permission. See `.github/AGENTS.md`
  for the `GITHUB_TOKEN` recursion-guard side-effects.
- **Don't strand local-only branches.** Push WIP to `origin` (bus-factor;
  a single local copy is what stranded the toolchain modernization).
  Divergence happens — expect to rebase onto `origin/dev` before an
  `--ff-only` land.

## Surprises rule (READ THIS)

**If anything in this repo surprises you — a build flag, a hidden opt-in,
a deprecated alias still wired up, an ABI-dump diff that looks innocent
but isn't — TELL THE USER and append a brief note here in `AGENTS.md`
(library/architecture) or `.github/AGENTS.md` (workflow/release/
verification) so the next agent doesn't have to relearn it.**
Memory > recovery.

## Pointers

- User-facing usage / platform matrix → `README.md`
- Commit + PR rules → `CONTRIBUTING.md`
- Release flow → `RELEASING.md`
- Roadmap / known gaps → `ROADMAP.md`
- Build behaviour comes from `io.github.fluxo-kt.fluxo-kmp-conf`
  (`fkcSetupRaw`/`fkcSetupMultiplatform`); when something Gradle-side is
  mysterious, read that plugin's source — not this repo.
