package com.vertexchat.data.model

import com.google.gson.annotations.SerializedName

data class ClaudeRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val system: String? = null,
    val tools: List<ClaudeTool>? = null,
)

data class ClaudeMessage(
    val role: String,
    val content: Any,  // String or List<ClaudeBlock>
)

data class ClaudeBlock(
    val type: String,
    // text
    val text: String? = null,
    // tool_use
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any?>? = null,
    // tool_result
    @SerializedName("tool_use_id") val toolUseId: String? = null,
)

data class ClaudeTool(
    val name: String,
    val description: String,
    @SerializedName("input_schema") val inputSchema: Map<String, Any?>,
)

data class ClaudeResponse(
    val id: String? = null,
    val content: List<ClaudeBlock>? = null,
    @SerializedName("stop_reason") val stopReason: String? = null,
    val model: String? = null,
)

data class ClaudeErrorEnvelope(
    val type: String?,
    val error: ClaudeErrorDetail?,
)

data class ClaudeErrorDetail(
    val type: String?,
    val message: String?,
)
