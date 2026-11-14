package com.wildrift.aicoach.vision

import com.wildrift.aicoach.model.MinimapDetection
import com.wildrift.aicoach.model.MinimapEntityClass
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

enum class TensorEncoding(val bytes: Int) { FLOAT32(4), UINT8(1), INT8(1) }

/** RGB normalization and tensor quantization, independently testable without Android. */
object MinimapTensorCodec {
    fun packRgb(pixels: IntArray, channelFirst: Boolean, encoding: TensorEncoding, scale: Float, zeroPoint: Int): ByteBuffer {
        if (encoding != TensorEncoding.FLOAT32) require(scale.isFinite() && scale > 0f)
        val buffer = ByteBuffer.allocateDirect(pixels.size * 3 * encoding.bytes).order(ByteOrder.nativeOrder())
        fun put(pixel: Int, channel: Int) {
            val value = ((pixel shr (16 - channel * 8)) and 255) / 255f
            when (encoding) {
                TensorEncoding.FLOAT32 -> buffer.putFloat(value)
                TensorEncoding.UINT8 -> buffer.put((value / scale + zeroPoint).roundToInt().coerceIn(0, 255).toByte())
                TensorEncoding.INT8 -> buffer.put((value / scale + zeroPoint).roundToInt().coerceIn(-128, 127).toByte())
            }
        }
        if (channelFirst) for (channel in 0..2) for (pixel in pixels) put(pixel, channel)
        else for (pixel in pixels) for (channel in 0..2) put(pixel, channel)
        buffer.rewind()
        return buffer
    }

    fun unpack(buffer: ByteBuffer, encoding: TensorEncoding, scale: Float, zeroPoint: Int): FloatArray {
        if (encoding != TensorEncoding.FLOAT32) require(scale.isFinite() && scale > 0f)
        buffer.rewind()
        return FloatArray(buffer.remaining() / encoding.bytes) {
            when (encoding) {
                TensorEncoding.FLOAT32 -> buffer.float
                TensorEncoding.UINT8 -> ((buffer.get().toInt() and 255) - zeroPoint) * scale
                TensorEncoding.INT8 -> (buffer.get().toInt() - zeroPoint) * scale
            }
        }
    }
}

/** Explicit YOLOv8 output contract: four center/size channels and four class probabilities. */
object MinimapYoloDecoder {
    private data class Box(val detection: MinimapDetection, val width: Float, val height: Float)
    fun decode(values: FloatArray, shape: IntArray, normalizedCoordinates: Boolean, inputSize: Int = 320): List<MinimapDetection> {
        require(shape.size == 3 && shape[0] == 1 && (shape[1] == 8 || shape[2] == 8)) { "Expected [1,8,N] or [1,N,8]" }
        val channelsFirst = shape[1] == 8
        val anchors = if (channelsFirst) shape[2] else shape[1]
        require(values.size == anchors * 8)
        fun value(anchor: Int, channel: Int) = values[if (channelsFirst) channel * anchors + anchor else anchor * 8 + channel]
        val boxes = mutableListOf<Box>()
        for (anchor in 0 until anchors) {
            val scores = FloatArray(4) { value(anchor, it + 4) }
            if (scores.any { !it.isFinite() || it !in 0f..1f }) continue
            val classId = scores.indices.maxBy { scores[it] }
            if (scores[classId] < .45f) continue
            val divisor = if (normalizedCoordinates) 1f else inputSize.toFloat()
            val x = value(anchor, 0) / divisor
            val y = value(anchor, 1) / divisor
            val w = value(anchor, 2) / divisor
            val h = value(anchor, 3) / divisor
            if (!x.isFinite() || !y.isFinite() || !w.isFinite() || !h.isFinite() || x !in 0f..1f || y !in 0f..1f || w <= 0f || h <= 0f) continue
            boxes.add(Box(MinimapDetection(MinimapEntityClass.values()[classId], scores[classId], x, y), w, h))
        }
        val kept = mutableListOf<Box>()
        for (box in boxes.sortedByDescending { it.detection.confidence }) {
            if (kept.none { it.detection.entityClass == box.detection.entityClass && iou(it, box) > .45f }) kept.add(box)
        }
        return kept.map { it.detection }
    }

    private fun iou(a: Box, b: Box): Float {
        val ax = a.detection.xNormalized; val ay = a.detection.yNormalized
        val bx = b.detection.xNormalized; val by = b.detection.yNormalized
        val overlapW = (minOf(ax + a.width / 2, bx + b.width / 2) - maxOf(ax - a.width / 2, bx - b.width / 2)).coerceAtLeast(0f)
        val overlapH = (minOf(ay + a.height / 2, by + b.height / 2) - maxOf(ay - a.height / 2, by - b.height / 2)).coerceAtLeast(0f)
        val intersection = overlapW * overlapH
        return intersection / (a.width * a.height + b.width * b.height - intersection).coerceAtLeast(.000001f)
    }
}
