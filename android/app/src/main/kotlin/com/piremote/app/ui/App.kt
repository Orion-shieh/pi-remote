package com.piremote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.piremote.app.data.SessionRepository

/**
 * Flat, state-driven navigation.
 *
 * There are only three destinations and they are mutually exclusive, so a
 * navigation library would add a dependency without buying anything.
 */
@Composable
fun App(repository: SessionRepository) {
    val connection by repository.connection.collectAsState()
    val activeSessionId by repository.activeSessionId.collectAsState()
    val title by repository.title.collectAsState()

    var showSettings by remember { mutableStateOf(!repository.settingsStore.isConfigured) }

    // Without this every back press finishes the activity, so leaving a
    // terminal drops you out of the app entirely. The keyboard still consumes
    // the first back press to dismiss itself, which is what you want.
    BackHandler(enabled = showSettings || activeSessionId != null) {
        when {
            showSettings -> showSettings = false
            activeSessionId != null -> repository.detachActive()
        }
    }

    LaunchedEffect(Unit) { repository.connect() }

    when {
        showSettings -> SettingsScreen(repository, connection) { showSettings = false }

        activeSessionId != null -> TerminalScreen(
            repository = repository,
            sessionId = activeSessionId!!,
            title = title,
            onBack = { repository.detachActive() },
        )

        else -> SessionListScreen(
            repository = repository,
            connection = connection,
            onOpenSettings = { showSettings = true },
        )
    }
}
