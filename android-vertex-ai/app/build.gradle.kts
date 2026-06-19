plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vertexchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vertexchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1-promptfix"

        // AppAuth's RedirectUriReceiverActivity reads this placeholder to register
        // the custom-scheme intent filter that catches the OAuth redirect.
        manifestPlaceholders["appAuthRedirectScheme"] =
            (project.findProperty("appAuthRedirectScheme") as String?)
                ?: "com.googleusercontent.apps.REPLACE_WITH_REVERSED_CLIENT_ID"
    }

    signingConfigs {
        // Fixed debug keystore committed to the repo so every build (local or CI)
        // is signed with the SAME key — its SHA-1 stays constant, which is what
        // the Google OAuth Android client must be registered against.
        // SHA-1: 6B:CF:B4:01:56:84:2F:F5:B4:98:31:D6:0F:FA:D5:44:2D:51:F5:1E
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // OAuth 2.0 (Authorization Code + PKCE) against Google's endpoints.
    implementation("net.openid:appauth:0.11.1")

    // HTTP + JSON for the Vertex AI REST calls.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Persist auth state + settings.
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
