package com.vertexchat.data

import com.vertexchat.auth.AuthManager
import com.vertexchat.data.model.Content
import com.vertexchat.data.model.GenerateContentRequest
import com.vertexchat.data.model.GenerationConfig
import com.vertexchat.data.model.Part

/** One chat turn. role is "user" or "model" (Vertex/Gemini convention). */
data class ChatTurn(
    val role: String,
    val text: String,
)

/**
 * Bridges the chat UI to Vertex AI: grabs a fresh access token, maps the chat
 * history into the request shape, and returns the model's reply text.
 */
class VertexAiRepository(
    private val authManager: AuthManager,
    private val client: VertexAiClient,
) {
    suspend fun sendMessage(
        history: List<ChatTurn>,
        settings: AppSettings,
    ): String {
        require(settings.isComplete) { "Set your project, location and model in Settings first." }

        val accessToken = authManager.freshAccessToken()

        val contents = history.map { turn ->
            Content(role = turn.role, parts = listOf(Part(turn.text)))
        }

        val systemInstruction = settings.systemInstruction
            .takeIf { it.isNotBlank() }
            ?.let { Content(role = "system", parts = listOf(Part(it))) }

        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(temperature = settings.temperature),
        )

        val response = client.generateContent(
            accessToken = accessToken,
            projectId = settings.projectId,
            location = settings.location,
            model = settings.model,
            request = request,
        )

        val candidate = response.candidates?.firstOrNull()
        val text = candidate?.content?.parts
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }

        if (text != null) return text

        // No text came back — surface why (safety block, empty finish reason, etc.).
        response.promptFeedback?.blockReason?.let {
            throw IllegalStateException("Request blocked: $it")
        }
        candidate?.finishReason?.let {
            throw IllegalStateException("No content returned (finishReason=$it)")
        }
        throw IllegalStateException("Empty response from the model")
    }
}
