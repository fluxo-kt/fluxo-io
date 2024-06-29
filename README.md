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
> **API isn’t completely stable yet!**<br>
> **Benchmarks and complete test coverage are coming.**

Library provides cross-platform [`RandomAccessData`][RandomAccessData]
abstraction for effective read-only random access to binary data.
Both suspend and blocking APIs are provided.
Different platform-specific implementations are provided.

> [!TIP]
> For JVM and Android, compatibility with `ByteBuffer` reads and writes is provided.<br>
> An `InputStream` view is also provided for compatibility with existing APIs.


|                                        API | Platform     | Supported for |
|-------------------------------------------:|:-------------|:--------------|
|                                [ByteArray] | All          | All           |
|                               [ByteBuffer] | JVM, Android | JVM, Android  |
| [ByteBufferMmap]<br>_(memory-mapped file)_ | JVM, Android | JVM, Android  |
|                              [FileChannel] | JVM, Android | JVM, Android  |
|                         [RandomAccessFile] | JVM, Android | JVM, Android  |
|                      [SeekableByteChannel] | JVM, Android | JVM, Android  |
|                [() -> InputStream] Factory | JVM, Android | JVM, Android  |
|                  [() -> DataInput] Factory | JVM, Android | JVM, Android  |
|        [() -> ReadableByteChannel] Factory | JVM, Android | JVM, Android  |
|                  [AsynchronousFileChannel] | JVM, Android | JVM, Android  |


> [!IMPORTANT]
> For using [AsynchronousFileChannel], you need to add Kotlin Coroutines dependency to your project.

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
[AsynchronousFileChannel]: fluxo-io-rad/src/commonJvmMain/kotlin/fluxo/io/rad/AsyncFileChannelRadAccessor.kt#L32

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


### Versioning

Uses [SemVer](http://semver.org/) for versioning. <br>
For the versions available, see the [tags on this repository](../../tags).


### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.


[badge-kotlin]: http://img.shields.io/badge/Kotlin-2.0.0-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B
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
