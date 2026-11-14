package com.wildrift.aicoach.brain

import java.util.concurrent.ConcurrentHashMap

class CooldownManager {
    private val lastTriggeredMap = ConcurrentHashMap<String, Long>()

    fun canTrigger(ruleKey: String, cooldownMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastTriggeredMap[ruleKey]
        if (last == null || nowMs - last >= cooldownMs) {
            lastTriggeredMap[ruleKey] = nowMs
            return true
        }
        return false
    }

    fun reset() {
        lastTriggeredMap.clear()
    }
}
