package com.wildrift.aicoach.model

import kotlinx.serialization.Serializable

@Serializable
enum class MinimapEntityClass {
    ALIADO,
    INIMIGO,
    OBJETIVO_ATIVO,
    ONDA_MINIONS
}

@Serializable
data class MinimapDetection(
    val entityClass: MinimapEntityClass,
    val confidence: Float,
    val xNormalized: Float,
    val yNormalized: Float
)

@Serializable
data class GameStateSnapshot(
    val timestampMs: Long,
    val matchSeconds: Int,
    val formattedClock: String,
    val currentGold: Int,
    val rawKdaText: String,
    val playerLane: String,
    val minimapDetections: List<MinimapDetection> = emptyList(),
    val rawOcrDebug: String = "",
    val playerXNormalized: Float? = null,
    val playerYNormalized: Float? = null
) {
    fun countEnemiesNearPlayer(playerX: Float? = playerXNormalized, playerY: Float? = playerYNormalized, radius: Float = 0.28f): Int {
        if (playerX == null || playerY == null) return 0
        return minimapDetections.count {
            it.entityClass == MinimapEntityClass.INIMIGO &&
                hypot(it.xNormalized - playerX, it.yNormalized - playerY) <= radius
        }
    }

    fun countAlliesNearPlayer(playerX: Float? = playerXNormalized, playerY: Float? = playerYNormalized, radius: Float = 0.28f): Int {
        if (playerX == null || playerY == null) return 0
        return minimapDetections.count {
            it.entityClass == MinimapEntityClass.ALIADO &&
                hypot(it.xNormalized - playerX, it.yNormalized - playerY) <= radius
        }
    }

    private fun hypot(dx: Float, dy: Float): Float = kotlin.math.sqrt(dx * dx + dy * dy)
}
