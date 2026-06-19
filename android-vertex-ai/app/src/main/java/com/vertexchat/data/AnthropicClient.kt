package com.vertexchat.data

import com.google.gson.Gson
import com.vertexchat.data.model.ClaudeBlock
import com.vertexchat.data.model.ClaudeErrorEnvelope
import com.vertexchat.data.model.ClaudeMessage
import com.vertexchat.data.model.ClaudeRequest
import com.vertexchat.data.model.ClaudeResponse
import com.vertexchat.data.model.ClaudeTool
import com.vertexchat.data.model.OaiToolCall
import com.vertexchat.data.model.OaiFunctionCall
import com.vertexchat.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

sealed class ClaudeEvent {
    data class Text(val delta: String) : ClaudeEvent()
    data class ToolCallsReady(val calls: List<OaiToolCall>) : ClaudeEvent()
    data object Done : ClaudeEvent()
}

class AnthropicClient(@Volatile var http: OkHttpClient, private val gson: Gson = Gson()) {
    fun updateHttp(http: OkHttpClient) { this.http = http }

    fun streamChat(
        provider: ProviderConfig,
        messages: List<ClaudeMessage>,
        model: String,
        temperature: Double?,
        maxTokens: Int,
        system: String?,
        tools: List<ClaudeTool>?,
    ): Flow<ClaudeEvent> = flow {
        val req = buildRequest(provider, messages, model, temperature, maxTokens, system, tools, stream = true)

        data class ToolBuilder(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())
        val toolBuilders = mutableMapOf<Int, ToolBuilder>()
        var currentIndex = 0

        withContext(Dispatchers.IO) { http.newCall(req).execute() }.use { resp ->
            val body = resp.body ?: throw IOException("Empty response")
            if (!resp.isSuccessful) throw IOException(extractError(body.string(), resp.code))

            val source = body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                val obj = runCatching { gson.fromJson(data, Map::class.java) }.getOrNull() ?: continue
                val type = obj["type"] as? String ?: continue

                when (type) {
                    "content_block_start" -> {
                        val block = obj["content_block"] as? Map<*, *> ?: continue
                        val blockType = block["type"] as? String ?: continue
                        val index = (obj["index"] as? Double)?.toInt() ?: 0
                        currentIndex = index
                        if (blockType == "tool_use") {
                            toolBuilders[index] = ToolBuilder(
                                id = block["id"] as? String ?: "",
                                name = block["name"] as? String ?: "",
                            )
                        }
                    }
                    "content_block_delta" -> {
                        val delta = obj["delta"] as? Map<*, *> ?: continue
                        val deltaType = delta["type"] as? String ?: continue
                        val index = (obj["index"] as? Double)?.toInt() ?: currentIndex
                        when (deltaType) {
                            "text_delta" -> {
                                val text = delta["text"] as? String ?: continue
                                emit(ClaudeEvent.Text(text))
                            }
                            "input_json_delta" -> {
                                val partial = delta["partial_json"] as? String ?: continue
                                toolBuilders[index]?.args?.append(partial)
                            }
                        }
                    }
                    "message_stop" -> break
                }
            }
        }

        if (toolBuilders.isNotEmpty()) {
            val calls = toolBuilders.entries.sortedBy { it.key }.map { (_, b) ->
                OaiToolCall(
                    id = b.id,
                    function = OaiFunctionCall(name = b.name, arguments = b.args.toString()),
                )
            }
            emit(ClaudeEvent.ToolCallsReady(calls))
        }
        emit(ClaudeEvent.Done)
    }

    // Non-streaming for tool-call continuations
    suspend fun chat(
        provider: ProviderConfig,
        messages: List<ClaudeMessage>,
        model: String,
        temperature: Double?,
        maxTokens: Int,
        system: String?,
        tools: List<ClaudeTool>?,
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        val req = buildRequest(provider, messages, model, temperature, maxTokens, system, tools, stream = false)
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(extractError(body, resp.code))
            gson.fromJson(body, ClaudeResponse::class.java)
        }
    }

    private fun buildRequest(
        provider: ProviderConfig,
        messages: List<ClaudeMessage>,
        model: String,
        temperature: Double?,
        maxTokens: Int,
        system: String?,
        tools: List<ClaudeTool>?,
        stream: Boolean,
    ): Request {
        val body = gson.toJson(
            ClaudeRequest(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                stream = stream,
                temperature = temperature,
                system = system?.ifBlank { null },
                tools = tools?.ifEmpty { null },
            )
        ).toRequestBody(JSON)

        val url = provider.baseUrl.trimEnd('/') + "/v1/messages"

        return Request.Builder()
            .url(url)
            .addHeader("x-api-key", provider.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
    }

    private fun extractError(body: String, code: Int): String {
        val parsed = runCatching {
            gson.fromJson(body, ClaudeErrorEnvelope::class.java)?.error?.message
        }.getOrNull()
        return parsed?.let { "Anthropic error ($code): $it" } ?: "HTTP $code"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
