package com.wildrift.aicoach.model

import android.content.Context
import android.graphics.Rect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    fun toPixelRect(bitmapWidth: Int, bitmapHeight: Int): Rect {
        val x = (left.coerceIn(0f, 0.99f) * bitmapWidth).toInt()
        val y = (top.coerceIn(0f, 0.99f) * bitmapHeight).toInt()
        val w = (width * bitmapWidth).toInt().coerceAtLeast(1).coerceAtMost(bitmapWidth - x)
        val h = (height * bitmapHeight).toInt().coerceAtLeast(1).coerceAtMost(bitmapHeight - y)
        return Rect(x, y, x + w, y + h)
    }
}

/**
 * Coordenadas normalizadas (0.0f a 1.0f) para suportar qualquer proporção (16:9, 19.5:9, 20:9).
 * Padrão calibrado para HUD clássica do Wild Rift (Minimapa superior esquerdo, Placar/Tempo superior direito).
 */
@Serializable
data class HudConfig(
    val minimap: NormalizedRect = NormalizedRect(left = 0.015f, top = 0.020f, width = 0.185f, height = 0.310f),
    val matchClock: NormalizedRect = NormalizedRect(left = 0.925f, top = 0.015f, width = 0.065f, height = 0.055f),
    val kdaScore: NormalizedRect = NormalizedRect(left = 0.815f, top = 0.015f, width = 0.105f, height = 0.055f),
    val currentGold: NormalizedRect = NormalizedRect(left = 0.030f, top = 0.355f, width = 0.085f, height = 0.060f)
) {
    fun withNotchOffset(offsetFraction: Float): HudConfig {
        return copy(
            minimap = minimap.copy(left = (0.015f + offsetFraction).coerceIn(0f, 0.25f)),
            currentGold = currentGold.copy(left = (0.030f + offsetFraction).coerceIn(0f, 0.25f))
        )
    }

    companion object {
        private const val PREFS_NAME = "wr_hud_calibration"
        private const val KEY_CONFIG = "hud_config_json"

        fun load(context: Context): HudConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_CONFIG, null) ?: return HudConfig()
            return runCatching { Json.decodeFromString<HudConfig>(raw) }.getOrDefault(HudConfig())
        }

        fun save(context: Context, config: HudConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CONFIG, Json.encodeToString(config)).apply()
        }
    }
}
