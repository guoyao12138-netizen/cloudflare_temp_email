package com.vertexchat

import android.net.Uri

/**
 * Static configuration for OAuth + Vertex AI.
 *
 * The two things you MUST change before the app will work are [OAUTH_CLIENT_ID]
 * and the matching `appAuthRedirectScheme` in gradle.properties. See README.md
 * for the full Google Cloud setup walkthrough.
 */
object Config {

    /**
     * OAuth 2.0 client ID of type **Android** (or "Desktop") created in the
     * Google Cloud Console for your project. Looks like:
     *   123456789-abcdefg.apps.googleusercontent.com
     */
    const val OAUTH_CLIENT_ID = "816002980526-q6ncbbilgd0senbabdeur6dkphv4s3h4.apps.googleusercontent.com"

    /**
     * Redirect URI handed back to the app after the consent screen. For Google
     * installed-app clients this is the *reversed* client ID as a custom scheme
     * followed by a path. It must match the appAuthRedirectScheme placeholder
     * declared in gradle.properties / app build.gradle.kts.
     */
    val REDIRECT_URI: Uri = Uri.parse(reversedClientId() + ":/oauth2redirect")

    /** Google's OAuth 2.0 endpoints. */
    val AUTH_ENDPOINT: Uri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
    val TOKEN_ENDPOINT: Uri = Uri.parse("https://oauth2.googleapis.com/token")

    /**
     * Scopes requested at sign-in:
     *  - cloud-platform : authorizes calls to the Vertex AI (aiplatform) API.
     *  - openid / email : so we can show who is signed in.
     */
    val SCOPES = listOf(
        "openid",
        "email",
        "https://www.googleapis.com/auth/cloud-platform",
    )

    /** Convert  xyz.apps.googleusercontent.com  ->  com.googleusercontent.apps.xyz */
    private fun reversedClientId(): String {
        val suffix = ".apps.googleusercontent.com"
        val core = OAUTH_CLIENT_ID.removeSuffix(suffix)
        return "com.googleusercontent.apps.$core"
    }
}
