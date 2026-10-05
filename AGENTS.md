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
- **Tests use real temp files, not mocks.** Behaviour every impl must
  share lives in ONE table, `RadContract` (main code of the unpublished
  `:conformance-test` module, so adapter modules can run it too), run on every
  target (`ByteArrayRadContractTest`) and against every JVM impl over a
  temp file (`AbstractRandomAccessDataTest.contract`). A new common rule
  goes into that table, never into a per-platform copy; platform-only API
  (streams, `ByteBuffer`, `transferTo`, `readByteAt`, concurrency) is
  tested in its platform test source set. `docs/spec/random-access-data.md`
  states the same rules for future ports; change both together.
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
  - `commonMain` — `expect interface RandomAccessData` + `expect fun byteArrayRad`
    (each platform's `ByteArrayRad` is a plain internal class behind it).
  - `commonJvmMain` — JVM+Android impl set (`ByteBuffer`, mmap,
    `FileChannel`, `RandomAccessFile`, `SeekableByteChannel`, stream
    factories, async).
  - `nonJvmMain` — JS / Native / Wasm-JS / Wasm-WASI. File impls per family:
    `nixMain` (pread), `mingwMain` (ReadFile), `webMain` (Node fs; JS and
    Wasm-JS differ only in the byte copy), `wasmWasiMain` (fd_pread). Each
    exposes `openPlatformFile(path)`. Node externals take `Uint8Array` only:
    Deno's `fs` silently ignores an `Int8Array` (Kotlin/JS `ByteArray`).
  - Wasm-WASI is declared explicitly (`wasmWasi { … }`): fkc adds it only
    together with its own wasmJs. Its test task patches KGP's driver to
    preopen `/tmp` (KT-65179); a WASI module reaches no file otherwise.
- `:conformance-test` — unpublished; `RadContract` for every module's tests.
  No Android target: no Android test consumes it, and fkc 0.15.1's Android
  Detekt task fails on a KMP-Android project dependency. The `-test` name
  makes fkc skip Dependency Guard (it bans kotlin-test on main classpaths).
- `:fluxo-io-rad-okio`, `:fluxo-io-rad-kotlinx-io` — published adapters, so the
  core stays dependency-free. They build sources with `fluxo.io.internal.radOf`
  (public, but `@InternalFluxoIoApi` at ERROR level and absent from API dumps:
  the adapters ship in lockstep with the core, consumers must not call it).
  Inside any `SharedCloseable` subclass, a name `close` resolves to the member
  function, never to a function-typed property: name such properties otherwise.
- Root `build.gradle.kts` — umbrella via `fkcSetupRaw {…}`, Kover
  aggregation. `commonJvmMain` compiles against the Android SDK jar taken
  from AGP's `sdkComponents.bootClasspath` (`fluxo-io-rad/build.gradle.kts`);
  never build an SDK path by hand, SDK directory names vary (`android-37.0`).
- `gradle/libs.versions.toml` — version+toolchain SoT; per-pin rationale
  lives in catalog comments.
- `config/{detekt.yml,lint.xml}`; `.editorconfig` (ktlint_official,
  line=100, indent=4 for `.kt`/`.kts`).

## Core architecture

- `RandomAccessData` is `expect interface` annotated
  `@SubclassOptInRequired(InternalFluxoIoApi::class)`. JVM `actual` adds
  `Closeable`, `ByteBuffer` reads, `asInputStream()`, `transferTo`,
  `readByteAt`.
- **Expose a platform implementation to common code through an
  `expect fun` factory, never an `expect class` extending `BasicRad`.** The
  common-metadata compiler demands every abstract `BasicRad` hook in the
  `expect class` too, test compilations never run that check, and the
  break shows only in `build`/publishing (`compileCommonMainKotlinMetadata`).
- **The obvious entry point is `RandomAccessData.open(…)` in Kotlin and Java**
  (maintainer ruling; no `Rad.open`). Overloads live on the interface's
  companion: `@JvmStatic` members of the JVM `actual companion`, Kotlin
  extensions on the companion for Android-only types, and
  `AsyncRandomAccessData.Companion` extensions for natively async sources.
  The explicit `Rad.forX` factories stay.
- **Handles and slices (`fluxo.io.internal.RadHandle`).** A factory or
  `share()` returns a *handle*: it holds one ownership of the
  `SharedDataAccessor` (`SharedCloseable` refcount) and must be closed
  once; the resource is released when the last handle closes. `slice()`
  returns a *view*: owns nothing, close is a no-op, so views cannot leak.
  **A closed handle never reads, nor do its slices**, even while another
  handle keeps the data open: `ensureOpen()` (one volatile read) runs first
  in every `final` public read of `BasicRad`, and implementations override
  only the protected `…0` hooks, so no fast path can skip it. Why strict:
  otherwise a read-after-close succeeds or fails depending on unrelated
  handles, hiding the caller's bug. Applies to ByteArray too (one rule for
  every impl and future ports). `close()` is idempotent per handle
  (a double close must not give back the ownership twice, which would free
  data other handles use: `IOException` for files, **JVM `SIGABRT`** for
  mmap/direct `ByteBuffer`). `subsection()` is deprecated as
  `slice(p, l).share()`. Regressions run on every impl:
  `doubleClosingShareKeepsDataForParent`,
  `closedHandleRejectsReadsWhileOtherHandleIsOpen`,
  `readingClosedHolderThrowsNotCrashes` (all public read paths; only the
  ByteArray run can catch a missing `ensureOpen`, since resource-backed
  impls still fail via the lease).
- **Every access to a releasable resource runs inside
  `SharedCloseable.withLease { … }`**, the only read-after-close guard.
  A lease is one atomic add, granted only while an owner exists; once the
  last owner closes, new reads fail with `IOException` and in-flight ones
  finish. The release (unmap, pool drain, handle close) runs when the last
  owner AND the last lease are gone, so it can never free state under a
  read (mmap/direct `ByteBuffer` → native **SIGABRT**; StreamFactory →
  leaked stream), and `close()` never waits for readers. So no monitor is
  needed for safety: ByteBuffer reads use absolute `get`/`duplicate()` and
  run in parallel. Keep the lease inside the accessor (`api` is `private`
  there), never in `AccessorAwareRad`: per-impl perf overrides
  (`readByteAt0`, `read(ByteBuffer, position)`, `transferTo`) bypass the
  base `read`. ByteArray needs no lease (nothing to release). Sequential
  read-after-close is rejected earlier, at the handle; the lease matters
  when a close races an in-flight read, which `RadConcurrentCloseTest`
  latch-freezes (no sleeps) and which is RED when the lease is removed.
- `SharedDataAccessor` owns the JVM resource and the only
  `read(bytes, position, offset, length)` primitive.
  `onSharedClose()` is `final`; release the API in
  `protected open fun releaseApi()` — the template always closes the
  `resources` array even if release throws. Direct `onSharedClose`
  overrides are compile-blocked (see `SharedDataAccessorReleaseApiTest`).
  `AccessorAwareRad<A>` (common, every platform) wraps it with offset/size + bounds checks
  (`fluxo.io.util.IoUtil`). `BasicRad` (expect/actual) carries the JVM
  common impl: `readByteAt`, `transferTo`, `read(ByteBuffer, position)`,
  suspend wrappers, `Java8BufferCompat`-based buffer handling.
- **`Thread.interrupt()` cooperation**: `BasicRad.readFully` and
  `readByteAt` check `Thread.interrupted()` and throw
  `IOException("Thread interrupted")`. Bare `read()` does not.
- **No suspend function may block its caller.** Async reads go through
  `AsyncRandomAccessData`; `rad.asAsync(dispatcher)` runs each blocking
  call as ONE dispatch on that dispatcher using only stdlib intrinsics
  (`startCoroutine`), so kotlinx-coroutines stays `compileOnly`. It rejects
  a context without a dispatcher and takes over the handle it wraps
  (`rad.share().asAsync(…)` keeps `rad` usable). The old
  `readAsync`/`readFullyAsync` blocked the caller and are ERROR-deprecated
  for one release; never call them from library code (ERROR level breaks
  our own compile) — use the `…0` hooks.
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
./gradlew build                    # full verify, as CI: check (BCV apiCheck, kover, detekt, AGP lint wiring, depGuard, tests) + assemble
                                   # `check` alone never compiles common metadata (compileCommonMainKotlinMetadata), where expect/actual breaks show
./gradlew :fluxo-io-rad:jvmTest    # JVM unit tests
./gradlew :fluxo-io-rad:detektAll  # fast lint verdict (plain `detekt` is NO-SOURCE here: checks zero files, exits 0)
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

1. Any platform: `internal class FooRad(access, offset, size) :
   AccessorAwareRad<FooAccess>(access, offset, size)`. Inner
   `FooAccess(api, resources) : SharedDataAccessor(resources)` exposes
   `size: Long` + `read(bytes, position, offset, length)`.
   `view0(access, globalPosition, length, owner)` returns
   `FooRad(access, globalPosition, length, owner)`: same `access`; the
   handle lifetime is inherited from `RadHandle`, never reimplemented.
2. Keep `api` `private` in `FooAccess`; every `FooAccess` method that
   touches it runs inside `withLease { … }` (read-after-close UAF/leak).
   Optional perf overrides (`read(ByteBuffer, position)`,
   `transferTo(WritableByteChannel, …)`, see `FileChannelRad`) call such
   leased `FooAccess` methods, never the resource directly. The cross-impl
   `readingClosedHolderThrowsNotCrashes` exercises these four entry points,
   so a missing lease reds CI.
3. Public factory in `FooRadAccessor.kt` with
   `@file:JvmName("Rad") @file:JvmMultifileClass` and `@JvmName("forFoo")`
   per overload. Mark `@Blocking` if the constructor opens resources.
4. Test: extend `AbstractRandomAccessDataTest(factory)` (JVM) or call
   `RadContract.verify { bytes -> … }` from that platform's test set; the
   factory must open the bytes it is given.
5. Run `./updateBaseline`. Inspect `api/jvm/fluxo-io-rad.api` diff
   before committing.
6. **Adding a new submodule** needs no workflow edit: CI runs root tasks
   (`build`, `allTests`, `apiCheck`) that cover every module.

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
  `kotlinLangVersion=2.2` (maintainer ruling: the library follows the newest
  compiler that still accepts this language version). Native/JS/Wasm consumers
  need at least the library compiler's minor version (a klib built by 2.4.20
  fails on 2.3.21, works on 2.4.0; `scripts/consumer-check.sh` proves it). JVM
  consumers need only one minor below the language version (2.1.21 compiles and
  runs against language 2.2).
  Detekt supports at most Kotlin language 2.1, so fluxo-kmp-conf clamps
  Detekt's `--language-version` and logs it; that log is expected.
- Okio and kotlinx-io are used only by their adapter modules
  (`:fluxo-io-rad-okio`, `:fluxo-io-rad-kotlinx-io`); the core never depends
  on them. JMH is catalogue-reserved; don't wire it without a feature need.
- JSR305 stays at `3.0.2`; upstream has no newer release.
- `kotlin.concurrent.atomics` is still experimental; keep AtomicFU.
- Keep the explicit `apiValidation { klib { enabled = true } }` in
  `:fluxo-io-rad`; fluxo-kmp-conf `klibValidationEnabled = true` alone did
  not enable BCV 0.18 KLIB tasks here.
- Current warning debt is upstream/plugin-shaped: Detekt calls deprecated
  Gradle `ReportingExtension.file`, Dokka 2.2 and BCV call
  `Configuration.setVisible`, gradle-doctor calls `Project.getProperties`
  (find owners with `-Dorg.gradle.deprecation.trace=true`), Kotlin/JS resolves `*NpmAggregated`
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
- **Before judging an API break, check what Maven Central actually serves**
  under every coordinate this library has used (`fluxo-io` up to 0.1.0,
  `fluxo-io-rad` after). Git tags list candidate releases only.

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
