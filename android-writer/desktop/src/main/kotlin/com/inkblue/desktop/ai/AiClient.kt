package com.inkblue.desktop.ai

import com.inkblue.desktop.data.AiProfile
import com.inkblue.desktop.data.AiProvider
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

fun AiProfile.toAiConfig() = AiConfig(provider, effectiveBaseUrl(), apiKey, effectiveModel())

object AiClient {

    suspend fun generate(
        config: AiConfig,
        system: String,
        userPrompt: String,
        maxTokens: Int = 2048,
    ): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            AiProvider.ANTHROPIC -> anthropicMessages(config, system, userPrompt, maxTokens)
            AiProvider.OPENAI -> openAiChat(config, system, userPrompt, maxTokens)
        }
    }

    private fun anthropicMessages(config: AiConfig, system: String, prompt: String, maxTokens: Int): String {
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }
        val json = post(
            url = config.baseUrl.trimEnd('/') + "/v1/messages",
            headers = mapOf("x-api-key" to config.apiKey, "anthropic-version" to "2023-06-01"),
            body = body,
        )
        if (json.optString("stop_reason") == "refusal") {
            throw IOException("请求被模型安全策略拒绝，请调整内容后重试")
        }
        val content = json.getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.getString("type") == "text") {
                val text = block.getString("text").trim()
                if (text.isNotEmpty()) return text
            }
        }
        throw IOException("AI 返回内容为空")
    }

    private fun openAiChat(config: AiConfig, system: String, prompt: String, maxTokens: Int): String {
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("messages", JSONArray()
                .put(JSONObject().apply { put("role", "system"); put("content", system) })
                .put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
        }
        val json = post(
            url = config.baseUrl.trimEnd('/') + "/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
            body = body,
        )
        val text = json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
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
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code：${text.take(300)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
