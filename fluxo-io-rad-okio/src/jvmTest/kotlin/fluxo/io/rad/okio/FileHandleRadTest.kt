package fluxo.io.rad.okio

import fluxo.io.rad.RadContract
import fluxo.io.rad.RandomAccessData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** The Okio FileHandle adapter over real temp files, through the system file system. */
internal class FileHandleRadTest {

    @Test
    fun contract() {
        val files = ArrayList<File>()
        try {
            RadContract.verify { bytes ->
                val file = File.createTempFile("okio-contract", ".bin").also(files::add)
                file.writeBytes(bytes)
                RandomAccessData.open(FileSystem.SYSTEM.openReadOnly(file.toOkioPath()))
            }
        } finally {
            files.forEach(File::delete)
        }
    }

    @Test
    fun lastHandleClosesTheFileHandle() {
        val file = File.createTempFile("okio-close", ".bin").apply { writeBytes(RadContract.bytes()) }
        try {
            val handle = FileSystem.SYSTEM.openReadOnly(file.toOkioPath())
            val rad = RandomAccessData.open(handle)
            val shared = rad.share()
            rad.close()
            handle.size() // still open for the shared handle
            shared.close()
            assertFailsWith<IllegalStateException> { handle.size() } // Okio: "closed"
        } finally {
            file.delete()
        }
    }
}
