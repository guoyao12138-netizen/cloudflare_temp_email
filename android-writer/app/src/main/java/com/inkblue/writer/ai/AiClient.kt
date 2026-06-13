package com.inkblue.writer.ai

import com.inkblue.writer.data.AiProfile
import com.inkblue.writer.data.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AiConfig(
    val provider: AiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

fun AiProfile.toAiConfig() = AiConfig(
    provider = provider,
    baseUrl = effectiveBaseUrl(),
    apiKey = apiKey,
    model = effectiveModel(),
)

/** One chat turn: role is "user" or "assistant". */
data class ChatTurn(val role: String, val content: String)

/**
 * Minimal HTTP client for the two supported protocols. Web search is served
 * by Anthropic's server-side `web_search` tool (Anthropic protocol only);
 * the OpenAI-compatible path silently ignores the flag.
 */
object AiClient {

    suspend fun generate(
        config: AiConfig,
        system: String,
        userPrompt: String,
        maxTokens: Int = 2048,
        enableSearch: Boolean = false,
    ): String = chat(config, system, listOf(ChatTurn("user", userPrompt)), maxTokens, enableSearch)

    suspend fun chat(
        config: AiConfig,
        system: String,
        history: List<ChatTurn>,
        maxTokens: Int = 2048,
        enableSearch: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            AiProvider.ANTHROPIC -> anthropicRun(config, system, history, maxTokens, enableSearch)
            AiProvider.OPENAI -> openAiRun(config, system, history, maxTokens)
        }
    }

    private fun anthropicRun(
        config: AiConfig,
        system: String,
        history: List<ChatTurn>,
        maxTokens: Int,
        enableSearch: Boolean,
    ): String {
        val messages = JSONArray()
        history.forEach { turn ->
            messages.put(JSONObject().apply {
                put("role", turn.role)
                put("content", turn.content)
            })
        }
        val collected = StringBuilder()
        // Server-side tools may pause the turn; resume up to 6 rounds.
        repeat(6) {
            val body = JSONObject().apply {
                put("model", config.model)
                put("max_tokens", maxTokens)
                put("system", system)
                put("messages", messages)
                if (enableSearch) {
                    put("tools", JSONArray().put(JSONObject().apply {
                        put("type", "web_search_20260209")
                        put("name", "web_search")
                    }))
                }
            }
            val json = post(
                url = config.baseUrl.trimEnd('/') + "/v1/messages",
                headers = mapOf(
                    "x-api-key" to config.apiKey,
                    "anthropic-version" to "2023-06-01",
                ),
                body = body,
            )
            if (json.optString("stop_reason") == "refusal") {
                throw IOException("请求被模型安全策略拒绝，请调整内容后重试")
            }
            val content = json.getJSONArray("content")
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") {
                    collected.append(block.optString("text"))
                }
            }
            if (json.optString("stop_reason") != "pause_turn") {
                val text = collected.toString().trim()
                if (text.isEmpty()) throw IOException("AI 返回内容为空")
                return text
            }
            // Resume: echo the assistant content back and continue the turn.
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", content)
            })
        }
        val text = collected.toString().trim()
        if (text.isEmpty()) throw IOException("搜索轮次过多且无内容返回，请重试")
        return text
    }

    private fun openAiRun(
        config: AiConfig,
        system: String,
        history: List<ChatTurn>,
        maxTokens: Int,
    ): String {
        val messages = JSONArray().put(JSONObject().apply {
            put("role", "system")
            put("content", system)
        })
        history.forEach { turn ->
            messages.put(JSONObject().apply {
                put("role", turn.role)
                put("content", turn.content)
            })
        }
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("messages", messages)
        }
        val json = post(
            url = config.baseUrl.trimEnd('/') + "/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
            body = body,
        )
        val text = json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        if (text.isEmpty()) throw IOException("AI 返回内容为空")
        return text
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 300_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: ""
            if (code !in 200..299) {
                throw IOException("HTTP $code：${text.take(300)}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
