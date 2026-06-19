package com.vertexchat.data

import com.google.gson.Gson
import com.vertexchat.data.model.OaiChunk
import com.vertexchat.data.model.OaiErrorEnvelope
import com.vertexchat.data.model.OaiMessage
import com.vertexchat.data.model.OaiRequest
import com.vertexchat.data.model.OaiResponse
import com.vertexchat.data.model.OaiStreamToolCall
import com.vertexchat.data.model.OaiTool
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

sealed class OaiEvent {
    data class Text(val delta: String) : OaiEvent()
    data class ToolCallsReady(val calls: List<OaiToolCall>) : OaiEvent()
    data object Done : OaiEvent()
}

class OpenAIClient(@Volatile var http: OkHttpClient, private val gson: Gson = Gson()) {
    fun updateHttp(http: OkHttpClient) { this.http = http }

    /** Non-streaming — used for tool-call continuation turns. */
    suspend fun chat(
        provider: ProviderConfig,
        messages: List<OaiMessage>,
        model: String,
        temperature: Double?,
        maxTokens: Int?,
        tools: List<OaiTool>?,
    ): OaiResponse = withContext(Dispatchers.IO) {
        val req = buildRequest(provider, messages, model, temperature, maxTokens, tools, stream = false)
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(extractError(body, resp.code))
            gson.fromJson(body, OaiResponse::class.java)
        }
    }

    /** Streaming — emits text deltas then tool calls (if any) then Done. */
    fun streamChat(
        provider: ProviderConfig,
        messages: List<OaiMessage>,
        model: String,
        temperature: Double?,
        maxTokens: Int?,
        tools: List<OaiTool>?,
    ): Flow<OaiEvent> = flow {
        val req = buildRequest(provider, messages, model, temperature, maxTokens, tools, stream = true)

        // Accumulate streaming tool calls by index
        val toolCallBuilders = mutableMapOf<Int, MutableToolCallBuilder>()

        withContext(Dispatchers.IO) {
            http.newCall(req).execute()
        }.use { resp ->
            val body = resp.body ?: throw IOException("Empty response")
            if (!resp.isSuccessful) {
                throw IOException(extractError(body.string(), resp.code))
            }

            val source = body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val chunk = runCatching { gson.fromJson(data, OaiChunk::class.java) }.getOrNull()
                    ?: continue
                val choice = chunk.choices?.firstOrNull() ?: continue
                val delta = choice.delta ?: continue

                delta.content?.let { emit(OaiEvent.Text(it)) }

                delta.toolCalls?.forEach { tc ->
                    val b = toolCallBuilders.getOrPut(tc.index) { MutableToolCallBuilder() }
                    tc.id?.let { b.id = it }
                    tc.type?.let { b.type = it }
                    tc.function?.name?.let { b.name += it }
                    tc.function?.arguments?.let { b.args += it }
                }
            }
        }

        if (toolCallBuilders.isNotEmpty()) {
            val calls = toolCallBuilders.entries.sortedBy { it.key }.map { (_, b) ->
                OaiToolCall(
                    id = b.id,
                    type = b.type,
                    function = OaiFunctionCall(name = b.name, arguments = b.args),
                )
            }
            emit(OaiEvent.ToolCallsReady(calls))
        }
        emit(OaiEvent.Done)
    }

    private fun buildRequest(
        provider: ProviderConfig,
        messages: List<OaiMessage>,
        model: String,
        temperature: Double?,
        maxTokens: Int?,
        tools: List<OaiTool>?,
        stream: Boolean,
    ): Request {
        val body = gson.toJson(
            OaiRequest(
                model = model,
                messages = messages,
                stream = stream,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = tools?.ifEmpty { null },
            )
        ).toRequestBody(JSON)

        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            // Copilot requires this header
            .addHeader("Copilot-Integration-Id", "copilot-chat")
            .post(body)
            .build()
    }

    private fun extractError(body: String, code: Int): String {
        val parsed = runCatching {
            gson.fromJson(body, OaiErrorEnvelope::class.java)?.error?.message
        }.getOrNull()
        return parsed?.let { "API error ($code): $it" } ?: "HTTP $code"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private data class MutableToolCallBuilder(
        var id: String = "",
        var type: String = "function",
        var name: String = "",
        var args: String = "",
    )
}
