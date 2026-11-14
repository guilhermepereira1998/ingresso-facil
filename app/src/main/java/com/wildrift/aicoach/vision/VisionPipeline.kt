package com.wildrift.aicoach.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wildrift.aicoach.model.GameStateSnapshot
import com.wildrift.aicoach.model.HudConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class VisionPipeline(context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val minimapDetector = MinimapDetector(context)

    val modelStatus: String get() = minimapDetector.status

    @Volatile
    var isMinimapDetectionEnabled: Boolean = true

    suspend fun processFrame(
        frame: Bitmap,
        hudConfig: HudConfig,
        playerLane: String
    ): GameStateSnapshot {
        val w = frame.width
        val h = frame.height

        var clockBmp: Bitmap? = null
        var goldBmp: Bitmap? = null
        var kdaBmp: Bitmap? = null
        var minimapBmp: Bitmap? = null

        try {
            clockBmp = ImagePreprocessor.cropAndBinarizeForOcr(frame, hudConfig.matchClock.toPixelRect(w, h))
            goldBmp = ImagePreprocessor.cropAndBinarizeForOcr(frame, hudConfig.currentGold.toPixelRect(w, h))
            kdaBmp = ImagePreprocessor.cropAndBinarizeForOcr(frame, hudConfig.kdaScore.toPixelRect(w, h))

            val rawClock = runOcr(clockBmp)
            val rawGold = runOcr(goldBmp)
            val rawKda = runOcr(kdaBmp)

            val detections = if (isMinimapDetectionEnabled) {
                minimapBmp = ImagePreprocessor.cropMinimap(frame, hudConfig.minimap.toPixelRect(w, h))
                minimapDetector.detect(minimapBmp)
            } else {
                emptyList()
            }

            val parsedSeconds = parseClockToSeconds(rawClock)
            val parsedGold = parseGoldAmount(rawGold)

            return GameStateSnapshot(
                timestampMs = System.currentTimeMillis(),
                matchSeconds = parsedSeconds,
                formattedClock = formatSeconds(parsedSeconds),
                currentGold = parsedGold,
                rawKdaText = rawKda.ifBlank { "0/0/0" },
                playerLane = playerLane,
                minimapDetections = detections,
                rawOcrDebug = "Clock='$rawClock' | Gold='$rawGold' | KDA='$rawKda' | MapObjs=${detections.size}"
            )
        } finally {
            clockBmp?.recycle()
            goldBmp?.recycle()
            kdaBmp?.recycle()
            minimapBmp?.recycle()
        }
    }

    private suspend fun runOcr(bitmap: Bitmap): String {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = withContext(NonCancellable) { recognizer.process(image).await() }
            result.text.replace("\n", " ").trim()
        }.getOrDefault("")
    }

    companion object {
        fun parseClockToSeconds(raw: String): Int {
            val clean = raw.replace("O", "0").replace("o", "0").replace("S", "5")
            val match = Regex("""(\d{1,2})[:.](\d{2})""").find(clean) ?: return -1
            val minutes = match.groupValues[1].toIntOrNull() ?: return -1
            val seconds = match.groupValues[2].toIntOrNull() ?: return -1
            if (seconds >= 60 || minutes > 60) return -1
            return (minutes * 60) + seconds
        }

        fun parseGoldAmount(raw: String): Int {
            val clean = raw.trim().lowercase().replace("o", "0").replace(" ", "")
            val compact = Regex("""^(\d{1,3})(?:[.,](\d{1,2}))?k$""").matchEntire(clean)
            if (compact != null) {
                val amount = compact.groupValues[1].toDouble() +
                    ("0." + compact.groupValues[2].ifBlank { "0" }).toDouble()
                return (amount * 1000).toInt().takeIf { it <= 99999 } ?: -1
            }
            val digits = clean.replace(",", "").replace(".", "")
            if (!Regex("""\d{1,5}""").matches(digits)) return -1
            return digits.toIntOrNull() ?: -1
        }

        private fun formatSeconds(totalSec: Int): String {
            if (totalSec < 0) return "--:--"
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d".format(m, s)
        }
    }

    fun close() {
        recognizer.close()
        minimapDetector.close()
    }
}
