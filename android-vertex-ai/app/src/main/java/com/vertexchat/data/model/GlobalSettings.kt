package com.vertexchat.data.model

data class GlobalSettings(
    val activeProviderId: String = "vertex-default",
    val activeModel: String = "gemini-2.0-flash-001",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    // Proxy
    val proxyEnabled: Boolean = false,
    val proxyType: String = "HTTP",     // "HTTP" or "SOCKS5"
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    // Built-in tools
    val searchEnabled: Boolean = false,
    val searchApiKey: String = "",      // Brave Search API key (free tier: 2000 req/month)
    val mcpEnabled: Boolean = false,
)
