package com.wildrift.aicoach.brain

import com.wildrift.aicoach.model.GameStateSnapshot

enum class TacticalPriority(val weight: Int) {
    URGENTE(3),
    ESTRATEGIA(2),
    DICA(1)
}

data class TacticalCallout(
    val ruleId: String,
    val message: String,
    val priority: TacticalPriority,
    val createdAtMs: Long = System.currentTimeMillis()
)

class LocalRuleEngine(
    private val cooldownManager: CooldownManager = CooldownManager()
) {
    fun evaluate(snapshot: GameStateSnapshot, nowMs: Long = snapshot.timestampMs): List<TacticalCallout> {
        val calls = mutableListOf<TacticalCallout>()

        val nearbyEnemies = snapshot.countEnemiesNearPlayer()
        val nearbyAllies = snapshot.countAlliesNearPlayer()
        if (nearbyEnemies >= 2 && nearbyAllies <= 1) {
            if (cooldownManager.canTrigger("MINIMAP_GANK", 25_000L, nowMs)) {
                calls.add(
                    TacticalCallout(
                        ruleId = "MINIMAP_GANK",
                        message = "Atenção, múltiplos inimigos próximos da sua rota.",
                        priority = TacticalPriority.URGENTE,
                        createdAtMs = nowMs
                    )
                )
            }
        }

        if (snapshot.matchSeconds in 262..268) {
            if (cooldownManager.canTrigger("OBJ_DRAGON_5M", 60_000L, nowMs)) {
                calls.add(
                    TacticalCallout(
                        ruleId = "OBJ_DRAGON_5M",
                        message = "Objetivo nascendo em 35 segundos, prepare visão e wave.",
                        priority = TacticalPriority.URGENTE,
                        createdAtMs = nowMs
                    )
                )
            }
        }

        if (snapshot.matchSeconds in 682..688) {
            if (cooldownManager.canTrigger("OBJ_BARON_12M", 60_000L, nowMs)) {
                calls.add(
                    TacticalCallout(
                        ruleId = "OBJ_BARON_12M",
                        message = "Barão nasce em 35 segundos, controle a visão superior.",
                        priority = TacticalPriority.URGENTE,
                        createdAtMs = nowMs
                    )
                )
            }
        }

        if (snapshot.matchSeconds >= 180 && snapshot.currentGold >= 1300) {
            if (cooldownManager.canTrigger("HIGH_GOLD_BASE", 90_000L, nowMs)) {
                calls.add(
                    TacticalCallout(
                        ruleId = "HIGH_GOLD_BASE",
                        message = "Ouro alto na bolsa, procure janela de base.",
                        priority = TacticalPriority.ESTRATEGIA,
                        createdAtMs = nowMs
                    )
                )
            }
        }

        return calls
    }
}
