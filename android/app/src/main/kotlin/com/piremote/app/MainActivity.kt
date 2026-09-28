package com.piremote.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.piremote.app.service.TerminalService
import com.piremote.app.ui.App
import com.piremote.app.ui.PiRemoteTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required for the IME inset to reach Compose. targetSdk 35 already
        // forces edge-to-edge on Android 15+, but unless the activity opts in
        // explicitly the keyboard still overlays the content and the terminal
        // never shrinks to fit the visible area.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val repository = (application as PiRemoteApp).repository

        requestNotificationPermissionIfNeeded()
        startConnectionService()

        setContent {
            val themeMode by repository.settingsStore.themeModeFlow.collectAsState()
            val guiFontScale by repository.settingsStore.guiFontScaleFlow.collectAsState()
            PiRemoteTheme(themeMode = themeMode, guiFontScale = guiFontScale) {
                App(repository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as? PiRemoteApp)?.repository?.refresh()
    }

    /**
     * The connection lives in a foreground service so it survives the activity
     * being backgrounded or the screen turning off.
     */
    private fun startConnectionService() {
        val intent = Intent(this, TerminalService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
