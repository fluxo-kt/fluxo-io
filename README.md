# Fluxo IO

![Stability: Alpha](https://kotl.in/badges/alpha.svg)
[![Kotlin Version][badge-kotlin]][badge-kotlin-link]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

![Kotlin Multiplatform][badge-kmp]
![JVM][badge-jvm] ![badge][badge-android] ![badge][badge-android-native]
![badge][badge-ios] ![badge][badge-watchos] ![badge][badge-tvos] ![badge][badge-mac]
![badge][badge-win] ![badge][badge-linux]
![badge][badge-js] ![badge][badge-wasm]

---

> [!CAUTION]
> **⚠ Work-In-Progress**.
> **API may be not completely stable yet!**<br>
> **Benchmarks and complete test coverage are coming.**


### How to use

```kotlin
// in the `build.gradle.kts` of the target module.
plugins {
  kotlin("multiplatform") version "2.2.21"
}
dependencies {
  implementation("io.github.fluxo-kt:fluxo-io:0.1.0")
}
```

`0.1.0` is the latest published version. From `0.2.0` the coordinate becomes
`io.github.fluxo-kt:fluxo-io-rad`.

Use only versions that exist in Maven Central or the Central Portal snapshot
repository. This module is alpha; do not assume unpublished coordinates are
available.


Library provides cross-platform [`RandomAccessData`][RandomAccessData]
abstraction for effective read-only random access to binary data: positional
reads, no-copy `slice`s, and shared handles that free the resource when the
last one closes.

```kotlin
RandomAccessData.open("data.bin").use { rad ->
  val header = ByteArray(16)
  rad.readFully(header, position = 0)
  val body = rad.slice(16) // a view to the end: no copy, nothing to close
}
```

Reads block the calling thread. To suspend instead, wrap any instance:
`rad.asAsync(Dispatchers.IO)`. Sources that are async by nature
(`AsyncRandomAccessData.open(path)` on Node, `open(blob)` in a browser) never
block at all.

| Platform           | `RandomAccessData.open(path)` reads with  | Also                                                  |
|:-------------------|:------------------------------------------|:------------------------------------------------------|
| JVM                | `FileChannel` positional reads            | `open(File)`, `open(Path)` (also from Java)           |
| Android            | `FileChannel` positional reads            | `open(ParcelFileDescriptor)`, `open(AssetFileDescriptor)` |
| Apple, Linux, Android Native | `pread`                         |                                                       |
| Windows (mingw)    | `ReadFile` at an offset                   |                                                       |
| JS, Wasm-JS        | Node `fs` (Node, Bun, Deno)               | `AsyncRandomAccessData.open(path)`; JS: `open(blob)`  |
| Wasm-WASI          | `fd_pread` under a preopened directory    |                                                       |
| All                | —                                         | `RadByteArrayAccessor(bytes)`                         |

A browser has no file system, so `open(path)` throws there.

Which one when (JVM/Android):

|                                        API | Use when                                                         |
|-------------------------------------------:|:-----------------------------------------------------------------|
|                 `RandomAccessData.open(…)` | Default for files: safe if the file shrinks, any size            |
| [ByteBufferMmap]<br>_(memory-mapped file)_ | Hot random reads of a file < 2 GiB that nobody truncates         |
|                               [ByteBuffer] | Data already in a `ByteBuffer`                                   |
|                                [ByteArray] | Data already in memory                                           |
|                              [FileChannel] | An open channel or descriptor you hand over                      |
|                         [RandomAccessFile] | An open `RandomAccessFile` you hand over                         |
|                      [SeekableByteChannel] | Any other seekable channel (e.g. zip file systems)               |
|                [() -> InputStream] Factory | Only streams exist; each read may reopen and skip                |
|                  [() -> DataInput] Factory | Same, for `DataInput`                                            |
|        [() -> ReadableByteChannel] Factory | Same, for channels                                               |

> [!TIP]
> On JVM and Android, `ByteBuffer` reads, `transferTo(channel)` and an
> `InputStream` view are provided for existing APIs.

Adapter modules (same version; the core has no dependencies):

- `io.github.fluxo-kt:fluxo-io-rad-okio`: `RandomAccessData.open(FileHandle)`
  and `RandomAccessData.source(position)`.
- `io.github.fluxo-kt:fluxo-io-rad-kotlinx-io`:
  `RandomAccessData.asRawSource(position)`.

[RandomAccessData]: fluxo-io-rad/src/commonMain/kotlin/fluxo/io/rad/RandomAccessData.common.kt#L29

[ByteArray]: fluxo-io-rad/src/commonMain/kotlin/fluxo/io/rad/RadByteArrayAccessor.kt#L21
[ByteBuffer]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/ByteBufferRadAccessor.kt#L30
[ByteBufferMmap]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/ByteBufferRadAccessor.kt#L85
[FileChannel]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/FileChannelRadAccessor.kt#L28
[RandomAccessFile]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/RandomAccessFileRadAccessor.kt#L29
[SeekableByteChannel]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/SeekableByteChannelRadAccessor.kt#L34
[() -> InputStream]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/StreamFactoryRadAccessor.kt#L62
[() -> DataInput]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/StreamFactoryRadAccessor.kt#L92
[() -> ReadableByteChannel]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/StreamFactoryRadAccessor.kt#L122

<details>
  <summary>History notes</summary>

_The first steps of the implementation were dated 2021-03-31 (2d87ec044f5801cd3ad8cc31ac380b17fa31d44a)._<br>
_Open-source since 2024-06-16._
</details>


### Related or alternative projects

* [Okio](https://github.com/square/okio)
* [Kotlinx IO](https://github.com/Kotlin/kotlinx-io)
* [Ktor IO](https://github.com/ktorio/ktor/tree/main/ktor-io)
* [DitchOoM Buffer](https://github.com/DitchOoM/buffer)
* [Karma Krafts kMMIO](https://git.karmakrafts.dev/kk/kmmio) (Lightweight KMP MMIO)


### Versioning

Uses [SemVer](http://semver.org/) for versioning. <br>
For the versions available, see the [tags on this repository](../../tags).


### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.


[badge-kotlin]: http://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B
[badge-kotlin-link]: https://github.com/JetBrains/kotlin/releases

[badge-kmp]: http://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=7F52FF&labelColor=2B2B2B
[badge-jvm]: http://img.shields.io/badge/-JVM-530E0E?logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAOCAMAAAAolt3jAAAAh1BMVEUAAABTgqFTgJ9Yg6VTgqFSg6FUgaFTgZ9TgqFSg6FSgqJTgp/ncABVgqRVgKpTg6HnbwDnbwBTgqDnbwDocADocADnbwDnbQBOgJ1Vg6T/ZgDnbwDnbwDnbwBTgqHnbwBTgqBTgqJTgaDnbgDnbwDnbgBVgqFRgqNRgKLpbQDpcQDjcQDtbQD42oiEAAAALXRSTlMAQyEPSWlUJqlwXllQKgaijIRmYFY3Lx8aEwXz5dLEta+ZlHZsQTkvLCMiEg6oPAWiAAAAfklEQVQI102LVxKDMBBDtbsugDGdUNJ7vf/5MjAJY33pjfTwz2eFMOk1pLrAuMBzX7fN8m63XXkJvAKbJjDPXbp+/3rmBa+xBIQY4EhXxiSKLLCbZn1n9qQy0WrC3pkqUYx+VgebH/MomhecDsqyyMAPopsA4p2O4zhxhsh+ASqXBd13PdMrAAAAAElFTkSuQmCC
[badge-android]: https://img.shields.io/badge/-Android-0E3B1A?logo=android&logoColor=3DDC84
[badge-android-native]: https://img.shields.io/badge/-Android%20Native-0A7E07?logo=androidstudio&logoColor=3DDC84&labelColor=2B2B2B

[badge-ios]: http://img.shields.io/badge/-iOS-E5E5EA?logo=apple&logoColor=64647D
[badge-mac]: http://img.shields.io/badge/-macOS-F4F4F4?logo=apple&logoColor=6D6D88
[badge-watchos]: http://img.shields.io/badge/-watchOS-C0C0C0?logo=apple&logoColor=4C4C61
[badge-tvos]: http://img.shields.io/badge/-tvOS-808080?logo=apple&logoColor=23232E

[badge-win]: http://img.shields.io/badge/-Windows-00ADEF?logo=windows&logoColor=FCFDFD
[badge-linux]: http://img.shields.io/badge/-Linux-6E1F7C?logo=linux&logoColor=FFF6DB
[badge-js]: http://img.shields.io/badge/-JavaScript-F8DB5D?logo=javascript&logoColor=312C02
[badge-wasm]: http://img.shields.io/badge/-WASM.JS-654FF0?logo=webassembly&logoColor=FCFDFD
[badge-wasi]: http://img.shields.io/badge/-WASM.WASI-F72585?logo=webassembly&logoColor=FCFDFD
