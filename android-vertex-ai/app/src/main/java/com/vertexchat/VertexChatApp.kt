package com.vertexchat

import android.app.Application
import com.google.gson.Gson
import com.vertexchat.auth.AuthManager
import com.vertexchat.data.AnthropicClient
import com.vertexchat.data.ChatRepository
import com.vertexchat.data.GlobalSettingsStore
import com.vertexchat.data.HttpClientFactory
import com.vertexchat.data.McpClient
import com.vertexchat.data.McpStore
import com.vertexchat.data.OpenAIClient
import com.vertexchat.data.ProviderStore
import com.vertexchat.data.SearchTool
import com.vertexchat.data.VertexAiClient
import com.vertexchat.data.model.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VertexChatApp : Application() {

    lateinit var authManager: AuthManager
        private set
    lateinit var providerStore: ProviderStore
        private set
    lateinit var mcpStore: McpStore
        private set
    lateinit var globalStore: GlobalSettingsStore
        private set
    lateinit var chatRepository: ChatRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val gson = Gson()

        authManager = AuthManager(this)
        providerStore = ProviderStore(this)
        mcpStore = McpStore(this)
        globalStore = GlobalSettingsStore(this)

        // Build HTTP client (proxy config loaded lazily on first use)
        val initialHttp = HttpClientFactory.build(GlobalSettings())
        val vertexClient = VertexAiClient(gson)
        val openAIClient = OpenAIClient(initialHttp, gson)
        val anthropicClient = AnthropicClient(initialHttp, gson)
        val mcpClient = McpClient(initialHttp, gson)
        val searchTool = SearchTool(initialHttp, gson)

        chatRepository = ChatRepository(
            openAIClient = openAIClient,
            anthropicClient = anthropicClient,
            mcpClient = mcpClient,
            searchTool = searchTool,
            vertexClient = vertexClient,
            authManager = authManager,
            gson = gson,
        )

        // Rebuild HTTP clients when proxy settings change
        appScope.launch {
            globalStore.settings.collect { gs ->
                val http = HttpClientFactory.build(gs)
                openAIClient.updateHttp(http)
                anthropicClient.updateHttp(http)
                mcpClient.updateHttp(http)
                searchTool.updateHttp(http)
                vertexClient.updateHttp(http)
            }
        }
    }
}
