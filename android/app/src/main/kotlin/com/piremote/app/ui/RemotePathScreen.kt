package com.piremote.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piremote.app.data.ConnectionState
import com.piremote.app.data.ControlMessage
import com.piremote.app.data.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen Remote Computer Directory & Path Explorer.
 * Accessible via the center button in the main bottom navigation bar.
 */
@Composable
fun RemotePathScreen(
    repository: SessionRepository,
    connection: ConnectionState,
    isActive: Boolean = true,
    onBackToHome: () -> Unit,
) {
    val store = repository.settingsStore
    val dirListing by repository.dirListing.collectAsState()
    val presets by repository.presets.collectAsState()
    val pending by repository.pendingResize.collectAsState()
    val (cols, rows) = pending ?: (80 to 24)

    val defaultFallback = presets.firstOrNull { it.id.equals("pi", ignoreCase = true) }?.cwd?.takeIf { it.isNotBlank() }
        ?: presets.firstOrNull()?.cwd?.takeIf { it.isNotBlank() }
        ?: ""

    var currentPath by remember { mutableStateOf(sanitizeRemotePath(store.quickLaunchCwd.ifBlank { defaultFallback })) }
    var searchQuery by remember { mutableStateOf("") }
    var showLaunchOptions by remember { mutableStateOf(false) }
    var justSavedDefault by remember { mutableStateOf(false) }

    // Adopt preset cwd if store has no default and current path is blank
    LaunchedEffect(defaultFallback) {
        if (store.quickLaunchCwd.isBlank() && currentPath.isBlank() && defaultFallback.isNotBlank()) {
            currentPath = sanitizeRemotePath(defaultFallback)
        }
    }

    // Always fetch directory listing whenever path changes or connection becomes ready
    LaunchedEffect(currentPath, connection) {
        if (connection is ConnectionState.Connected) {
            repository.requestDirListing(currentPath)
        }
    }

    // When the screen becomes active (user navigated to this screen), ensure listing is loaded
    LaunchedEffect(isActive) {
        if (isActive && connection is ConnectionState.Connected) {
            val listingPath = dirListing?.path?.let { sanitizeRemotePath(it) }
            val normCurrent = sanitizeRemotePath(currentPath)
            if (dirListing == null || listingPath != normCurrent) {
                repository.requestDirListing(currentPath)
            }
        }
    }

    val focusManager = LocalFocusManager.current

    fun navigate(target: String) {
        focusManager.clearFocus()
        currentPath = sanitizeRemotePath(target)
        searchQuery = ""
        justSavedDefault = false
    }

    fun navigateUp() {
        focusManager.clearFocus()
        val parent = parentDir(currentPath)
        if (parent != null) {
            navigate(parent)
        }
    }

    // Breadcrumbs list
    val breadcrumbs = remember(currentPath) {
        val norm = sanitizeRemotePath(currentPath)
        if (norm.isBlank()) {
            listOf("我的电脑" to "")
        } else {
            val parts = norm.split('/')
            val list = mutableListOf("我的电脑" to "")
            var acc = ""
            for (part in parts) {
                if (part.isBlank()) continue
                acc = if (acc.isEmpty()) part else "$acc/$part"
                list.add(part to acc)
            }
            list
        }
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var previewingFilePath by remember { mutableStateOf<String?>(null) }
    var selectedFileForAction by remember { mutableStateOf<String?>(null) }
    var pendingSaveLocalFilePath by remember { mutableStateOf<String?>(null) }
    val fileContent by repository.fileContent.collectAsState()

    // Download and save file listener
    LaunchedEffect(fileContent, pendingSaveLocalFilePath) {
        val savePath = pendingSaveLocalFilePath ?: return@LaunchedEffect
        val content = fileContent ?: return@LaunchedEffect
        if (content.path == savePath) {
            pendingSaveLocalFilePath = null
            if (content.error != null) {
                Toast.makeText(context, "下载失败: ${content.error}", Toast.LENGTH_LONG).show()
            } else {
                val fileName = cleanDirDisplayName(savePath, false)
                val ok = saveFileToLocal(context, fileName, content.content)
                if (ok) {
                    Toast.makeText(context, "已保存至手机下载目录: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isDriveRoot = currentPath.isBlank()
    val rawDirs = dirListing?.dirs ?: emptyList()
    val rawFiles = dirListing?.files ?: emptyList()
    val filteredDirs = remember(rawDirs, searchQuery, currentPath) {
        if (searchQuery.isBlank()) rawDirs
        else {
            val q = searchQuery.trim()
            rawDirs.filter { dir ->
                val display = cleanDirDisplayName(dir, currentPath.isBlank())
                display.contains(q, ignoreCase = true) || dir.contains(q, ignoreCase = true)
            }
        }
    }
    val filteredFiles = remember(rawFiles, searchQuery) {
        if (searchQuery.isBlank()) rawFiles
        else {
            val q = searchQuery.trim()
            rawFiles.filter { file ->
                val display = cleanDirDisplayName(file, false)
                display.contains(q, ignoreCase = true) || file.contains(q, ignoreCase = true)
            }
        }
    }

    val isCurrentDefault = store.quickLaunchCwd.isNotBlank() &&
        normalizePath(store.quickLaunchCwd) == normalizePath(currentPath)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeekColors.DeepCanvas)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // App Hero Header (Apple iOS Large Title style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Apple Folder squircle badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardSurface,
                    border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "电脑路径",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = GeekColors.TextPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GeekColors.BrandAccent.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "远程文件",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                ),
                                color = GeekColors.BrandAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "智能工作台 · 远程电脑文件树与路径浏览",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = GeekColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Connection Status Banner
        ConnectionCapsule(connection, onReconnect = { repository.refresh() })

        // Current Selected Path Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = GeekColors.TerminalCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "当前定位路径",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GeekColors.TextMuted,
                        )
                    }

                    if (isCurrentDefault || justSavedDefault) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GeekColors.NeonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, GeekColors.NeonGreen.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = "默认工作目录 ✓",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = GeekColors.NeonGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Path box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = currentPath.ifBlank { "电脑根目录 (请在下方选择磁盘或文件夹)" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = if (currentPath.isNotBlank()) GeekColors.TerminalCyan else GeekColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Set as Default CWD button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .pressClickEffect(),
                        onClick = {
                            if (currentPath.isNotBlank()) {
                                store.quickLaunchCwd = currentPath
                                justSavedDefault = true
                            }
                        },
                        enabled = currentPath.isNotBlank(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "设为默认目录",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (currentPath.isNotBlank()) GeekColors.TextPrimary else GeekColors.TextMuted,
                            )
                        }
                    }

                    // Launch session here button
                    Button(
                        onClick = { showLaunchOptions = !showLaunchOptions },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeekColors.BrandAccent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(36.dp)
                            .pressClickEffect(),
                    ) {
                        Icon(
                            imageVector = if (showLaunchOptions) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showLaunchOptions) "收起" else "在此目录启动",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                // Launch session mode drawer
                AnimatedVisibility(
                    visible = showLaunchOptions,
                    enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                    exit = fadeOut(tween(90)) + shrinkVertically(tween(90)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val piPreset = presets.find { it.id.equals("pi-gui", ignoreCase = true) }
                            ?: presets.find { it.id.equals("pi", ignoreCase = true) }
                            ?: presets.firstOrNull()

                        if (piPreset != null) {
                            LaunchModeChip(
                                icon = Icons.Default.Chat,
                                title = "图形工作台 (GUI)",
                                tag = "推荐",
                                tagColor = GeekColors.BrandPurple,
                                onClick = {
                                    showLaunchOptions = false
                                    repository.createSession(
                                        presetId = piPreset.id,
                                        cols = cols,
                                        rows = rows,
                                        cwd = currentPath.takeIf { it.isNotBlank() },
                                        startInGui = true,
                                    )
                                },
                            )
                            LaunchModeChip(
                                icon = Icons.Default.Terminal,
                                title = "终端仿真 (PTY)",
                                tag = "原生底层",
                                tagColor = GeekColors.TerminalCyan,
                                onClick = {
                                    showLaunchOptions = false
                                    repository.createSession(
                                        presetId = piPreset.id,
                                        cols = cols,
                                        rows = rows,
                                        cwd = currentPath.takeIf { it.isNotBlank() },
                                        startInGui = false,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // Breadcrumbs Row
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                breadcrumbs.forEachIndexed { index, (name, path) ->
                    val isLast = index == breadcrumbs.lastIndex
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLast) GeekColors.BrandAccentGlow else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { navigate(path) },
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = if (isLast) GeekColors.BrandAccent else GeekColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    if (!isLast) {
                        Text(
                            text = "/",
                            color = GeekColors.TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 1.dp),
                        )
                    }
                }
            }
        }

        // Subdirectories & Files List Card (Integrated with Search)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isEmpty = filteredDirs.isEmpty() && filteredFiles.isEmpty()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Compact Search Filter Bar (scrolls together with directories and files)
                item(key = "search_bar") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(
                            0.8.dp,
                            if (searchQuery.isNotEmpty()) GeekColors.BrandAccent else GeekColors.BorderSubtle,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (searchQuery.isNotEmpty()) GeekColors.BrandAccent else GeekColors.TextMuted,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索当前目录下的文件与文件夹...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = GeekColors.TextMuted,
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = GeekColors.TextPrimary,
                                        fontSize = 12.sp,
                                    ),
                                    cursorBrush = SolidColor(GeekColors.TerminalCyan),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空搜索",
                                    tint = GeekColors.TextMuted,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable {
                                            searchQuery = ""
                                            focusManager.clearFocus()
                                        },
                                )
                            }
                        }
                    }
                }

                // Back to parent directory item
                if (currentPath.isNotBlank()) {
                    item(key = "parent_nav") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GeekColors.CardElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { navigateUp() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "↰",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GeekColors.TerminalCyan,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = ".. 返回上一级目录",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = GeekColors.TerminalCyan,
                                )
                            }
                        }
                    }
                }

                // Status messages (loading / empty) or content items
                if (dirListing == null) {
                    item(key = "loading_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (connection is ConnectionState.Connecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = GeekColors.TerminalCyan,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "正在连接中继与远程电脑...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GeekColors.TextMuted,
                                    )
                                } else if (connection is ConnectionState.Disconnected) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = GeekColors.RoseError,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "未连接至远程电脑 (${(connection as ConnectionState.Disconnected).reason.ifBlank { "已断开" }})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GeekColors.RoseError,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = GeekColors.TerminalCyan,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "正在读取远程电脑目录与文件...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GeekColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                } else if (isEmpty) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "未搜索到匹配的文件夹或文件" else "此目录下没有子文件夹与文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        }
                    }
                } else {
                    // Section title for Folders if files are also present
                    if (filteredDirs.isNotEmpty() && filteredFiles.isNotEmpty()) {
                        item(key = "header_dirs") {
                            PathSectionHeader(title = "文件夹", count = filteredDirs.size)
                        }
                    }

                    // Directory items
                    items(filteredDirs, key = { "dir_$it" }) { dirEntry ->
                        val isDrive = isDriveRoot || (dirEntry.length <= 3 && dirEntry.contains(":"))
                        val displayName = cleanDirDisplayName(dirEntry, isDriveRoot)
                        val target = resolveNavigationPath(currentPath, dirEntry)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { navigate(target) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isDrive) Icons.Default.Storage else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isDrive) GeekColors.BrandAccent else GeekColors.TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isDrive) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = if (isDrive) FontFamily.Monospace else FontFamily.Default,
                                    ),
                                    color = GeekColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "▸",
                                    color = GeekColors.TextMuted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    // Section title for Files
                    if (filteredFiles.isNotEmpty()) {
                        item(key = "header_files") {
                            PathSectionHeader(title = "文件", count = filteredFiles.size)
                        }
                    }

                    // File items
                    items(filteredFiles, key = { "file_$it" }) { fileEntry ->
                        val displayName = cleanDirDisplayName(fileEntry, false)
                        val target = resolveNavigationPath(currentPath, fileEntry)
                        val fileInfo = getFileTypeInfo(displayName)
                        val ext = displayName.substringAfterLast('.', "").uppercase().take(5)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedFileForAction = target
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = fileInfo.icon,
                                    contentDescription = null,
                                    tint = fileInfo.color,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = GeekColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )

                                if (ext.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = fileInfo.color.copy(alpha = 0.12f),
                                    ) {
                                        Text(
                                            text = ext,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                            ),
                                            color = fileInfo.color,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }

                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "操作选项",
                                    tint = GeekColors.TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // File Action Options Modal Dialog (Choose View or Save to Local)
    if (selectedFileForAction != null) {
        val targetPath = selectedFileForAction!!
        val targetFileName = cleanDirDisplayName(targetPath, false)
        FileActionDialog(
            filePath = targetPath,
            onDismiss = { selectedFileForAction = null },
            onView = {
                selectedFileForAction = null
                previewingFilePath = targetPath
                repository.requestFileContent(targetPath)
            },
            onSaveToLocal = {
                selectedFileForAction = null
                val cached = fileContent
                if (cached != null && cached.path == targetPath && cached.error == null) {
                    val ok = saveFileToLocal(context, targetFileName, cached.content)
                    if (ok) {
                        Toast.makeText(context, "已保存至手机下载目录: $targetFileName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    pendingSaveLocalFilePath = targetPath
                    repository.requestFileContent(targetPath)
                    Toast.makeText(context, "正在下载 $targetFileName ...", Toast.LENGTH_SHORT).show()
                }
            },
            onCopyPath = {
                clipboardManager.setText(AnnotatedString(targetPath))
                Toast.makeText(context, "已复制完整路径到剪贴板", Toast.LENGTH_SHORT).show()
                selectedFileForAction = null
            },
        )
    }

    // File Content Preview Modal Dialog
    if (previewingFilePath != null) {
        FileContentPreviewDialog(
            filePath = previewingFilePath!!,
            fileContent = fileContent,
            repository = repository,
            onDismiss = {
                previewingFilePath = null
                repository.clearFileContent()
            },
            onSaveToLocal = { path, content ->
                val fileName = cleanDirDisplayName(path, false)
                val ok = saveFileToLocal(context, fileName, content)
                if (ok) {
                    Toast.makeText(context, "已保存至手机下载目录: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                }
            },
            onLaunchHere = { dir ->
                previewingFilePath = null
                repository.clearFileContent()
                currentPath = dir
                showLaunchOptions = true
            },
            onSetDefaultCwd = { dir ->
                store.quickLaunchCwd = dir
                justSavedDefault = true
            }
        )
    }
}

@Composable
private fun LaunchModeChip(
    icon: ImageVector,
    title: String,
    tag: String,
    tagColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GeekColors.CardElevated,
        border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tagColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = GeekColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = tagColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                    ),
                    color = tagColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Strips drive letters and parent directory paths from a folder entry for UI display.
 * When at root, displays the drive letter (e.g. "C:").
 * When browsing inside a drive (e.g. C: or D:), displays only the base directory name (e.g. "Windows", "Program Files").
 */
internal fun cleanDirDisplayName(entry: String, isRoot: Boolean): String {
    val trimmed = entry.trim().trimEnd('\\', '/')
    if (isRoot || trimmed.matches(Regex("^[a-zA-Z]:$"))) {
        return trimmed.take(2).uppercase()
    }
    val name = trimmed.substringAfterLast('\\').substringAfterLast('/')
    return if (name.isBlank()) trimmed else name
}

/**
 * Resolves the target path when clicking a folder item.
 * Handles both relative directory names ("Windows") and agent full paths ("C:\\Windows", "C:/Windows"),
 * preventing duplicate drive letters like "C:/C:\\Windows".
 */
internal fun resolveNavigationPath(currentPath: String, entry: String): String {
    val cleanEntry = sanitizeRemotePath(entry)
    val isAbsolute = cleanEntry.matches(Regex("^[a-zA-Z]:.*")) || cleanEntry.startsWith("/")
    if (isAbsolute) {
        return cleanEntry
    }
    if (currentPath.isBlank()) {
        return cleanEntry
    }
    val cleanCurrent = sanitizeRemotePath(currentPath)
    return "$cleanCurrent/${cleanEntry.trimStart('/')}"
}

/**
 * Sanitizes and normalizes path strings:
 * - Replaces backslashes with forward slashes
 * - Fixes duplicated drive prefixes (e.g. "C:/C:\\Windows" -> "C:/Windows")
 * - Trims trailing slashes
 */
internal fun sanitizeRemotePath(p: String): String {
    var norm = p.trim().replace('\\', '/')
    while (norm.matches(Regex("^[a-zA-Z]:/[a-zA-Z]:.*"))) {
        norm = norm.substring(3)
    }
    return norm.trimEnd('/')
}

private fun parentDir(p: String): String? {
    val norm = sanitizeRemotePath(p)
    if (norm.isEmpty()) return null
    if (norm.matches(Regex("^[a-zA-Z]:$"))) return ""
    val idx = norm.lastIndexOf('/')
    if (idx < 0) return ""
    if (idx == 0) return if (norm.startsWith("/")) "/" else ""
    return norm.substring(0, idx)
}

private fun normalizePath(p: String): String =
    sanitizeRemotePath(p).lowercase()

internal data class FileTypeInfo(
    val icon: ImageVector,
    val color: Color,
    val category: String,
)

@Composable
internal fun getFileTypeInfo(fileName: String): FileTypeInfo {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "java", "kts" -> FileTypeInfo(Icons.Default.Code, GeekColors.TerminalCyan, "Kotlin/Java")
        "js", "jsx", "ts", "tsx", "mjs", "cjs" -> FileTypeInfo(Icons.Default.Code, GeekColors.BrandPurple, "JavaScript/TS")
        "py", "pyw" -> FileTypeInfo(Icons.Default.Code, GeekColors.NeonGreen, "Python")
        "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift" -> FileTypeInfo(Icons.Default.Code, GeekColors.BrandAccent, "Code")
        "html", "css", "scss", "less", "vue" -> FileTypeInfo(Icons.Default.Code, GeekColors.AmberWarn, "Web")
        "json", "yaml", "yml", "xml", "toml", "env", "properties", "gradle" ->
            FileTypeInfo(Icons.Default.Settings, GeekColors.AmberWarn, "Config")
        "md", "markdown", "txt", "log", "rst", "pdf", "doc", "docx" ->
            FileTypeInfo(Icons.Default.Description, Color(0xFF60A5FA), "Doc")
        "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico" ->
            FileTypeInfo(Icons.Default.Image, Color(0xFFF472B6), "Image")
        "zip", "tar", "gz", "7z", "rar" ->
            FileTypeInfo(Icons.Default.Archive, GeekColors.TextMuted, "Archive")
        "bat", "cmd", "ps1", "sh", "bash", "exe", "msi" ->
            FileTypeInfo(Icons.Default.Terminal, GeekColors.TerminalCyan, "Script")
        else ->
            FileTypeInfo(Icons.Default.InsertDriveFile, GeekColors.TextSecondary, "File")
    }
}

@Composable
private fun PathSectionHeader(title: String, count: Int) {
    SectionHeader(title = title, count = count, isActive = count > 0)
}

@Composable
private fun FileContentPreviewDialog(
    filePath: String,
    fileContent: ControlMessage.FileContent?,
    repository: SessionRepository,
    onDismiss: () -> Unit,
    onSaveToLocal: (String, String) -> Unit,
    onLaunchHere: (String) -> Unit,
    onSetDefaultCwd: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val fileName = cleanDirDisplayName(filePath, false)
    val fileInfo = getFileTypeInfo(fileName)
    val parentDirectory = parentDir(filePath).orEmpty().ifBlank {
        filePath.substringBeforeLast('/', "").ifBlank { "C:" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(1.dp, GeekColors.BorderHighlight),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = fileInfo.color.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = fileInfo.icon,
                                    contentDescription = null,
                                    tint = fileInfo.color,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = GeekColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = filePath,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = GeekColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = GeekColors.CardElevated,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = GeekColors.TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Content View Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GeekColors.DeepCanvas,
                    border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (fileContent == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = GeekColors.TerminalCyan,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "正在读取远端电脑文件内容...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GeekColors.TextMuted,
                                )
                            }
                        }
                    } else if (fileContent.error != null) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "读取失败: ${fileContent.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.RoseError,
                            )
                        }
                    } else {
                        val content = fileContent.content
                        val lines = remember(content) { content.lines() }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(10.dp),
                        ) {
                            items(lines.size) { lineIdx ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = (lineIdx + 1).toString().padStart(3, ' '),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                        ),
                                        color = GeekColors.TextMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Text(
                                        text = lines[lineIdx],
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                        ),
                                        color = GeekColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Copy Content button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pressClickEffect(),
                        onClick = {
                            val contentToCopy = fileContent?.content ?: filePath
                            clipboardManager.setText(AnnotatedString(contentToCopy))
                            Toast.makeText(context, "已复制文件内容到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = GeekColors.TextSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "复制内容",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextPrimary,
                            )
                        }
                    }

                    // Save to local button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(1.dp, GeekColors.BrandAccent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pressClickEffect(),
                        onClick = {
                            val content = fileContent?.content
                            if (!content.isNullOrEmpty()) {
                                onSaveToLocal(filePath, content)
                            } else {
                                Toast.makeText(context, "文件内容为空或正在加载中", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = GeekColors.BrandAccent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "保存本地",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.BrandAccent,
                            )
                        }
                    }

                    // Launch in file's directory button
                    Button(
                        onClick = { onLaunchHere(parentDirectory) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeekColors.BrandAccent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(38.dp)
                            .pressClickEffect(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "以所在目录启动",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

/**
 * File Action Options Modal Dialog:
 * Offers the choice to "View Content" or "Save to Local" (or copy path).
 */
@Composable
private fun FileActionDialog(
    filePath: String,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onSaveToLocal: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val fileName = cleanDirDisplayName(filePath, false)
    val fileInfo = getFileTypeInfo(fileName)
    val ext = fileName.substringAfterLast('.', "").uppercase()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(1.dp, GeekColors.BorderHighlight),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header with File icon, name, path
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = fileInfo.color.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, fileInfo.color.copy(alpha = 0.3f)),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = fileInfo.icon,
                                contentDescription = null,
                                tint = fileInfo.color,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = GeekColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (ext.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = fileInfo.color.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = ext,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                        ),
                                        color = fileInfo.color,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = GeekColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = GeekColors.CardElevated,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = GeekColors.TextSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(GeekColors.BorderSubtle)
                )

                // Option 1: 查看文件内容
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(1.dp, GeekColors.TerminalCyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .pressClickEffect(),
                    onClick = onView,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GeekColors.TerminalCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = GeekColors.TerminalCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "在线查看内容",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextPrimary,
                            )
                            Text(
                                text = "快速预览源码、文档与文本配置",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeekColors.TextMuted,
                            )
                        }
                        Text(
                            text = "▸",
                            color = GeekColors.TerminalCyan,
                            fontSize = 15.sp,
                        )
                    }
                }

                // Option 2: 保存到本地手机
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(1.dp, GeekColors.BrandAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .pressClickEffect(),
                    onClick = onSaveToLocal,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GeekColors.BrandAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = GeekColors.BrandAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "保存到本地手机",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextPrimary,
                            )
                            Text(
                                text = "下载并存入手机「Download」下载目录",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeekColors.TextMuted,
                            )
                        }
                        Text(
                            text = "▸",
                            color = GeekColors.BrandAccent,
                            fontSize = 15.sp,
                        )
                    }
                }

                // Option 3: 复制路径
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .pressClickEffect(),
                    onClick = onCopyPath,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = GeekColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "复制电脑完整路径",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = GeekColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Saves content to the Android device's public Download directory using MediaStore on Android Q+,
 * or falls back to external files dir.
 */
private fun saveFileToLocal(context: Context, fileName: String, content: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                true
            } else {
                val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                fallbackFile.writeText(content, Charsets.UTF_8)
                true
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, fileName)
            targetFile.writeText(content, Charsets.UTF_8)
            true
        }
    } catch (e: Exception) {
        try {
            val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            fallbackFile.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }
}

private fun getMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "txt", "log" -> "text/plain"
        "json" -> "application/json"
        "md", "markdown" -> "text/markdown"
        "html", "htm" -> "text/html"
        "xml" -> "text/xml"
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "cs", "go", "rs", "sh", "bat", "cmd" -> "text/plain"
        else -> "application/octet-stream"
    }
}
