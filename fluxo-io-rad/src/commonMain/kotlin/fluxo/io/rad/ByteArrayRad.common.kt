package fluxo.io.rad

/**
 * Opens a [RandomAccessData] over `array[offset, offset + length)` (the platform `ByteArrayRad`).
 *
 * A factory function, not an `expect class`: common code only needs the instance as
 * [RandomAccessData], while an `expect class` extending the abstract `BasicRad` makes the
 * common-metadata compiler demand every abstract hook in the `expect` declaration too.
 * Test compilations never run that check, so such a break surfaced only in publishing builds.
 */
internal expect fun byteArrayRad(array: ByteArray, offset: Int, length: Int): RandomAccessData
