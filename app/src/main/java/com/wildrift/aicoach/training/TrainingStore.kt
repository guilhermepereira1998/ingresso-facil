package com.wildrift.aicoach.training

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import com.wildrift.aicoach.model.GameStateSnapshot
import com.wildrift.aicoach.model.HudConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

class TrainingStore(context: Context, kind: String, lane: String) {
    companion object {
        fun root(context: Context): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "training").apply { mkdirs() }
        fun summary(context: Context): String {
            val files = TrainingArchive.completedFiles(root(context))
            val samples = files.count { it.name == "frame.png" }
            val videos = files.count { it.extension == "mp4" }
            val mb = files.sumOf { it.length() } / (1024.0 * 1024)
            return "%d capturas • %d vídeos • %.1f MB".format(samples, videos, mb)
        }
    }
    val sessionId = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    val directory = File(root(context), sessionId).apply { check(mkdirs()) }
    var sampleCount = 0
        private set

    init {
        File(directory, "session.json").writeText(JSONObject().apply {
            put("schemaVersion", 1); put("sessionId", sessionId); put("kind", kind)
            put("createdAtMs", System.currentTimeMillis()); put("lane", lane.take(32))
            put("appVersion", "1.0.3-beta"); put("androidApi", Build.VERSION.SDK_INT)
            put("deviceModel", Build.MODEL); put("manufacturer", Build.MANUFACTURER)
            put("annotationsAreGroundTruth", false)
        }.toString(2))
    }

    fun save(bitmap: Bitmap, config: HudConfig, snapshot: GameStateSnapshot?, elapsedMs: Long, acquiredAtMs: Long): File {
        check(directory.usableSpace >= CollectionPolicy.RESERVE_BYTES) { "Espaço livre insuficiente para novas capturas" }
        val id = "sample_${System.currentTimeMillis()}_${sampleCount.toString().padStart(4, '0')}"
        val pending = File(directory, ".$id.part").apply { check(mkdir()) }
        try {
            val frameHash = png(bitmap, File(pending, "frame.png"))
            val rect = config.minimap.toPixelRect(bitmap.width, bitmap.height)
            val minimap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            val mapHash = try { png(minimap, File(pending, "minimap.png")) } finally { if (minimap !== bitmap) minimap.recycle() }
            File(pending, "metadata.json").writeText(JSONObject().apply {
                put("schemaVersion", 1); put("sessionId", sessionId); put("acquiredAtMs", acquiredAtMs); put("savedAtMs", System.currentTimeMillis())
                put("elapsedMs", elapsedMs); put("width", bitmap.width); put("height", bitmap.height)
                put("hud", JSONObject(Json.encodeToString(config)))
                put("ocr", if (snapshot == null) JSONObject.NULL else JSONObject(Json.encodeToString(snapshot)))
                put("annotationsAreGroundTruth", false)
                put("frameSha256", frameHash); put("minimapSha256", mapHash)
            }.toString(2))
            val completed = File(directory, id)
            check(pending.renameTo(completed)) { "Não foi possível concluir a captura" }
            sampleCount++
            return completed
        } catch (e: Exception) { pending.deleteRecursively(); throw e }
    }

    private fun png(bitmap: Bitmap, file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(file.outputStream().buffered(), digest).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Falha ao salvar PNG" }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
