package com.wildrift.aicoach

import com.wildrift.aicoach.training.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

class TrainingFilesTest {
    @Test fun `automatic samples obey cooldown pause and storage reserve`() {
        val space = CollectionPolicy.RESERVE_BYTES + 1
        assertTrue(CollectionPolicy.shouldSave(1, null, true, false, false, 0, space))
        assertFalse(CollectionPolicy.shouldSave(4999, 0, true, false, false, 1, space))
        assertTrue(CollectionPolicy.shouldSave(5000, 0, true, false, false, 1, space))
        assertFalse(CollectionPolicy.shouldSave(9000, 0, true, false, true, 1, space))
        assertTrue(CollectionPolicy.shouldSave(9000, 0, false, true, true, 1, space))
        assertFalse(CollectionPolicy.shouldSave(9000, 0, true, true, false, 1, 0))
        assertFalse(CollectionPolicy.shouldSave(9000, 0, true, true, false, 600, space))
    }
    @Test fun `video size preserves orientation caps resolution and uses even dimensions`() {
        assertEquals(1280 to 576, CollectionPolicy.videoSize(2400, 1080))
        assertEquals(576 to 1280, CollectionPolicy.videoSize(1080, 2400))
        assertEquals(126 to 62, CollectionPolicy.videoSize(127, 63))
    }
    @Test fun `export includes completed data but excludes active files and unfinished samples`() {
        val root = Files.createTempDirectory("coach-export").toFile()
        try {
            root.resolve("session/sample").mkdirs()
            root.resolve("session/sample/frame.png").writeBytes(byteArrayOf(1, 2, 3))
            root.resolve("session/video.mp4").writeText("completed")
            root.resolve("session/video.mp4.part").writeText("active")
            root.resolve("session/.pending").mkdir()
            root.resolve("session/.pending/frame.png").writeText("unfinished")
            val out = ByteArrayOutputStream()
            assertEquals(2, TrainingArchive.write(root, out))
            val names = mutableListOf<String>()
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                while (true) { val item = zip.nextEntry ?: break; names.add(item.name); zip.readBytes() }
            }
            assertEquals(listOf("session/sample/frame.png", "session/video.mp4"), names)
        } finally { root.deleteRecursively() }
    }
    @Test fun `export rejects symlinks outside the training folder`() {
        val root = Files.createTempDirectory("coach-root").toFile()
        val outside = Files.createTempFile("outside", ".txt").toFile()
        try {
            outside.writeText("private")
            Files.createSymbolicLink(root.resolve("leak.txt").toPath(), outside.toPath())
            assertTrue(TrainingArchive.completedFiles(root).isEmpty())
        } finally { root.deleteRecursively(); outside.delete() }
    }
}
