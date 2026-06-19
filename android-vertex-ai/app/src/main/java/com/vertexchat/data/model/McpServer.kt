package com.vertexchat.data.model

import java.util.UUID

data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val serverId: String,
    val serverName: String,
)
