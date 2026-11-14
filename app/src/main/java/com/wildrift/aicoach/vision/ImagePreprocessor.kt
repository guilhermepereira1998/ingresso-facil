package com.wildrift.aicoach.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect

object ImagePreprocessor {

    /**
     * Recorta uma região segura e aplica escala de cinza + alto contraste (binarização)
     * para facilitar o OCR; precisão depende da HUD e requer validação com capturas reais.
     */
    fun cropAndBinarizeForOcr(source: Bitmap, roi: Rect, scaleFactor: Float = 2.0f): Bitmap {
        val safeRect = Rect(
            roi.left.coerceIn(0, source.width - 1),
            roi.top.coerceIn(0, source.height - 1),
            roi.right.coerceIn(1, source.width),
            roi.bottom.coerceIn(1, source.height)
        )

        val rawCrop = Bitmap.createBitmap(
            source,
            safeRect.left,
            safeRect.top,
            safeRect.width().coerceAtLeast(1),
            safeRect.height().coerceAtLeast(1)
        )

        val targetW = (rawCrop.width * scaleFactor).toInt().coerceAtLeast(1)
        val targetH = (rawCrop.height * scaleFactor).toInt().coerceAtLeast(1)
        val upscaled = Bitmap.createScaledBitmap(rawCrop, targetW, targetH, true)
        if (upscaled != rawCrop && rawCrop != source) rawCrop.recycle()

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val contrast = 3.2f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(upscaled, 0f, 0f, paint)
        if (upscaled != source) upscaled.recycle()

        val pixels = IntArray(targetW * targetH)
        output.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        for (i in pixels.indices) {
            val luma = Color.red(pixels[i])
            pixels[i] = if (luma > 135) Color.WHITE else Color.BLACK
        }
        output.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        return output
    }

    fun cropMinimap(source: Bitmap, roi: Rect, targetSize: Int = 320): Bitmap {
        val safeRect = Rect(
            roi.left.coerceIn(0, source.width - 1),
            roi.top.coerceIn(0, source.height - 1),
            roi.right.coerceIn(1, source.width),
            roi.bottom.coerceAtLeast(roi.top + 1).coerceAtMost(source.height)
        )
        val crop = Bitmap.createBitmap(
            source,
            safeRect.left,
            safeRect.top,
            safeRect.width().coerceAtLeast(1),
            safeRect.height().coerceAtLeast(1)
        )
        val resized = Bitmap.createScaledBitmap(crop, targetSize, targetSize, true)
        if (resized != crop && crop != source) crop.recycle()
        return if (resized === source) resized.copy(Bitmap.Config.ARGB_8888, false) else resized
    }
}
