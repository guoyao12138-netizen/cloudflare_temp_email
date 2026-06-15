package com.vertexchat

import android.app.Application
import com.vertexchat.auth.AuthManager
import com.vertexchat.data.SettingsStore
import com.vertexchat.data.VertexAiClient
import com.vertexchat.data.VertexAiRepository

/**
 * Tiny hand-rolled service locator. Keeps the sample dependency-injection-free
 * while still giving every screen a single shared [AuthManager] and repository.
 */
class VertexChatApp : Application() {

    lateinit var authManager: AuthManager
        private set

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var vertexRepository: VertexAiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(this)
        settingsStore = SettingsStore(this)
        vertexRepository = VertexAiRepository(authManager, VertexAiClient())
    }
}
