package ru.speedmeter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.speedmeter.app.data.SpeedStore
import ru.speedmeter.app.ui.MeasureScreen
import ru.speedmeter.app.ui.SettingsSheet
import ru.speedmeter.app.ui.rememberMeasureController
import ru.speedmeter.app.ui.theme.SpeedmeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SpeedmeterRoot() }
    }
}

/**
 * Тема задаётся здесь, а не внутри экрана: оформление должно поменяться
 * целиком, включая системные панели, а экран об оформлении ничего не знает.
 */
@Composable
private fun SpeedmeterRoot() {
    val context = LocalContext.current
    val store = remember { SpeedStore.get(context) }
    val saved by store.state.collectAsState()

    SpeedmeterTheme(accentId = saved.accentId, themeMode = saved.themeMode) {
        val controller = rememberMeasureController(store)
        val state = controller.state

        MeasureScreen(state = state, onStart = controller.start, onStop = controller.stop)

        if (state.settingsOpen) {
            SettingsSheet(state)
        }
    }
}