package com.inkblue.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.inkblue.desktop.data.Store
import com.inkblue.desktop.ui.App
import com.inkblue.desktop.ui.InkTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "墨蓝写作 InkBlue Writer",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        val data by Store.data.collectAsState()
        InkTheme(darkTheme = data.settings.dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                App()
            }
        }
    }
}
