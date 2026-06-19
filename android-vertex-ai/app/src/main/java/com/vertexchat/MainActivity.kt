package com.vertexchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.vertexchat.data.model.ProviderType
import com.vertexchat.ui.chat.ChatScreen
import com.vertexchat.ui.chat.ChatViewModel
import com.vertexchat.ui.mcp.McpScreen
import com.vertexchat.ui.mcp.McpViewModel
import com.vertexchat.ui.providers.ProviderEditScreen
import com.vertexchat.ui.providers.ProvidersScreen
import com.vertexchat.ui.providers.ProvidersViewModel
import com.vertexchat.ui.settings.SettingsScreen
import com.vertexchat.ui.settings.SettingsViewModel
import com.vertexchat.ui.signin.SignInScreen
import com.vertexchat.ui.theme.VertexChatTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private data class SignInUiState(val loading: Boolean = false, val error: String? = null)

sealed class AppScreen {
    data object Chat : AppScreen()
    data object Settings : AppScreen()
    data object Providers : AppScreen()
    data object Mcp : AppScreen()
    data class EditProvider(val providerId: String?) : AppScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private val signInState = MutableStateFlow(SignInUiState())
    private lateinit var authLauncher: ActivityResultLauncher<android.content.Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VertexChatApp
        authManager = app.authManager

        authLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            val data = result.data
            if (data == null) {
                signInState.value = SignInUiState(error = "Sign-in was cancelled")
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val outcome = authManager.handleAuthResult(data)
                signInState.value = if (outcome.isSuccess) SignInUiState()
                else SignInUiState(error = outcome.exceptionOrNull()?.message ?: "Sign-in failed")
            }
        }

        setContent {
            VertexChatTheme {
                AppRoot(
                    app = app,
                    authManager = authManager,
                    signInStateFlow = signInState,
                    onStartSignIn = {
                        signInState.value = SignInUiState(loading = true)
                        try {
                            authLauncher.launch(authManager.createAuthIntent())
                        } catch (e: Exception) {
                            signInState.value = SignInUiState(error = e.message ?: "Could not start sign-in")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    app: VertexChatApp,
    authManager: AuthManager,
    signInStateFlow: MutableStateFlow<SignInUiState>,
    onStartSignIn: () -> Unit,
) {
    val authUi by authManager.uiState.collectAsStateWithLifecycle()
    val signIn by signInStateFlow.collectAsStateWithLifecycle()

    val chatVm: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(app.chatRepository, app.providerStore, app.mcpStore, app.globalStore)
    )
    val activeProvider by chatVm.activeProvider.collectAsStateWithLifecycle()

    // Only require Google sign-in when the active provider is Vertex AI
    val needsSignIn = activeProvider?.type == ProviderType.VERTEX && !authUi.isAuthorized
    if (needsSignIn) {
        SignInScreen(
            loading = signIn.loading,
            error = signIn.error,
            configWarning = if (Config.OAUTH_CLIENT_ID.startsWith("REPLACE"))
                "Setup needed: configure OAuth client ID." else null,
            buildLabel = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            onSignIn = onStartSignIn,
        )
        return
    }

    var stack by remember { mutableStateOf(listOf<AppScreen>(AppScreen.Chat)) }
    val current = stack.last()
    val push: (AppScreen) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    if (stack.size > 1) BackHandler(onBack = pop)

    val providersVm: ProvidersViewModel = viewModel(factory = ProvidersViewModel.Factory(app.providerStore))
    val mcpVm: McpViewModel = viewModel(factory = McpViewModel.Factory(app.mcpStore))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.globalStore))

    when (val screen = current) {
        is AppScreen.Chat -> ChatScreen(
            viewModel = chatVm,
            onOpenSettings = { push(AppScreen.Settings) },
            onOpenMcp = { push(AppScreen.Mcp) },
        )
        is AppScreen.Settings -> SettingsScreen(
            viewModel = settingsVm,
            onBack = pop,
            onOpenProviders = { push(AppScreen.Providers) },
        )
        is AppScreen.Providers -> ProvidersScreen(
            viewModel = providersVm,
            onBack = pop,
            onAdd = { push(AppScreen.EditProvider(null)) },
            onEdit = { id -> push(AppScreen.EditProvider(id)) },
        )
        is AppScreen.EditProvider -> ProviderEditScreen(
            initial = screen.providerId?.let { providersVm.getById(it) },
            onBack = pop,
            onSave = { provider -> providersVm.save(provider) },
        )
        is AppScreen.Mcp -> McpScreen(viewModel = mcpVm, onBack = pop)
    }
}
