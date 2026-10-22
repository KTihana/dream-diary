package com.afterglow.diary

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private var captureRequest by mutableStateOf<String?>(null)
    private var practiceRequest by mutableStateOf<String?>(null)
    private var practiceNight by mutableStateOf<String?>(null)
    private lateinit var model: DiaryViewModel
    private fun readRequests(intent: Intent) {
        captureRequest = intent.getStringExtra("capture")
        if (captureRequest != null) getSystemService(android.app.NotificationManager::class.java).cancel(41)
        practiceRequest = intent.getStringExtra(PRACTICE_ROUTE)
        practiceNight = intent.getStringExtra(PRACTICE_NIGHT)?.let { runCatching { java.time.LocalDate.parse(it).toString() }.getOrNull() }
        practiceRequest?.let { PracticeAlarms.opened(this, it, intent.getBooleanExtra("stop_lucid_audio", false)) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequests(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0x00E5DFD7, 0x00E5DFD7),
            navigationBarStyle = SystemBarStyle.light(0x00E5DFD7, 0x00E5DFD7),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        model = ViewModelProvider(this)[DiaryViewModel::class.java]
        readRequests(intent)
        setContent { AfterglowApp(model, captureRequest = captureRequest, onCaptureConsumed = {
            captureRequest = null; intent.removeExtra("capture")
        }, practiceRequest = practiceRequest, practiceNight = practiceNight, onPracticeConsumed = {
            practiceRequest = null; practiceNight = null
            intent.removeExtra(PRACTICE_ROUTE); intent.removeExtra(PRACTICE_NIGHT); intent.removeExtra("stop_lucid_audio")
        }) }
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            model.lucid.refresh()
            runCatching { PracticeAlarms.reschedule(this) }
        }
    }
}
