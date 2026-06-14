package com.inkblue.writer.ai

import com.inkblue.writer.data.AiProfile
import com.inkblue.writer.data.AiProvider
import com.inkblue.writer.data.ReasoningEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AiConfig(
    val provider: AiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val effort: ReasoningEffort = ReasoningEffort.OFF,
)

fun AiProfile.toAiConfig() = AiConfig(
    provider = provider,
    baseUrl = effectiveBaseUrl(),
    apiKey = apiKey,
    model = effectiveModel(),
    effort = effort,
)

/** One chat turn: role is "user" or "assistant". */
data class ChatTurn(val role: String, val content: String)

/**
 * Minimal HTTP client for the three supported protocols. Native web search is
 * served by Anthropic's `web_search` tool and Gemini's `google_search` tool;
 * the OpenAI-compatible path has no standard search and ignores the flag.
 * For universal search across every provider, use [TavilyClient] to fetch
 * results and inject them into the prompt before calling generate/chat.
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
            AiProvider.GEMINI -> geminiRun(config, system, history, maxTokens, enableSearch)
        }
    }

    // -------------------- Anthropic --------------------

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
                if (config.effort != ReasoningEffort.OFF) {
                    put("thinking", JSONObject().apply { put("type", "adaptive") })
                    put("output_config", JSONObject().apply { put("effort", anthropicEffort(config.effort)) })
                }
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
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", content)
            })
        }
        val text = collected.toString().trim()
        if (text.isEmpty()) throw IOException("搜索轮次过多且无内容返回，请重试")
        return text
    }

    private fun anthropicEffort(effort: ReasoningEffort) = when (effort) {
        ReasoningEffort.LOW -> "low"
        ReasoningEffort.MEDIUM -> "medium"
        ReasoningEffort.HIGH -> "high"
        ReasoningEffort.OFF -> "low"
    }

    // -------------------- OpenAI-compatible --------------------

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
            if (config.effort != ReasoningEffort.OFF) {
                // Honored by OpenAI o-series and many relays; ignored otherwise.
                put("reasoning_effort", anthropicEffort(config.effort))
            }
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

    // -------------------- Gemini --------------------

    private fun geminiRun(
        config: AiConfig,
        system: String,
        history: List<ChatTurn>,
        maxTokens: Int,
        enableSearch: Boolean,
    ): String {
        val contents = JSONArray()
        history.forEach { turn ->
            contents.put(JSONObject().apply {
                put("role", if (turn.role == "assistant") "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", turn.content)))
            })
        }
        val generationConfig = JSONObject().apply {
            put("maxOutputTokens", maxTokens)
            // thinkingBudget: 0 disables thinking; higher = deeper reasoning.
            put("thinkingConfig", JSONObject().apply {
                put("thinkingBudget", geminiThinkingBudget(config.effort))
            })
        }
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", system)))
            })
            put("contents", contents)
            put("generationConfig", generationConfig)
            if (enableSearch) {
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
        }
        val key = URLEncoder.encode(config.apiKey, "UTF-8")
        val json = post(
            url = config.baseUrl.trimEnd('/') + "/v1beta/models/${config.model}:generateContent?key=$key",
            headers = emptyMap(),
            body = body,
        )
        val candidates = json.optJSONArray("candidates")
            ?: throw IOException(json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Gemini 返回内容为空")
        if (candidates.length() == 0) throw IOException("Gemini 返回内容为空")
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini 返回内容为空")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) throw IOException("Gemini 返回内容为空")
        return text
    }

    private fun geminiThinkingBudget(effort: ReasoningEffort) = when (effort) {
        ReasoningEffort.OFF -> 0
        ReasoningEffort.LOW -> 2048
        ReasoningEffort.MEDIUM -> 8192
        ReasoningEffort.HIGH -> 24576
    }

    // -------------------- shared --------------------

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

/**
 * Tavily web search. Provider-agnostic: fetch results here, then inject the
 * formatted text into any model's prompt so every protocol can "search".
 */
object TavilyClient {

    suspend fun search(apiKey: String, query: String, maxResults: Int = 5): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("query", query.take(400))
                put("max_results", maxResults)
                put("search_depth", "basic")
                put("include_answer", true)
            }
            val conn = URL("https://api.tavily.com/search").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IOException("Tavily HTTP $code：${raw.take(200)}")
                format(JSONObject(raw))
            } finally {
                conn.disconnect()
            }
        }

    private fun format(json: JSONObject): String = buildString {
        val answer = json.optString("answer")
        if (answer.isNotBlank()) {
            appendLine("摘要：$answer")
            appendLine()
        }
        val results = json.optJSONArray("results")
        if (results != null) {
            for (i in 0 until minOf(results.length(), 5)) {
                val r = results.getJSONObject(i)
                appendLine("【${r.optString("title")}】")
                appendLine(r.optString("content").take(500))
                val url = r.optString("url")
                if (url.isNotBlank()) appendLine("来源：$url")
                appendLine()
            }
        }
    }.trim()
}
