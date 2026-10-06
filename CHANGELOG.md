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
- `AsyncRandomAccessData` and `RandomAccessData.asAsync(blockingContext)`: reads
  that suspend without blocking the caller, e.g. `rad.asAsync(Dispatchers.IO)`.
  Needs only the Kotlin stdlib; closing the adapter closes the wrapped handle.
- `RandomAccessData.open(path)` on every target, reading real files with
  positional reads: `FileChannel` on JVM/Android (also `open(File)` and
  `open(Path)`, callable from Java), `pread` on Apple/Linux/Android Native,
  `ReadFile` on Windows, Node's `fs` on JS and Wasm-JS (Node, Bun, Deno), and
  `fd_pread` on Wasm-WASI. A browser has no file system: `open(path)` throws.
- Android: `RandomAccessData.open(ParcelFileDescriptor)` and
  `open(AssetFileDescriptor)`; a pipe or socket is read into memory once.
- `AsyncRandomAccessData.open(path)` on JS and Wasm-JS (Node's async `fs`, the
  event loop never blocks) and `AsyncRandomAccessData.open(Blob)` on JS (browser
  `Blob`/`File`).
- Wasm-WASI target.
- New modules: `fluxo-io-rad-okio` (`RandomAccessData.open(FileHandle)`,
  `RandomAccessData.source()`) and `fluxo-io-rad-kotlinx-io`
  (`RandomAccessData.asRawSource()`). The core gains no dependency.

### Changed

- **A closed handle never reads**: every read of a closed `RandomAccessData`, or
  of a slice taken from it, throws `IOException`, even while another handle keeps
  the data open. Before, such reads succeeded until the last handle closed, and
  `ByteArray`-backed instances kept reading after `close()`.
- `readAsync`/`readFullyAsync` are deprecated (error level): they suspended but
  blocked the calling thread. Use `asAsync(dispatcher).read(...)`.
- `subsection(position, length)` is deprecated (error level), replaced by
  `slice(position, length).share()`; it will be removed in the next release.
  Most callers that never closed their subsections want plain `slice(...)`.
- **Artifact coordinate renamed** to `io.github.fluxo-kt:fluxo-io-rad` (was
  `fluxo-io` / `fluxo-io-jvm` / platform klibs in 0.1.0). Migrate the
  dependency coord; the old artifacts are no longer published.
- Toolchain: built with Kotlin 2.4.20 at language 2.2, AGP 9, JVM target 17,
  Android minSdk 21. Consumers need Kotlin 2.1+ on the JVM and Kotlin 2.4+ on
  Native/JS/Wasm (a klib is readable only by its compiler's minor or newer).
- Publishing via Maven Central Portal (vanniktech); Sonatype S01/OSSRH retired.
- `Rad.forByteBuffer(buffer)` no longer frees the caller's buffer on close (using
  it afterwards could crash the JVM); pass `{ buffer.releaseCompat() }` in
  `resources` to keep that. Its data ends at the buffer's limit, not capacity.
- Implementing `RandomAccessData` outside this library now needs
  `@OptIn(InternalFluxoIoApi::class)` (an error; it was a warning that could not
  be silenced), and implementations must provide `slice` and `share`.

### Fixed

- Reads and transfers no longer spin forever when the source returns 0 bytes, the
  file shrinks under `transferTo`, or a non-blocking target channel accepts nothing:
  they now throw `IOException` naming the cause.
- `transferTo` on implementations without a native `ByteBuffer` read (random-access
  file, stream factory) no longer allocates a temporary array per chunk.
- Read-after-(last-)close on direct/mmap `ByteBuffer` (was: JVM `SIGABRT`)
  and silent stream-handle leak in `StreamFactoryRad`. Reads on a freed
  shared resource now throw `IOException`; a close during a read defers the
  release until that read ends, and `close()` never waits for readers.
- `close()` is idempotent per handle: a `Closeable`-legal double close no
  longer frees the shared resource still used by other handles.
- `StreamFactoryRad` ghost-read race + DoS-on-close: a slow user factory no
  longer blocks `close()`, and no stream leaks past the pool drain.
- Stream, `DataInput` and byte-channel factory sources with a non-zero offset
  failed every read past `size - offset`.
- `Rad.forX(File)` factories leaked the opened file when the offset or size was
  invalid.
- `read(ByteBuffer, position)` left the caller's buffer limit lowered when the
  read threw.
- Slicing on an interrupted thread closed a `FileChannel` for every handle (each
  slice queried the file size); the size is now read once at open.
- `SharedDataAccessor.onSharedClose` is `final`; release goes in an overridable
  `releaseApi()` slot. A release-throw never skips closing the `resources`
  array (compile-blocked re-introduction of the bug class).


## [0.1.0] - 2024-11-26

🌱 _Initial Maven Central release._


## Notes

[0.1.0]: https://github.com/fluxo-kt/fluxo-io/releases/tag/v0.1.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme

