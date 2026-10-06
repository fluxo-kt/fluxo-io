@file:OptIn(UnsafeWasmMemoryApi::class)

package fluxo.io.rad

import fluxo.io.IOException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * The WASI `fd_pread` implementation over real files in the preopened `/tmp` (the test task
 * maps it to a host temp dir, see fluxo-io-rad/build.gradle.kts). Files are written with WASI
 * calls too, so the test needs nothing from the host but the preopen.
 */
internal class WasiFileRadTest {

    @Test
    fun contract() = RadContract.verify { bytes -> RandomAccessData.open(tempFile(bytes)) }

    @Test
    fun relativePathResolvesAgainstTheFirstPreopen() {
        val path = tempFile(RadContract.bytes())
        RandomAccessData.open(path.removePrefix("/tmp/")).use {
            assertContentEquals(RadContract.bytes(), it.readAllBytes())
        }
    }

    @Test
    fun outsidePreopensMissingAndDirectoryFailAtOpen() {
        assertFailsWith<IOException> { RandomAccessData.open("/etc/hosts") }
        assertFailsWith<IOException> { RandomAccessData.open("/tmp/missing-" + Random.nextLong()) }
        assertFailsWith<IOException> { RandomAccessData.open("/tmp") }
    }

    @Test
    fun preopenProbeStopsAtAFileTheProgramAlreadyOpened() {
        // The file takes the first fd after the preopens; Node answers EINVAL for it, not EBADF.
        val (_, fd) = createFile(RadContract.bytes())
        try {
            assertEquals(listOf("/tmp"), discoverPreopens().map { it.first })
        } finally {
            assertEquals(0, fdClose(fd))
        }
    }

    @Test
    fun readsAcrossTheHostReadChunkIntoABufferOffset() {
        val data = ByteArray(200_000) { (it * 31 + it / 251).toByte() }
        RandomAccessData.open(tempFile(data)).use { rad ->
            val buf = ByteArray(150_000)
            assertEquals(buf.size - 7, rad.readFully(buf, position = 30_000, offset = 7))
            val expected = data.copyOfRange(30_000, 30_000 + buf.size - 7)
            assertContentEquals(expected, buf.copyOfRange(7, buf.size))
            assertContentEquals(data, rad.readAllBytes())
        }
    }

    private val created = ArrayList<String>()

    @AfterTest
    fun deleteFiles() {
        withScopedMemoryAllocator { alloc ->
            for (name in created) {
                val bytes = name.encodeToByteArray()
                val ptr = alloc.allocate(bytes.size)
                bytes.forEachIndexed { i, b -> (ptr + i).storeByte(b) }
                pathUnlinkFile(3, ptr.address.toInt(), bytes.size)
            }
        }
        created.clear()
    }

    private fun tempFile(bytes: ByteArray): String {
        val (path, fd) = createFile(bytes)
        assertEquals(0, fdClose(fd))
        return path
    }

    /**
     * Creates `/tmp/<random>` holding [bytes] via path_open(CREAT|TRUNC) + fd_write; returns its
     * path and its still-open fd.
     */
    private fun createFile(bytes: ByteArray): Pair<String, Int> {
        val name = "fluxo-rad-" + Random.nextLong().toULong() + ".bin"
        created += name
        return withScopedMemoryAllocator { alloc ->
            val nameBytes = name.encodeToByteArray()
            val namePtr = alloc.allocate(nameBytes.size)
            nameBytes.forEachIndexed { i, b -> (namePtr + i).storeByte(b) }
            val fdOut = alloc.allocate(4)
            val rights = (1L shl 6) // fd_write
            // oflags CREAT (1) | TRUNC (8), under the first preopen (fd 3).
            val namePtrInt = namePtr.address.toInt()
            val outPtr = fdOut.address.toInt()
            val err = pathOpen(3, 0, namePtrInt, nameBytes.size, 1 or 8, rights, 0L, 0, outPtr)
            assertEquals(0, err)
            val fd = fdOut.loadInt()
            val data = alloc.allocate(bytes.size.coerceAtLeast(1))
            bytes.forEachIndexed { i, b -> (data + i).storeByte(b) }
            val iovec = alloc.allocate(8)
            iovec.storeInt(data.address.toInt())
            (iovec + 4).storeInt(bytes.size)
            val written = alloc.allocate(4)
            assertEquals(0, fdWrite(fd, iovec.address.toInt(), 1, written.address.toInt()))
            assertEquals(bytes.size, written.loadInt())
            "/tmp/$name" to fd
        }
    }
}

@WasmImport("wasi_snapshot_preview1", "path_unlink_file")
private external fun pathUnlinkFile(fd: Int, pathPtr: Int, pathLen: Int): Int

@Suppress("LongParameterList")
@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun pathOpen(
    fd: Int, dirflags: Int, pathPtr: Int, pathLen: Int, oflags: Int,
    rightsBase: Long, rightsInheriting: Long, fdFlags: Int, resultPtr: Int,
): Int

@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun fdWrite(fd: Int, iovsPtr: Int, iovsLen: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun fdClose(fd: Int): Int
