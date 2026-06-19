package com.vertexchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vertexchat.auth.AuthManager
import com.vertexchat.ui.chat.ChatScreen
import com.vertexchat.ui.chat.ChatViewModel
import com.vertexchat.ui.settings.SettingsScreen
import com.vertexchat.ui.settings.SettingsViewModel
import com.vertexchat.ui.signin.SignInScreen
import com.vertexchat.ui.theme.VertexChatTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private data class SignInState(
    val loading: Boolean = false,
    val error: String? = null,
)

private enum class Screen { Chat, Settings }

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private val signInState = MutableStateFlow(SignInState())
    private lateinit var authLauncher: ActivityResultLauncher<android.content.Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VertexChatApp
        authManager = app.authManager

        authLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data
            if (data == null) {
                signInState.value = SignInState(error = "Sign-in was cancelled")
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val outcome = authManager.handleAuthResult(data)
                signInState.value = if (outcome.isSuccess) {
                    SignInState()
                } else {
                    SignInState(error = outcome.exceptionOrNull()?.message ?: "Sign-in failed")
                }
            }
        }

        setContent {
            VertexChatTheme {
                AppRoot(
                    app = app,
                    authManager = authManager,
                    signInStateFlow = signInState,
                    onStartSignIn = ::startSignIn,
                )
            }
        }
    }

    private fun startSignIn() {
        signInState.value = SignInState(loading = true)
        try {
            authLauncher.launch(authManager.createAuthIntent())
        } catch (e: Exception) {
            signInState.value = SignInState(error = e.message ?: "Could not start sign-in")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // AuthManager lives on the Application, so we don't dispose it here.
    }
}

@Composable
private fun AppRoot(
    app: VertexChatApp,
    authManager: AuthManager,
    signInStateFlow: MutableStateFlow<SignInState>,
    onStartSignIn: () -> Unit,
) {
    val authUi by authManager.uiState.collectAsStateWithLifecycle()
    val signIn by signInStateFlow.collectAsStateWithLifecycle()

    if (!authUi.isAuthorized) {
        val warning = if (Config.OAUTH_CLIENT_ID.startsWith("REPLACE")) {
            "Setup needed: set your OAuth client ID in Config.kt and the redirect " +
                "scheme in gradle.properties (see README.md)."
        } else {
            null
        }
        SignInScreen(
            loading = signIn.loading,
            error = signIn.error,
            configWarning = warning,
            buildLabel = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            onSignIn = onStartSignIn,
        )
        return
    }

    var screen by remember { mutableStateOf(Screen.Chat) }

    when (screen) {
        Screen.Chat -> {
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(app.vertexRepository, app.settingsStore)
            )
            ChatScreen(
                viewModel = chatViewModel,
                email = authUi.email,
                onOpenSettings = { screen = Screen.Settings },
                onSignOut = { authManager.signOut() },
            )
        }

        Screen.Settings -> {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.settingsStore)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { screen = Screen.Chat },
            )
        }
    }
}
