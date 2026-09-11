package com.piremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.piremote.app.data.ConnectionState
import com.piremote.app.data.SessionRepository
import com.piremote.app.data.StoredSession

@Composable
fun SettingsScreen(
    repository: SessionRepository,
    connection: ConnectionState,
    onDone: () -> Unit,
) {
    val store = repository.settingsStore
    var endpoints by remember { mutableStateOf(store.endpoints.joinToString("\n")) }
    var token by remember { mutableStateOf(store.deviceToken) }
    var pin by remember { mutableStateOf(store.pinnedSha256.orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "中继配置",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "这些值来自电脑上的 .secrets/relay.json。指纹用于校验自签证书，" +
                "填错会直接连不上（这是故意的）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = endpoints,
            onValueChange = { endpoints = it },
            label = { Text("中继地址（每行一个，按顺序尝试）") },
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it.trim() },
            label = { Text("设备 Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.trim() },
            label = { Text("证书 SHA-256 指纹") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Button(
            onClick = {
                store.endpoints = endpoints.lines()
                store.deviceToken = token
                store.pinnedSha256 = pin
                repository.connect()
                onDone()
            },
            enabled = endpoints.isNotBlank() && token.isNotBlank() && pin.isNotBlank(),
        ) {
            Text("保存并连接")
        }

        ConnectionBanner(connection)
    }
}

@Composable
fun SessionListScreen(
    repository: SessionRepository,
    connection: ConnectionState,
    onOpenSettings: () -> Unit,
) {
    val presets by repository.presets.collectAsState()
    val known by repository.knownSessions.collectAsState()
    val error by repository.lastError.collectAsState()
    val pending by repository.pendingResize.collectAsState()
    val (cols, rows) = pending ?: (80 to 24)

    val running = known.filter { it.alive }
    val ended = known.filterNot { it.alive }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pi Remote",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("刷新") { repository.refresh() }
                SmallButton("设置", onOpenSettings)
            }
        }

        ConnectionBanner(connection)

        if (error != null) {
            ErrorBanner(error!!) { repository.clearError() }
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionTitle("新建会话") }

            if (presets.isEmpty()) {
                item {
                    Hint("电脑端还没有上报可用的会话预设。确认 agent 在线后点「刷新」。")
                }
            }

            items(presets, key = { "preset-${it.id}" }) { preset ->
                Button(
                    onClick = { repository.createSession(preset.id, cols, rows) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(preset.name)
                        Text(preset.cwd, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { SectionTitle("运行中的会话") }
            if (running.isEmpty()) {
                item { Hint("还没有运行中的会话。") }
            }
            items(running, key = { "run-${it.sid}" }) { session ->
                RunningRow(session, repository)
            }

            item { SectionTitle("已中断的会话") }
            if (ended.isEmpty()) {
                item { Hint("会话结束后会出现在这里，可以翻看或按原目录重开。") }
            }
            items(ended, key = { "end-${it.sid}" }) { session ->
                EndedRow(session, repository, cols, rows)
            }
        }
    }
}

@Composable
private fun RunningRow(session: StoredSession, repository: SessionRepository) {
    SessionCard(session) {
        SmallButton("打开") { repository.openSession(session.sid) }
        SmallButton("终止") { repository.killSession(session.sid) }
    }
}

/**
 * An ended session. The PC agent only keeps exited sessions for a short while,
 * so "查看" may legitimately fail; "重开" is the action that always works.
 */
@Composable
private fun EndedRow(
    session: StoredSession,
    repository: SessionRepository,
    cols: Int,
    rows: Int,
) {
    SessionCard(session, subtitleSuffix = " · ${relativeTime(session.lastSeenAt)}") {
        SmallButton("查看") { repository.openSession(session.sid) }
        SmallButton("重开") {
            repository.createSession(
                presetId = session.preset.ifBlank { "ps" },
                cols = cols,
                rows = rows,
                cwd = session.cwd.takeIf { it.isNotBlank() },
            )
        }
        SmallButton("移除") { repository.forgetSession(session.sid) }
    }
}

@Composable
private fun SessionCard(
    session: StoredSession,
    subtitleSuffix: String = "",
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(
                text = session.name.ifBlank { session.sid.take(8) },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = session.cwd + subtitleSuffix,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        SmallButton("知道了", onDismiss)
    }
}

@Composable
private fun ConnectionBanner(connection: ConnectionState) {
    val (text, color) = when (connection) {
        is ConnectionState.Idle -> "未连接" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionState.Connecting -> "正在连接中继…" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionState.Connected ->
            if (connection.agentId != null) {
                "已连接 · 电脑端 ${connection.agentId}" to MaterialTheme.colorScheme.primary
            } else {
                "已连上中继，但电脑端不在线" to MaterialTheme.colorScheme.onSurfaceVariant
            }

        is ConnectionState.Disconnected -> "已断开：${connection.reason}" to MaterialTheme.colorScheme.error
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000} 小时前"
        else -> "${diff / 86_400_000} 天前"
    }
}
