# Changelog [^1]


## Unreleased

[//]: # (Removed, Added, Changed, Fixed, Updated)

Next version: 0.2.0. Version 0.1.1 was tagged but never published to Maven
Central; its changes ship here.

### Added

- `RandomAccessData.slice(position, length)`: a no-copy range view that needs no
  close (it cannot leak) and reads only while its handle is open.
- `RandomAccessData.share()`: another handle that keeps the data open on its
  own; close it once.

### Changed

- **A closed handle never reads**: every read of a closed `RandomAccessData`, or
  of a slice taken from it, throws `IOException`, even while another handle keeps
  the data open. Before, such reads succeeded until the last handle closed, and
  `ByteArray`-backed instances kept reading after `close()`.
- `subsection(position, length)` is deprecated (error level), replaced by
  `slice(position, length).share()`; it will be removed in the next release.
  Most callers that never closed their subsections want plain `slice(...)`.
- **Artifact coordinate renamed** to `io.github.fluxo-kt:fluxo-io-rad` (was
  `fluxo-io` / `fluxo-io-jvm` / platform klibs in 0.1.0). Migrate the
  dependency coord; the old artifacts are no longer published.
- Toolchain: Kotlin 2.2 (language 2.1), AGP 9, JVM target 17, Android minSdk 21.
- Publishing via Maven Central Portal (vanniktech); Sonatype S01/OSSRH retired.

### Fixed

- Read-after-(last-)close on direct/mmap `ByteBuffer` (was: JVM `SIGABRT`)
  and silent stream-handle leak in `StreamFactoryRad`. Reads on a freed
  shared resource now throw `IOException`; a close during a read defers the
  release until that read ends, and `close()` never waits for readers.
- `close()` is idempotent per handle: a `Closeable`-legal double close no
  longer frees the shared resource still used by other handles.
- `StreamFactoryRad` ghost-read race + DoS-on-close: a slow user factory no
  longer blocks `close()`, and no stream leaks past the pool drain.
- `SharedDataAccessor.onSharedClose` is `final`; release goes in an overridable
  `releaseApi()` slot. A release-throw never skips closing the `resources`
  array (compile-blocked re-introduction of the bug class).


## [0.1.0] - 2024-11-26

🌱 _Initial Maven Central release._


## Notes

[0.1.0]: https://github.com/fluxo-kt/fluxo-io/releases/tag/v0.1.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme

