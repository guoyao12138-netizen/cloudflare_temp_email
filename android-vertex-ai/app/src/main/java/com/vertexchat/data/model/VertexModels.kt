package com.vertexchat.data.model

import com.google.gson.annotations.SerializedName

// Request --------------------------------------------------------------------

data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

data class Content(
    val role: String,
    val parts: List<Part>,
)

data class Part(
    val text: String,
)

data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxOutputTokens: Int? = null,
)

// Response -------------------------------------------------------------------

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val usageMetadata: UsageMetadata? = null,
    val promptFeedback: PromptFeedback? = null,
)

data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

data class UsageMetadata(
    @SerializedName("promptTokenCount") val promptTokenCount: Int? = null,
    @SerializedName("candidatesTokenCount") val candidatesTokenCount: Int? = null,
    @SerializedName("totalTokenCount") val totalTokenCount: Int? = null,
)

data class PromptFeedback(
    val blockReason: String? = null,
)

/** Shape of Google API error envelopes: { "error": { "code", "message", "status" } } */
data class ApiErrorEnvelope(
    val error: ApiError? = null,
)

data class ApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
