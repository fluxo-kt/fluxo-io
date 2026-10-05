# Roadmap, ideas, and notes

### Ports to other languages

The contract in [`docs/spec/random-access-data.md`](docs/spec/random-access-data.md)
is written so that ports can pass the same case table.

* Native TypeScript/JavaScript, across runtimes (Node, Bun, Deno, browsers).
* Rust, including Rust compiled to WASM/WASI.

### Research roadmap

<details>
  <summary>Show</summary>

* Multiplatform benchmarks (random small, sequential large, concurrent reads),
  to rank each platform's file sources and decide mmap vs positional reads.
* Memory-mapped reads of files over 2 GiB (several mappings of at most
  `Int.MAX_VALUE` bytes each).
* Browser `Blob` source on Wasm-JS (needs JS interop for `Blob.slice` and
  `arrayBuffer()` without adding a dependency to the core).
* JVM `AsynchronousFileChannel`-backed `AsyncRandomAccessData`, if it ever beats
  `open(path).asAsync(Dispatchers.IO)` (on Unix the JDK serves it from a thread
  pool too).
</details>

### Triggered modernization

<details>
  <summary>Show</summary>

* Migrate to Kotlin's built-in ABI validation only after fluxo-kmp-conf supports
  it for this project shape.
* Consider stdlib atomics only after `ExperimentalAtomicApi` is no longer
  required for the needed operations.
* Revisit `Java8BufferCompat` removal only as an explicit source-API decision.
</details>
