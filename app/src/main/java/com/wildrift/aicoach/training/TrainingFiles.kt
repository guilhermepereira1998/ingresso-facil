package com.wildrift.aicoach.training

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CollectionPolicy {
    const val INTERVAL_MS = 5000L
    const val MAX_SAMPLES = 600
    const val RESERVE_BYTES = 200L * 1024 * 1024
    fun shouldSave(now: Long, last: Long?, automatic: Boolean, manual: Boolean, paused: Boolean, count: Int, freeBytes: Long): Boolean {
        if (count >= MAX_SAMPLES || freeBytes < RESERVE_BYTES) return false
        return manual || (automatic && !paused && (last == null || now - last >= INTERVAL_MS))
    }
    fun videoSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, 1280.0 / maxOf(width, height))
        return maxOf(2, (width * scale).toInt() / 2 * 2) to maxOf(2, (height * scale).toInt() / 2 * 2)
    }
}

object TrainingArchive {
    fun completedFiles(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val base = root.canonicalFile
        val prefix = base.path + File.separator
        return base.walkTopDown().onEnter { dir ->
            !dir.name.startsWith(".") && !Files.isSymbolicLink(dir.toPath()) &&
                (dir == base || dir.canonicalPath.startsWith(prefix))
        }.filter { file ->
            file.isFile && !file.name.endsWith(".part") && !file.name.startsWith(".") &&
                file.canonicalPath.startsWith(prefix) && !Files.isSymbolicLink(file.toPath())
        }.sortedBy { it.relativeTo(base).invariantSeparatorsPath }.toList()
    }
    fun write(root: File, target: OutputStream, checkActive: () -> Unit = {}): Int {
        val files = completedFiles(root)
        ZipOutputStream(target.buffered()).use { zip ->
            // MP4 and PNG are already compressed; keep export inexpensive on the phone.
            zip.setLevel(1)
            for (file in files) {
                checkActive()
                zip.putNextEntry(ZipEntry(file.relativeTo(root.canonicalFile).invariantSeparatorsPath))
                file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        checkActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
        return files.size
    }
}
