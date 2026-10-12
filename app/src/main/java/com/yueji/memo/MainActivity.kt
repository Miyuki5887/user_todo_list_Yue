package com.yueji.memo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.memo.data.SettingsStore
import com.yueji.memo.data.WidgetBridge
import com.yueji.memo.ui.AppNav
import com.yueji.memo.ui.theme.YueJiTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by notification taps to jump straight into one to-do. */
        const val EXTRA_FOCUS_TODO = "yueji.focusTodo"
        private const val REQ_NOTIFICATIONS = 1001
    }

    private val focusTodo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        focusTodo.value = intent?.getStringExtra(EXTRA_FOCUS_TODO)
        askForNotifications()

        setContent {
            val settings by SettingsStore.state.collectAsStateWithLifecycle()
            YueJiTheme(themeMode = settings.themeMode) {
                // Any foreground visit is a good moment to refresh the widget.
                LaunchedEffect(Unit) { WidgetBridge.requestRefresh(this@MainActivity) }
                AppNav(
                    focusTodoId = focusTodo.value,
                    onFocusConsumed = { focusTodo.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focusTodo.value = intent.getStringExtra(EXTRA_FOCUS_TODO)
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS,
            )
        }
    }
}
