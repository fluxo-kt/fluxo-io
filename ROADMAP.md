# Roadmap, ideas, and notes

### Research roadmap

<details>
  <summary>Show</summary>

* Set up code coverage.
* Port benchmarks.
* Set up multiplatform benchmarks.
* Kotlin/Native implementations.
* Node.JS implementations.
* Wasm.Wasi implementations.
</details>

### Triggered modernization

<details>
  <summary>Show</summary>

* Migrate to Kotlin's built-in ABI validation only after fluxo-kmp-conf supports
  it for this project shape.
* Consider stdlib atomics only after `ExperimentalAtomicApi` is no longer
  required for the needed operations.
* Consider AGP 9.2+ only after fluxo-kmp-conf publishes compatibility evidence
  for it.
* Consider Kotlin 2.4 only after GA and fluxo-kmp-conf matrix support.
* Revisit `Java8BufferCompat` removal only as an explicit source-API decision.
</details>
