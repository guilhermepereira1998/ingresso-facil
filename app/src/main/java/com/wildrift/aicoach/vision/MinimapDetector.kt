package com.wildrift.aicoach.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wildrift.aicoach.model.MinimapDetection
import com.wildrift.aicoach.model.MinimapEntityClass
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MinimapDetector(context: Context, modelAssetName: String = "yolov8n_wr_minimap_int8.tflite") {

    private var interpreter: Interpreter? = null
    private val inputSize = 320
    private val numClasses = 4
    private val confidenceThreshold = 0.45f
    var status: String = "Minimapa: modelo não incluído; detecção indisponível"
        private set

    init {
        try {
            val modelBuffer = loadModelFile(context, modelAssetName)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            status = "Minimapa: modelo carregado (CPU)"
        } catch (e: Exception) {
            Log.w("MinimapDetector", "Modelo TFLite ($modelAssetName) não encontrado em assets/. Modo fallback ativo.")
        }
    }

    fun detect(minimapBitmap320: Bitmap): List<MinimapDetection> {
        val tflite = interpreter ?: return emptyList()

        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        val intValues = IntArray(inputSize * inputSize)
        minimapBitmap320.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
        }

        val numAnchors = 2100
        val outputArray = Array(1) { Array(4 + numClasses) { FloatArray(numAnchors) } }

        return try {
            tflite.run(inputBuffer, outputArray)
            parseYoloOutput(outputArray[0], numAnchors)
        } catch (e: Exception) {
            Log.e("MinimapDetector", "Erro na inferência TFLite: ${e.message}")
            emptyList()
        }
    }

    private fun parseYoloOutput(matrix: Array<FloatArray>, numAnchors: Int): List<MinimapDetection> {
        val rawDetections = mutableListOf<MinimapDetection>()
        for (a in 0 until numAnchors) {
            var bestClass = -1
            var bestScore = confidenceThreshold
            for (c in 0 until numClasses) {
                val score = matrix[4 + c][a]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }
            if (bestClass != -1) {
                val xNorm = (matrix[0][a] / inputSize).coerceIn(0f, 1f)
                val yNorm = (matrix[1][a] / inputSize).coerceIn(0f, 1f)
                val entity = when (bestClass) {
                    0 -> MinimapEntityClass.ALIADO
                    1 -> MinimapEntityClass.INIMIGO
                    2 -> MinimapEntityClass.OBJETIVO_ATIVO
                    else -> MinimapEntityClass.ONDA_MINIONS
                }
                rawDetections.add(MinimapDetection(entity, bestScore, xNorm, yNorm))
            }
        }
        return applySimpleNms(rawDetections, minDistance = 0.04f)
    }

    private fun applySimpleNms(items: List<MinimapDetection>, minDistance: Float): List<MinimapDetection> {
        val sorted = items.sortedByDescending { it.confidence }
        val kept = mutableListOf<MinimapDetection>()
        for (candidate in sorted) {
            val overlaps = kept.any {
                it.entityClass == candidate.entityClass &&
                    kotlin.math.hypot(it.xNormalized - candidate.xNormalized, it.yNormalized - candidate.yNormalized) < minDistance
            }
            if (!overlaps) kept.add(candidate)
        }
        return kept
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        context.assets.openFd(assetName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
            }
        }
    }

    fun close() {
        interpreter?.close()
    }
}
