package com.wildrift.aicoach.brain

import com.wildrift.aicoach.model.GameStateSnapshot

class SanityFilter {

    private var lastValidSnapshot: GameStateSnapshot? = null

    fun validate(candidate: GameStateSnapshot): GameStateSnapshot? {
        if (candidate.matchSeconds < 0) return null

        val prev = lastValidSnapshot
        if (prev == null) {
            lastValidSnapshot = candidate
            return candidate
        }

        val clockDelta = candidate.matchSeconds - prev.matchSeconds
        val elapsedSeconds = ((candidate.timestampMs - prev.timestampMs).coerceAtLeast(0) / 1000L).toInt()
        if (candidate.timestampMs < prev.timestampMs || clockDelta < 0 || clockDelta > elapsedSeconds + 5) {
            return null
        }

        val sanitizedGold = if (candidate.currentGold < 0 ||
            (prev.currentGold >= 0 && (candidate.currentGold - prev.currentGold) > 2500)
        ) {
            prev.currentGold
        } else {
            candidate.currentGold
        }

        val cleanSnapshot = candidate.copy(currentGold = sanitizedGold)
        lastValidSnapshot = cleanSnapshot
        return cleanSnapshot
    }

    fun reset() {
        lastValidSnapshot = null
    }
}
