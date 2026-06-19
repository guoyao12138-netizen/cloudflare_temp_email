package com.vertexchat.data.model

import com.google.gson.annotations.SerializedName

data class OaiRequest(
    val model: String,
    val messages: List<OaiMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val tools: List<OaiTool>? = null,
)

data class OaiMessage(
    val role: String,
    val content: String?,
    @SerializedName("tool_calls") val toolCalls: List<OaiToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
)

data class OaiTool(
    val type: String = "function",
    val function: OaiFunctionDef,
)

data class OaiFunctionDef(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>,
)

data class OaiToolCall(
    val id: String,
    val type: String = "function",
    val function: OaiFunctionCall,
)

data class OaiFunctionCall(
    val name: String,
    val arguments: String,
)

// Non-streaming response
data class OaiResponse(
    val choices: List<OaiChoice>?,
    val usage: OaiUsage?,
)

data class OaiChoice(
    val message: OaiMessage?,
    @SerializedName("finish_reason") val finishReason: String?,
)

data class OaiUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int? = null,
    @SerializedName("completion_tokens") val completionTokens: Int? = null,
)

// Streaming
data class OaiChunk(
    val choices: List<OaiChunkChoice>?,
)

data class OaiChunkChoice(
    val delta: OaiDelta?,
    @SerializedName("finish_reason") val finishReason: String?,
)

data class OaiDelta(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<OaiStreamToolCall>? = null,
)

data class OaiStreamToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: OaiStreamFunction? = null,
)

data class OaiStreamFunction(
    val name: String? = null,
    val arguments: String? = null,
)

data class OaiErrorEnvelope(
    val error: OaiErrorDetail?,
)

data class OaiErrorDetail(
    val message: String?,
    val type: String?,
    val code: String?,
)
