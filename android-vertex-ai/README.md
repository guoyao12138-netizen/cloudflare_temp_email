# Vertex Chat — Android App

A Kotlin + Jetpack Compose Android app that lets you **sign in with your Google account** (OAuth 2.0, cloud-platform scope) and chat with Gemini models running in **your own Vertex AI project** — no API keys bundled in the APK.

This is the Android equivalent of `gcloud auth application-default login` + Vertex AI ADC access, implemented as a proper mobile OAuth flow (Authorization Code + PKCE via AppAuth).

---

## Quick-start (one-time setup)

### 1. Enable Vertex AI in your GCP project

In the [Google Cloud Console](https://console.cloud.google.com/):
```
APIs & Services → Enable APIs → "Vertex AI API" → Enable
```

### 2. Create an OAuth 2.0 client ID

```
APIs & Services → Credentials → Create Credentials → OAuth client ID
Application type: Android
Package name: com.vertexchat
SHA-1 certificate fingerprint: (see below)
```

Get the debug SHA-1:
```bash
keytool -list -v -keystore ~/.android/debug.keystore \
        -alias androiddebugkey -storepass android -keypass android \
  | grep SHA1
```

### 3. Configure the app — two files to edit

**`app/src/main/java/com/vertexchat/Config.kt`**
```kotlin
const val OAUTH_CLIENT_ID = "YOUR_CLIENT_ID.apps.googleusercontent.com"
```

**`gradle.properties`**  
Replace the scheme with the *reversed* client ID (everything before `.apps.googleusercontent.com`, reversed by dot-segment, prefixed with `com.googleusercontent.apps.`):
```properties
# e.g. client ID = 123456-abc.apps.googleusercontent.com
# → reversed = com.googleusercontent.apps.123456-abc
appAuthRedirectScheme=com.googleusercontent.apps.YOUR_CLIENT_ID_REVERSED
```

### 4. Build & run

```bash
cd android-vertex-ai
./gradlew assembleDebug
# then install via adb or Android Studio
```

---

## First run

1. Tap **Sign in with Google** → consent screen appears in a Custom Tab.
2. Grant the **"See, edit, configure, and delete your Google Cloud data"** permission.
3. After redirect back to the app, tap the **Settings** (⚙) icon in the top bar.
4. Enter:
   - **GCP Project ID** — your project ID (e.g. `my-project-12345`)
   - **Location / Region** — where Vertex AI is deployed (e.g. `us-central1`)
   - **Model** — e.g. `gemini-2.0-flash-001` or `gemini-1.5-pro-002`
5. Tap **Save** → start chatting.

---

## Architecture

```
MainActivity
  └─ AppRoot (Compose)
       ├─ SignInScreen       ← OAuth launch via AppAuth
       ├─ ChatScreen         ← Conversation UI
       └─ SettingsScreen     ← Project / model config

AuthManager          ← wraps AppAuth, persists AuthState, refreshes tokens
VertexAiRepository   ← maps chat turns → generateContent request
VertexAiClient       ← OkHttp REST call to {location}-aiplatform.googleapis.com
SettingsStore        ← DataStore-persisted project/model config
```

### Why not literal ADC?

Application Default Credentials use credential files and the GCE metadata server — neither exists on Android. This app achieves the same goal (your Google identity → your GCP project → Vertex AI models) using the standard mobile OAuth 2.0 Authorization Code + PKCE flow. The access token it obtains has the same `cloud-platform` scope as ADC, so Vertex AI accepts it identically.

---

## Supported models

Any model available in your project under the publisher path `/publishers/google/models/{model}`. Examples:

| Model ID | Notes |
|---|---|
| `gemini-2.0-flash-001` | Fast, good default |
| `gemini-2.0-pro-exp-02-05` | Most capable Gemini 2.0 |
| `gemini-1.5-pro-002` | Long context |
| `gemini-1.5-flash-002` | Speed/cost optimized |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Setup needed" message on sign-in screen | Set `OAUTH_CLIENT_ID` in `Config.kt` and `appAuthRedirectScheme` in `gradle.properties` |
| Sign-in opens browser but never returns | The redirect scheme doesn't match — double-check `appAuthRedirectScheme` is the exact reversed client ID |
| `403 PERMISSION_DENIED` | Vertex AI API not enabled, or your account doesn't have `roles/aiplatform.user` on the project |
| `404` on first message | Wrong project ID, location, or model name |
| Token expired errors after a day | The app auto-refreshes via the stored refresh token; if it fails, sign out and sign in again |
