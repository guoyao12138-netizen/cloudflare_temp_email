package com.vertexchat.auth

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.vertexchat.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lightweight snapshot of the auth state for the UI to observe. */
data class AuthUiState(
    val isAuthorized: Boolean = false,
    val email: String? = null,
)

/**
 * Wraps AppAuth to perform the OAuth 2.0 Authorization Code + PKCE flow against
 * Google, persist the resulting [AuthState], and hand out fresh access tokens
 * (auto-refreshing when expired) for the Vertex AI calls.
 */
class AuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vertexchat_auth", Context.MODE_PRIVATE)
    private val authService = AuthorizationService(appContext)

    private val serviceConfig = AuthorizationServiceConfiguration(
        Config.AUTH_ENDPOINT,
        Config.TOKEN_ENDPOINT,
    )

    private var authState: AuthState = loadState()

    private val _uiState = MutableStateFlow(currentUi())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isAuthorized: Boolean get() = authState.isAuthorized

    /** Build the intent that launches the system browser / Custom Tab for consent. */
    fun createAuthIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            Config.OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Config.REDIRECT_URI,
        )
            .setScopes(Config.SCOPES)
            // prompt=consent forces Google to return a refresh token. It is a
            // reserved OAuth param, so AppAuth requires the dedicated builder
            // method rather than setAdditionalParameters().
            .setPromptValues(AuthorizationRequest.Prompt.CONSENT)
            // access_type=offline is a Google-specific extension (not reserved),
            // so it goes through additional parameters.
            .setAdditionalParameters(
                mapOf("access_type" to "offline")
            )
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Handle the redirect intent, then exchange the auth code for tokens. */
    suspend fun handleAuthResult(data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        authState.update(response, exception)

        if (response == null) {
            persist()
            return Result.failure(exception ?: IllegalStateException("Authorization cancelled"))
        }

        return try {
            exchangeCodeForTokens(response)
            persist()
            Result.success(Unit)
        } catch (e: Exception) {
            persist()
            Result.failure(e)
        }
    }

    private suspend fun exchangeCodeForTokens(response: AuthorizationResponse) =
        suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                authState.update(tokenResponse, ex)
                if (tokenResponse != null) {
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(ex ?: IllegalStateException("Token exchange failed"))
                }
            }
        }

    /**
     * Returns a valid access token, transparently refreshing it if the current
     * one is expired. Throws if the user is not (or no longer) authorized.
     */
    suspend fun freshAccessToken(): String = suspendCancellableCoroutine { cont ->
        authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (accessToken != null) {
                cont.resume(accessToken)
            } else {
                cont.resumeWithException(
                    ex ?: IllegalStateException("Not signed in — please sign in again")
                )
            }
        }
    }

    fun signOut() {
        authState = AuthState()
        persist()
    }

    fun dispose() = authService.dispose()

    // --- internals -----------------------------------------------------------

    private fun currentUi() = AuthUiState(
        isAuthorized = authState.isAuthorized,
        email = decodeEmail(),
    )

    /** Pull the `email` claim out of the ID token (no signature check needed for display). */
    private fun decodeEmail(): String? {
        val idToken = authState.idToken ?: return null
        val parts = idToken.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            )
            JSONObject(payload).optString("email").ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY_STATE, authState.jsonSerializeString()).apply()
        _uiState.value = currentUi()
    }

    private fun loadState(): AuthState {
        val json = prefs.getString(KEY_STATE, null) ?: return AuthState()
        return try {
            AuthState.jsonDeserialize(json)
        } catch (e: JSONException) {
            AuthState()
        }
    }

    private companion object {
        const val KEY_STATE = "state"
    }
}
