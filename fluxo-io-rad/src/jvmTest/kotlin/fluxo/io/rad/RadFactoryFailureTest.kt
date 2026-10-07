package fluxo.io.rad

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.junit.Assume.assumeTrue

/**
 * A factory that opens the file itself must close it when setup fails: the caller never gets a
 * handle, so nothing else can. Each factory gets its own file, and the check asks whether this
 * process still holds THAT file open. A process-wide descriptor count is not usable: class
 * loading and other tests' cleanup move it, and the change lands on whichever factory ran.
 */
internal class RadFactoryFailureTest {

    @Test
    @Suppress("DEPRECATION")
    fun fileFactoriesCloseTheFileWhenTheRangeIsInvalid() {
        val badOffset = 17L
        val factories = mapOf<String, (File) -> Any>(
            "FileChannel" to { RadFileChannelAccessor(it, offset = badOffset) },
            "SeekableByteChannel" to { RadSeekableByteChannelAccessor(it, offset = badOffset) },
            "RandomAccessFile" to { RandomAccessFileRadAccessor(it, offset = badOffset) },
            "ByteBuffer" to { RadByteBufferAccessor(it, offset = badOffset) },
            "AsyncFileChannel" to { RadAsyncFileChannelAccessor(it, offset = badOffset) },
        )
        val files = factories.mapValues { (name, open) ->
            File.createTempFile("rad-factory-$name", ".bin").also { file ->
                file.writeBytes(ByteArray(16))
                assertFails(name) { open(file) }
            }
        }
        try {
            val open = openFiles()
            assumeTrue("no way to list this process's open files", open != null)
            val leaked = files.filterValues { it.canonicalPath in open!! }.keys
            assertEquals(emptySet(), leaked, "these factories leaked the file they opened")
        } finally {
            files.values.forEach(File::delete)
        }
    }

    /** Canonical paths of the files this process holds open; `null` when it cannot be listed. */
    private fun openFiles(): Set<String>? {
        val procFds = Paths.get("/proc/self/fd")
        return if (Files.isDirectory(procFds)) procFiles(procFds) else lsofFiles()
    }

    /** Linux: each fd is a link to its file. One can close between listing and read: skip it. */
    private fun procFiles(procFds: Path): Set<String> = Files.list(procFds).use { fds ->
        // Not fds.toList(): that resolves to Stream.toList(), JDK 16+, and the jdk8 lane runs this.
        fds.iterator().asSequence().mapNotNullTo(HashSet()) {
            runCatching { Files.readSymbolicLink(it).toString() }.getOrNull()
        }
    }

    /** macOS has no /proc: lsof prints one `n<canonical path>` line per open file. */
    private fun lsofFiles(): Set<String>? = runCatching {
        // Not ProcessHandle (Java 9): tests compile for the Java 8 floor. The runtime name is
        // "<pid>@<host>" on HotSpot and OpenJ9.
        val pid = ManagementFactory.getRuntimeMXBean().name.substringBefore('@')
        val lsof = ProcessBuilder("lsof", "-p", pid, "-Fn").start()
        val names = lsof.inputStream.bufferedReader().readLines()
        check(lsof.waitFor() == 0 || names.isNotEmpty()) { "lsof failed" }
        names.filter { it.startsWith('n') }.mapTo(HashSet()) { it.substring(1) }
    }.getOrNull()
}
