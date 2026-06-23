# Changelog [^1]


## Unreleased

[//]: # (Removed, Added, Changed, Fixed, Updated)


## [0.1.1] - 2026-06-23

### Changed

- **Artifact coordinate renamed** to `io.github.fluxo-kt:fluxo-io-rad` (was
  `fluxo-io` / `fluxo-io-jvm` / platform klibs in 0.1.0). Migrate the
  dependency coord; the old artifacts are no longer published.
- Toolchain: Kotlin 2.2 (language 2.1), AGP 9, JVM target 17, Android minSdk 21.
- Publishing via Maven Central Portal (vanniktech); Sonatype S01/OSSRH retired.

### Fixed

- Read-after-(last-)close on direct/mmap `ByteBuffer` (was: JVM `SIGABRT`)
  and silent stream-handle leak in `StreamFactoryRad`. Reads on a freed
  shared resource now throw `IOException` via `SharedDataAccessor.checkOpen()`.
- `AccessorAwareRad.close()` is idempotent per holder — a `Closeable`-legal
  double-close no longer prematurely frees the shared resource still used
  by parent or siblings.
- `StreamFactoryRad` ghost-read race + DoS-on-close: factory now opens outside
  the pool monitor (three-phase); pool re-checks `isOpen` under the lock.
- `SharedDataAccessor.onSharedClose` is `final`; release goes in an overridable
  `releaseApi()` slot. A release-throw never skips closing the `resources`
  array (compile-blocked re-introduction of the bug class).


## [0.1.0] - 2024-11-26

🌱 _Initial Maven Central release._


## Notes

[0.1.1]: https://github.com/fluxo-kt/fluxo-io/releases/tag/v0.1.1
[0.1.0]: https://github.com/fluxo-kt/fluxo-io/releases/tag/v0.1.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme

