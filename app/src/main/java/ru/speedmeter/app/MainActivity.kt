package ru.speedmeter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.speedmeter.app.data.SpeedStore
import ru.speedmeter.app.ui.MeasureScreen
import ru.speedmeter.app.ui.rememberMeasureController
import ru.speedmeter.app.ui.theme.SpeedmeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SpeedmeterTheme { Speedmeter() } }
    }
}

@Composable
private fun Speedmeter() {
    val context = LocalContext.current
    val store = remember { SpeedStore.get(context) }
    val controller = rememberMeasureController(store)
    MeasureScreen(
        state = controller.state,
        onStart = controller.start,
        onStop = controller.stop,
    )
}
