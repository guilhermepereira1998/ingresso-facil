package com.wildrift.aicoach.brain

import com.wildrift.aicoach.model.GameStateSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class CloudMacroRequest(
    val systemPrompt: String,
    val state: GameStateSnapshot
)

@Serializable
data class CloudMacroResponse(
    val callout: String = ""
)

class CloudMacroClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private var lastRequestMs = 0L

    companion object {
        const val CLOUD_INTERVAL_MS = 20_000L

        const val CHALLENGER_SYSTEM_PROMPT = """
Você é um Analista Tático de League of Legends: Wild Rift (nível Challenger).
Receba o estado JSON atual da partida e responda APENAS com um JSON {"callout": "..."}.
Regras estritas:
1. A frase em 'callout' deve ter no máximo 12 palavras em português direto.
2. Foque em rotação, pressão de rota, controle de objetivos ou janela de compra.
3. Se não houver ação urgente, retorne {"callout": ""}.
"""
    }

    suspend fun fetchMacroAdviceIfDue(
        endpointUrl: String,
        snapshot: GameStateSnapshot
    ): TacticalCallout? {
        if (!endpointUrl.startsWith("https://")) return null
        val now = System.currentTimeMillis()
        if (now - lastRequestMs < CLOUD_INTERVAL_MS) return null
        lastRequestMs = now

        return withContext(Dispatchers.IO) {
            try {
                val payload = json.encodeToString(
                    CloudMacroRequest(
                        systemPrompt = CHALLENGER_SYSTEM_PROMPT.trimIndent(),
                        state = snapshot
                    )
                )
                val request = Request.Builder()
                    .url(endpointUrl)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val source = response.body?.source() ?: return@use null
                    if (source.request(65_537)) return@use null
                    val body = source.readUtf8()
                    val parsed = json.decodeFromString<CloudMacroResponse>(body)
                    val trimmed = parsed.callout.trim()
                    if (trimmed.isNotEmpty() && trimmed.split(Regex("\\s+")).size <= 12) {
                        TacticalCallout(
                            ruleId = "CLOUD_LLM_MACRO",
                            message = trimmed,
                            priority = TacticalPriority.ESTRATEGIA
                        )
                    } else null
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) { null }
        }
    }
}
