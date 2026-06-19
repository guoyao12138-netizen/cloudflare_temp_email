package com.vertexchat.data.model

import java.util.UUID

enum class ProviderType { OPENAI, ANTHROPIC, VERTEX }

data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ProviderType = ProviderType.OPENAI,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val activeModel: String = "",
    val enabled: Boolean = true,
    // Vertex AI extra
    val gcpProjectId: String = "",
    val gcpLocation: String = "us-central1",
)

object ProviderPresets {
    val OPENAI = ProviderConfig(
        id = "preset-openai",
        name = "OpenAI",
        type = ProviderType.OPENAI,
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o", "gpt-4o-mini", "o1-mini", "o1-preview", "gpt-4-turbo"),
        activeModel = "gpt-4o",
    )
    val COPILOT = ProviderConfig(
        id = "preset-copilot",
        name = "GitHub Copilot",
        type = ProviderType.OPENAI,
        baseUrl = "https://api.githubcopilot.com",
        models = listOf(
            "gpt-4o", "gpt-4o-mini", "o1-mini",
            "claude-3.5-sonnet", "claude-3.7-sonnet",
        ),
        activeModel = "gpt-4o",
    )
    val ANTHROPIC = ProviderConfig(
        id = "preset-anthropic",
        name = "Anthropic",
        type = ProviderType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        models = listOf(
            "claude-opus-4-8",
            "claude-sonnet-4-6",
            "claude-haiku-4-5-20251001",
            "claude-3-5-sonnet-20241022",
        ),
        activeModel = "claude-opus-4-8",
    )
    val VERTEX = ProviderConfig(
        id = "vertex-default",
        name = "Vertex AI (Google OAuth)",
        type = ProviderType.VERTEX,
        baseUrl = "",
        models = listOf(
            "gemini-2.0-flash-001",
            "gemini-1.5-pro-002",
            "gemini-1.5-flash-002",
        ),
        activeModel = "gemini-2.0-flash-001",
        gcpProjectId = "project-52b70721-e4b4-481e-a04",
        gcpLocation = "us-central1",
    )
    val all = listOf(OPENAI, COPILOT, ANTHROPIC, VERTEX)
}
