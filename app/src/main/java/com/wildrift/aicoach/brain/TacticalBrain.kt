package com.wildrift.aicoach.brain

import com.wildrift.aicoach.model.GameStateSnapshot

class TacticalBrain(
    private val sanityFilter: SanityFilter = SanityFilter(),
    private val localRuleEngine: LocalRuleEngine = LocalRuleEngine(),
    private val cloudMacroClient: CloudMacroClient = CloudMacroClient()
) {
    suspend fun analyze(rawSnapshot: GameStateSnapshot, cloudEndpointUrl: String, onImmediate: ((List<TacticalCallout>) -> Unit)? = null): List<TacticalCallout> {
        val cleanSnapshot = sanityFilter.validate(rawSnapshot) ?: return emptyList()

        val immediateCalls = localRuleEngine.evaluate(cleanSnapshot).toMutableList()
        onImmediate?.invoke(immediateCalls)
        val cloudCall = cloudMacroClient.fetchMacroAdviceIfDue(cloudEndpointUrl, cleanSnapshot)
        if (cloudCall != null) {
            immediateCalls.add(cloudCall)
        }
        return if (onImmediate == null) immediateCalls else immediateCalls.filter { it.ruleId == "CLOUD_LLM_MACRO" }
    }

    fun reset() {
        sanityFilter.reset()
    }
}
