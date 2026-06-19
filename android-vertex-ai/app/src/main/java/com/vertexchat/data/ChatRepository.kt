package com.vertexchat.data

import com.google.gson.Gson
import com.vertexchat.auth.AuthManager
import com.vertexchat.data.model.ClaudeBlock
import com.vertexchat.data.model.ClaudeMessage
import com.vertexchat.data.model.ClaudeTool
import com.vertexchat.data.model.Content
import com.vertexchat.data.model.GenerateContentRequest
import com.vertexchat.data.model.GenerationConfig
import com.vertexchat.data.model.GlobalSettings
import com.vertexchat.data.model.McpServerConfig
import com.vertexchat.data.model.OaiMessage
import com.vertexchat.data.model.OaiTool
import com.vertexchat.data.model.OaiFunctionDef
import com.vertexchat.data.model.OaiToolCall
import com.vertexchat.data.model.Part
import com.vertexchat.data.model.ProviderConfig
import com.vertexchat.data.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class UiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,   // "user" or "assistant"
    val content: String,
    val toolInfo: String? = null,   // inline tool call/result summary
    val isStreaming: Boolean = false,
)

class ChatRepository(
    private val openAIClient: OpenAIClient,
    private val anthropicClient: AnthropicClient,
    private val mcpClient: McpClient,
    private val searchTool: SearchTool,
    private val vertexClient: VertexAiClient,
    private val authManager: AuthManager,
    private val gson: Gson = Gson(),
) {
    /**
     * Send [history] (already includes the user's new message) to [provider] and stream
     * back the assistant reply. Each emission is the full accumulated text so far.
     * Tool calls (search / MCP) are executed transparently; their summaries are appended
     * inline so the user can see what the model did.
     */
    fun chat(
        history: List<UiChatMessage>,
        provider: ProviderConfig,
        model: String,
        settings: GlobalSettings,
        mcpServers: List<McpServerConfig>,
    ): Flow<String> = flow {
        val tools = buildToolDefs(settings, mcpServers)

        when (provider.type) {
            ProviderType.OPENAI -> streamOpenAI(history, provider, model, settings, tools)
                .collect { emit(it) }
            ProviderType.ANTHROPIC -> streamAnthropic(history, provider, model, settings, tools)
                .collect { emit(it) }
            ProviderType.VERTEX -> {
                val text = callVertex(history, provider, settings)
                emit(text)
            }
        }
    }

    // ── OpenAI path ───────────────────────────────────────────────────────────

    private fun streamOpenAI(
        history: List<UiChatMessage>,
        provider: ProviderConfig,
        model: String,
        settings: GlobalSettings,
        tools: List<OaiTool>,
    ): Flow<String> = flow {
        var messages = history.toOaiMessages(settings.systemPrompt)
        var accumulated = ""
        var iteration = 0

        while (iteration < 5) {
            var gotToolCalls = false
            var toolCalls: List<OaiToolCall> = emptyList()

            openAIClient.streamChat(
                provider = provider,
                messages = messages,
                model = model,
                temperature = settings.temperature.toDouble(),
                maxTokens = settings.maxTokens,
                tools = tools.ifEmpty { null },
            ).collect { event ->
                when (event) {
                    is OaiEvent.Text -> {
                        accumulated += event.delta
                        emit(accumulated)
                    }
                    is OaiEvent.ToolCallsReady -> {
                        gotToolCalls = true
                        toolCalls = event.calls
                    }
                    is OaiEvent.Done -> {}
                }
            }

            if (!gotToolCalls) break

            // Execute tools and continue
            val assistantMsg = OaiMessage(
                role = "assistant",
                content = accumulated.ifEmpty { null },
                toolCalls = toolCalls,
            )
            messages = messages + assistantMsg

            for (tc in toolCalls) {
                val toolSummary = "\n\n🔧 **${tc.function.name}**(${tc.function.arguments})\n"
                accumulated += toolSummary
                emit(accumulated)

                val result = runCatching {
                    executeOaiTool(tc, settings, findMcpServerForTool(tc.function.name, provider))
                }.getOrElse { "Error: ${it.message}" }

                accumulated += "_→ ${result.take(200)}_\n"
                emit(accumulated)

                messages = messages + OaiMessage(
                    role = "tool",
                    content = result,
                    toolCallId = tc.id,
                )
            }

            accumulated += "\n"
            iteration++
        }
    }

    private suspend fun executeOaiTool(
        tc: OaiToolCall,
        settings: GlobalSettings,
        mcpServer: McpServerConfig?,
    ): String {
        val args = runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(tc.function.arguments, Map::class.java) as Map<String, Any?>
        }.getOrElse { emptyMap() }

        return when (tc.function.name) {
            "web_search" -> {
                val query = args["query"] as? String ?: return "Missing query"
                searchTool.search(query, settings.searchApiKey)
            }
            else -> {
                val server = mcpServer ?: return "Unknown tool: ${tc.function.name}"
                mcpClient.callTool(server, tc.function.name, args)
            }
        }
    }

    // ── Anthropic path ────────────────────────────────────────────────────────

    private fun streamAnthropic(
        history: List<UiChatMessage>,
        provider: ProviderConfig,
        model: String,
        settings: GlobalSettings,
        tools: List<OaiTool>,
    ): Flow<String> = flow {
        var messages = history.toClaudeMessages()
        val claudeTools = tools.map { t ->
            ClaudeTool(
                name = t.function.name,
                description = t.function.description,
                inputSchema = t.function.parameters,
            )
        }
        var accumulated = ""
        var iteration = 0

        while (iteration < 5) {
            var gotToolCalls = false
            var oaiToolCalls: List<OaiToolCall> = emptyList()

            anthropicClient.streamChat(
                provider = provider,
                messages = messages,
                model = model,
                temperature = settings.temperature.toDouble(),
                maxTokens = settings.maxTokens,
                system = settings.systemPrompt.ifBlank { null },
                tools = claudeTools.ifEmpty { null },
            ).collect { event ->
                when (event) {
                    is ClaudeEvent.Text -> {
                        accumulated += event.delta
                        emit(accumulated)
                    }
                    is ClaudeEvent.ToolCallsReady -> {
                        gotToolCalls = true
                        oaiToolCalls = event.calls
                    }
                    is ClaudeEvent.Done -> {}
                }
            }

            if (!gotToolCalls) break

            // Build assistant message with tool_use blocks
            val assistantContent = buildList {
                if (accumulated.isNotBlank()) add(ClaudeBlock(type = "text", text = accumulated))
                oaiToolCalls.forEach { tc ->
                    val input = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        gson.fromJson(tc.function.arguments, Map::class.java) as Map<String, Any?>
                    }.getOrElse { emptyMap() }
                    add(ClaudeBlock(type = "tool_use", id = tc.id, name = tc.function.name, input = input))
                }
            }
            messages = messages + ClaudeMessage(role = "assistant", content = assistantContent)

            // Execute tools and build user tool_result message
            val resultBlocks = mutableListOf<ClaudeBlock>()
            for (tc in oaiToolCalls) {
                val toolSummary = "\n\n🔧 **${tc.function.name}**(${tc.function.arguments})\n"
                accumulated += toolSummary
                emit(accumulated)

                val result = runCatching {
                    executeOaiTool(tc, settings, findMcpServerForTool(tc.function.name, provider))
                }.getOrElse { "Error: ${it.message}" }

                accumulated += "_→ ${result.take(200)}_\n"
                emit(accumulated)

                resultBlocks.add(ClaudeBlock(type = "tool_result", toolUseId = tc.id, text = result))
            }
            messages = messages + ClaudeMessage(role = "user", content = resultBlocks)

            accumulated += "\n"
            iteration++
        }
    }

    // ── Vertex AI path ────────────────────────────────────────────────────────

    private suspend fun callVertex(
        history: List<UiChatMessage>,
        provider: ProviderConfig,
        settings: GlobalSettings,
    ): String {
        val accessToken = authManager.freshAccessToken()
        val contents = history.map { msg ->
            Content(
                role = if (msg.role == "user") "user" else "model",
                parts = listOf(Part(msg.content)),
            )
        }
        val systemInstruction = settings.systemPrompt.takeIf { it.isNotBlank() }
            ?.let { Content(role = "system", parts = listOf(Part(it))) }

        val response = vertexClient.generateContent(
            accessToken = accessToken,
            projectId = provider.gcpProjectId,
            location = provider.gcpLocation,
            model = vertexModel(provider),
            request = GenerateContentRequest(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = GenerationConfig(temperature = settings.temperature),
            ),
        )
        return response.candidates?.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Empty response from Vertex AI")
    }

    private fun vertexModel(provider: ProviderConfig) = provider.activeModel
        .ifBlank { provider.models.firstOrNull() ?: "gemini-2.0-flash-001" }

    // ── Tool building ─────────────────────────────────────────────────────────

    private suspend fun buildToolDefs(
        settings: GlobalSettings,
        mcpServers: List<McpServerConfig>,
    ): List<OaiTool> = buildList {
        if (settings.searchEnabled && settings.searchApiKey.isNotBlank()) {
            add(OaiTool(function = OaiFunctionDef(
                name = "web_search",
                description = "Search the web for current information.",
                parameters = SearchTool.TOOL_DEFINITION,
            )))
        }
        if (settings.mcpEnabled) {
            for (server in mcpServers.filter { it.enabled }) {
                runCatching { mcpClient.listTools(server) }
                    .getOrElse { emptyList() }
                    .forEach { tool ->
                        add(OaiTool(function = OaiFunctionDef(
                            name = tool.name,
                            description = "${tool.description} [MCP: ${server.name}]",
                            parameters = tool.inputSchema,
                        )))
                    }
            }
        }
    }

    // For now, MCP tool→server lookup is by checking if tool name exists in any server.
    // A more robust impl would cache tool→serverId mappings.
    private var _mcpServers: List<McpServerConfig> = emptyList()
    fun updateMcpServers(servers: List<McpServerConfig>) { _mcpServers = servers }

    private fun findMcpServerForTool(toolName: String, provider: ProviderConfig): McpServerConfig? =
        _mcpServers.firstOrNull { it.enabled }

    // ── Message conversion helpers ────────────────────────────────────────────

    private fun List<UiChatMessage>.toOaiMessages(systemPrompt: String): List<OaiMessage> = buildList {
        if (systemPrompt.isNotBlank()) add(OaiMessage(role = "system", content = systemPrompt))
        this@toOaiMessages.forEach { msg ->
            add(OaiMessage(role = msg.role, content = msg.content))
        }
    }

    private fun List<UiChatMessage>.toClaudeMessages(): List<ClaudeMessage> =
        map { msg -> ClaudeMessage(role = msg.role, content = msg.content) }
}
