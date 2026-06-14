package com.inkblue.writer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkblue.writer.data.AppSettings
import com.inkblue.writer.data.ThemeMode
import com.inkblue.writer.ui.InkNavHost
import com.inkblue.writer.ui.components.FpsOverlay
import com.inkblue.writer.ui.theme.InkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as InkApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            LaunchedEffect(settings.highRefreshRate) {
                applyPreferredRefreshRate(settings.highRefreshRate)
            }
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            InkTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        InkNavHost()
                    }
                    if (settings.showFps) {
                        FpsOverlay(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    /** Hint the system to run at the panel's highest supported refresh rate. */
    private fun applyPreferredRefreshRate(enabled: Boolean) {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val params = window.attributes
        params.preferredRefreshRate = if (enabled) {
            currentDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f
        } else {
            0f
        }
        window.attributes = params
    }
}
