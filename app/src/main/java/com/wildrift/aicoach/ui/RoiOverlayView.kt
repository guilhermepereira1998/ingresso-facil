package com.wildrift.aicoach.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import com.wildrift.aicoach.model.NormalizedRect
import com.wildrift.aicoach.model.HudConfig

class RoiOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var hudConfig: HudConfig = HudConfig()
        set(value) {
            field = value
            invalidate()
        }

    var onConfigChanged: ((HudConfig) -> Unit)? = null
    private var selected = -1
    private var resizing = false
    private var lastX = 0f
    private var lastY = 0f

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        val regions = listOf(hudConfig.minimap, hudConfig.matchClock, hudConfig.kdaScore, hudConfig.currentGold)
        val x = (event.x / width).coerceIn(0f, 1f)
        val y = (event.y / height).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selected = regions.indexOfFirst {
                    x >= it.left - .015f && x <= it.left + it.width + .015f &&
                    y >= it.top - .025f && y <= it.top + it.height + .025f
                }
                if (selected < 0) return false
                val region = regions[selected]
                resizing = kotlin.math.abs(event.x - (region.left + region.width) * width) < 32 * resources.displayMetrics.density &&
                    kotlin.math.abs(event.y - (region.top + region.height) * height) < 32 * resources.displayMetrics.density
                lastX = x; lastY = y
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected < 0) return false
                val old = regions[selected]
                val dx = x - lastX; val dy = y - lastY
                val adjusted = if (resizing) old.copy(
                    width = (old.width + dx).coerceIn(.015f, 1f - old.left),
                    height = (old.height + dy).coerceIn(.015f, 1f - old.top)
                ) else old.copy(
                    left = (old.left + dx).coerceIn(0f, 1f - old.width),
                    top = (old.top + dy).coerceIn(0f, 1f - old.height)
                )
                hudConfig = when (selected) {
                    0 -> hudConfig.copy(minimap = adjusted)
                    1 -> hudConfig.copy(matchClock = adjusted)
                    2 -> hudConfig.copy(kdaScore = adjusted)
                    else -> hudConfig.copy(currentGold = adjusted)
                }
                onConfigChanged?.invoke(hudConfig)
                lastX = x; lastY = y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selected = -1
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return false
    }

    private val minimapPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val ocrPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val mapRect = hudConfig.minimap.toPixelRect(width, height)
        canvas.drawRect(mapRect, minimapPaint)
        canvas.drawText("MINIMAP", mapRect.left.toFloat(), (mapRect.bottom + 28).toFloat(), labelPaint)

        val clockRect = hudConfig.matchClock.toPixelRect(width, height)
        canvas.drawRect(clockRect, ocrPaint)
        canvas.drawText("TIME", clockRect.left.toFloat(), (clockRect.bottom + 28).toFloat(), labelPaint)

        val kdaRect = hudConfig.kdaScore.toPixelRect(width, height)
        canvas.drawRect(kdaRect, ocrPaint)
        canvas.drawText("KDA", kdaRect.left.toFloat(), (kdaRect.bottom + 28).toFloat(), labelPaint)

        val goldRect = hudConfig.currentGold.toPixelRect(width, height)
        canvas.drawRect(goldRect, ocrPaint)
        canvas.drawText("GOLD", goldRect.left.toFloat(), (goldRect.bottom + 28).toFloat(), labelPaint)
    }
}
