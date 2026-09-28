package com.piremote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.piremote.app.data.LinuxEnvironmentManager
import com.piremote.app.data.LinuxEnvironmentStatus
import com.piremote.app.data.ToolchainInfo
import kotlinx.coroutines.launch

/**
 * Metadata for individual Linux base tools.
 */
data class BaseToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val installCommand: String,
    val runCommand: String? = null,
    val isInstalled: Boolean,
    val statusText: String,
    val icon: ImageVector,
)

/**
 * Metadata for predefined Terminal AI Agents.
 */
data class AgentPackageInfo(
    val id: String,
    val name: String,
    val tag: String,
    val tagColor: Color,
    val description: String,
    val installCommand: String,
    val runCommand: String,
    val icon: ImageVector,
)

/**
 * Predefined list of popular Terminal AI Agents for quick installation.
 */
val PREDEFINED_AGENTS = listOf(
    AgentPackageInfo(
        id = "pi",
        name = "Pi Agent",
        tag = "推荐",
        tagColor = Color(0xFF8B5CF6),
        description = "专为终端与编码打造的轻量级自主 Agent，支持 RPC 结构化多轮会话与交互式终端双模式。",
        installCommand = "npm install -g --registry=https://registry.npmmirror.com @earendil-works/pi-coding-agent",
        runCommand = "pi",
        icon = Icons.Default.SmartToy,
    ),
    AgentPackageInfo(
        id = "claude",
        name = "Claude Code",
        tag = "Anthropic",
        tagColor = Color(0xFFF59E0B),
        description = "Anthropic 官方研究级终端 Agent，擅长大型项目架构分析、跨文件协同重构与代码编写。",
        installCommand = "npm install -g --registry=https://registry.npmmirror.com @anthropic-ai/claude-code",
        runCommand = "claude",
        icon = Icons.Default.Bolt,
    ),
    AgentPackageInfo(
        id = "command_code",
        name = "Command Code",
        tag = "自动化",
        tagColor = Color(0xFF06B6D4),
        description = "基于命令行的自然语言自动化与代码生成 Agent，高效自动化终端复杂运维与批处理任务。",
        installCommand = "npm install -g command-code",
        runCommand = "command-code",
        icon = Icons.Default.Code,
    ),
    AgentPackageInfo(
        id = "opencode",
        name = "OpenCode",
        tag = "开源多模型",
        tagColor = Color(0xFF10B981),
        description = "开源终端全模型自主编程 Agent，支持接入本地 Ollama、Claude、OpenAI 及兼容网关。",
        installCommand = "npm install -g opencode-ai",
        runCommand = "opencode",
        icon = Icons.Default.AutoAwesome,
    ),
    AgentPackageInfo(
        id = "codex",
        name = "Codex CLI",
        tag = "OpenAI",
        tagColor = Color(0xFF6366F1),
        description = "OpenAI 命令行终端代码辅助工具，快速以自然语言指令在控制台生成并调试工程代码。",
        installCommand = "npm install -g @openai/codex",
        runCommand = "codex",
        icon = Icons.Default.Terminal,
    ),
    AgentPackageInfo(
        id = "aider",
        name = "Aider",
        tag = "Git 结对",
        tagColor = Color(0xFFF43F5E),
        description = "终端 AI 结对编程助手，自动结合当前 Git 仓库上下文和差异完成代码修改并提交 Commit。",
        installCommand = "python3 -m pip install -U aider-chat",
        runCommand = "aider",
        icon = Icons.Default.Build,
    ),
)

/**
 * Universal Multi-Page Agent & Linux Environment Toolchain Configuration Dialog.
 * Page 1: GNU Bash & Linux Base Subsystem Environment Configuration (individual tool cards)
 * Page 2: Terminal AI Agents Toolchain Configuration
 */
@Composable
fun AgentConfigDialog(
    targetEnvironmentName: String,
    onInstall: (title: String, command: String) -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
    envMgr: LinuxEnvironmentManager? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var customCommand by remember { mutableStateOf("") }

    // Resolve LinuxEnvironmentManager
    val effectiveEnvMgr = envMgr ?: runCatching { LinuxEnvironmentManager.getInstance(context) }.getOrNull()
    val toolchain = effectiveEnvMgr?.toolchain?.collectAsState()?.value ?: ToolchainInfo(environmentType = targetEnvironmentName)
    val status = effectiveEnvMgr?.status?.collectAsState()?.value ?: LinuxEnvironmentStatus.NOT_INSTALLED
    val isInstalling = status == LinuxEnvironmentStatus.INITIALIZING

    LaunchedEffect(Unit) {
        effectiveEnvMgr?.detectEnvironment()
    }

    // Build granular tool items list
    val baseTools = remember(toolchain) {
        listOf(
            BaseToolConfig(
                id = "bash",
                name = "GNU Bash",
                description = "Linux 官方标准 Shell，提供完备的脚本支持、历史命令与终端交互体验。",
                installCommand = "apk add bash",
                runCommand = "bash",
                isInstalled = toolchain.hasBash,
                statusText = toolchain.bashVersion ?: if (toolchain.hasBash) "已就绪" else "未安装 (当前为精简 Shell)",
                icon = Icons.Default.Terminal,
            ),
            BaseToolConfig(
                id = "git",
                name = "Git 版本控制",
                description = "分布式版本控制系统，克隆开源项目仓库、提交补丁及辅助 AI 结对编程必需。",
                installCommand = "apk add git",
                runCommand = "git --version",
                isInstalled = toolchain.hasGit,
                statusText = toolchain.gitVersion ?: if (toolchain.hasGit) "已就绪" else "未安装",
                icon = Icons.Default.Build,
            ),
            BaseToolConfig(
                id = "nodejs",
                name = "Node.js & npm",
                description = "JavaScript 运行时与官方包管理器，用于运行 Pi Agent、Claude Code 等 AI 客户端。",
                installCommand = "apk add nodejs npm",
                runCommand = "node -v",
                isInstalled = toolchain.hasNode,
                statusText = if (toolchain.hasNode) "${toolchain.nodeVersion ?: "已安装"} (npm: ${toolchain.npmVersion ?: "就绪"})" else "未安装",
                icon = Icons.Default.Code,
            ),
            BaseToolConfig(
                id = "python",
                name = "Python 3 & pip",
                description = "Python 开发运行环境，运行 Aider、各类数据分析脚本与 AI 自动化工具必需。",
                installCommand = "apk add python3 py3-pip",
                runCommand = "python3 --version",
                isInstalled = toolchain.hasPython,
                statusText = if (toolchain.hasPython) (toolchain.pythonVersion ?: "已就绪") else "未安装",
                icon = Icons.Default.AutoAwesome,
            ),
            BaseToolConfig(
                id = "curl",
                name = "Curl 网络工具",
                description = "命令行 HTTP/网络请求与文件下载传输工具，拉取远程脚本或测试接口连接。",
                installCommand = "apk add curl",
                runCommand = "curl --version",
                isInstalled = false, // probes or defaults
                statusText = "常用网络工具",
                icon = Icons.Default.Public,
            ),
            BaseToolConfig(
                id = "busybox",
                name = "BusyBox 核心工具箱",
                description = "提供 100+ 核心 Linux 基础实用工具（ls, grep, sed, awk, tar, vi 等）。",
                installCommand = "apk add busybox",
                runCommand = "busybox",
                isInstalled = toolchain.hasBusyBox,
                statusText = toolchain.busyBoxVersion ?: if (toolchain.hasBusyBox) "100+ 工具就绪" else "精简模式",
                icon = Icons.Default.Layers,
            ),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(1.dp, GeekColors.BorderHighlight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GeekColors.BrandAccentGlow,
                            border = BorderStroke(1.dp, GeekColors.BrandAccent),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = GeekColors.BrandAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "工具链与环境配置",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = GeekColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GeekColors.CardElevated,
                                    border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
                                ) {
                                    Text(
                                        text = targetEnvironmentName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = GeekColors.TerminalCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text(
                                text = "GNU Bash 与各系统组件单项配置 · 终端 AI Agent 部署",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = GeekColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Close Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.CardElevated,
                        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss),
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

                Spacer(Modifier.height(14.dp))

                // Multi-Page Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GeekColors.CardElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val tabs = listOf(
                        0 to ("1. GNU Bash / 基础环境" to Icons.Default.Terminal),
                        1 to ("2. AI Agent 工具链" to Icons.Default.SmartToy),
                    )
                    tabs.forEach { (idx, item) ->
                        val isSelected = selectedTab == idx
                        val (title, icon) = item
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GeekColors.BrandAccent else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = idx },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else GeekColors.TextMuted,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                    ),
                                    color = if (isSelected) Color.White else GeekColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ========================================== PAGE CONTENT
                if (selectedTab == 0) {
                    // ========================================== PAGE 1: Granular System Tools
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 1. Environment Header Status Card
                        Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GeekColors.CardElevated,
                                border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = GeekColors.TerminalCyan,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = toolchain.environmentType,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                ),
                                                color = GeekColors.TextPrimary,
                                            )
                                            Text(
                                                text = if (toolchain.hasAlpine) "Alpine Linux 发行版就绪 · 支持 apk 安装" else "Android 原生 Shell",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = GeekColors.TextMuted,
                                            )
                                        }
                                    }

                                    // Refresh button
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GeekColors.DeepCanvas,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                scope.launch {
                                                    effectiveEnvMgr?.detectEnvironment()
                                                    Toast.makeText(context, "环境状态已刷新", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "刷新",
                                                tint = GeekColors.TextMuted,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("刷新检测", fontSize = 10.sp, color = GeekColors.TextMuted)
                                        }
                                    }
                                }
                            }

                        // 2. Alpine Linux Subsystem Base (Infrastructure)
                        if (effectiveEnvMgr != null) {
                            Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GeekColors.CardElevated,
                                    border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Layers,
                                                    contentDescription = null,
                                                    tint = GeekColors.BrandAccent,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Alpine Linux 子系统根环境",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = GeekColors.TextPrimary,
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (toolchain.hasAlpine) GeekColors.NeonGreen.copy(alpha = 0.15f) else GeekColors.AmberWarn.copy(alpha = 0.15f),
                                            ) {
                                                Text(
                                                    text = if (toolchain.hasAlpine) "已部署 (3.20.3)" else "未部署",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = if (toolchain.hasAlpine) GeekColors.NeonGreen else GeekColors.AmberWarn,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "包含独立的 apk 官方包管理器与 fake-root 沙箱，所有后续工具均可运行在此子系统中。",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = GeekColors.TextMuted,
                                        )

                                        if (isInstalling) {
                                            Spacer(Modifier.height(8.dp))
                                            AlpineInstallProgressIndicator(effectiveEnvMgr)
                                        }

                                        Spacer(Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (toolchain.hasAlpine && !toolchain.hasProot) {
                                                Button(
                                                    onClick = {
                                                        effectiveEnvMgr.ensureProotAvailable(scope) { ok ->
                                                            val msg = if (ok) "PRoot 沙箱引擎已就绪！重开终端即可进入 Linux。" else "下载 PRoot 失败，请检查网络。"
                                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    enabled = !isInstalling,
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = Color.White,
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("补齐 PRoot 沙箱", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    effectiveEnvMgr.deployAlpineRootfs(scope) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                enabled = !isInstalling,
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (toolchain.hasAlpine) GeekColors.CardSurface else GeekColors.BrandAccent,
                                                ),
                                                border = if (toolchain.hasAlpine) BorderStroke(1.dp, GeekColors.BorderSubtle) else null,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp),
                                            ) {
                                                Icon(
                                                    imageVector = if (toolchain.hasAlpine) Icons.Default.CleaningServices else Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = if (toolchain.hasAlpine) GeekColors.TextPrimary else Color.White,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (toolchain.hasAlpine) "重新部署 / 重置子系统" else "部署 Alpine 子系统",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (toolchain.hasAlpine) GeekColors.TextPrimary else Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        // 3. Configure Complete Alpine Linux System Card
                        Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GeekColors.CardElevated,
                                border = BorderStroke(1.dp, GeekColors.BrandAccent.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = GeekColors.BrandAccentGlow,
                                                border = BorderStroke(0.5.dp, GeekColors.BrandAccent),
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription = null,
                                                        tint = GeekColors.BrandAccent,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "配置 Alpine 完整系统",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = GeekColors.TextPrimary,
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = GeekColors.BrandAccent.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = "官方完整套件",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = GeekColors.BrandAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "将 3.5MB 精简 minirootfs 升级为标准完整 Alpine 发行版环境，补全 alpine-base 系统套件、alpine-conf 管理脚本（含 setup-apkrepos/setup-timezone 等）、ca-certificates SSL 证书库、tzdata 时区库、coreutils 核心工具及 procps 进程管理。",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = GeekColors.TextMuted,
                                        lineHeight = 16.sp,
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    val fullSystemCmd = "apk add alpine-base alpine-conf ca-certificates tzdata coreutils procps"
                                    CompactCommandBox(
                                        command = fullSystemCmd,
                                        onCopy = { copyToClipboard(context, fullSystemCmd) },
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Button(
                                            onClick = {
                                                onInstall("配置 Alpine 完整系统", "$fullSystemCmd\n")
                                                onDismiss()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "一键配置完整系统",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }
                            }

                        // 4. Section Title for Individual Tools
                        Text(
                            text = "核心工具组件（分开独立安装与管理）",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = GeekColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )

                        // 4. Granular Individual Tool Cards
                        baseTools.forEach { tool ->
                            BaseToolCard(
                                tool = tool,
                                onInstall = {
                                    onInstall("安装 ${tool.name}", "${tool.installCommand}\n")
                                    onDismiss()
                                },
                                onRun = {
                                    if (tool.runCommand != null) {
                                        onInstall("运行 ${tool.name}", "${tool.runCommand}\n")
                                        onDismiss()
                                    }
                                },
                                onCopy = {
                                    copyToClipboard(context, tool.installCommand)
                                },
                            )
                        }

                        // 5. Mirror Stations Switcher Card (Pure Chinese & Uniform Height)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GeekColors.CardElevated,
                            border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = GeekColors.TerminalCyan,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "软件源镜像切换",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GeekColors.TextPrimary,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "支持官方全球节点与国内各大高校镜像自由切换，保障海内外任何网络顺畅下载。",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = GeekColors.TextMuted,
                                )

                                Spacer(Modifier.height(10.dp))

                                // Row 1 (Uniform height 42.dp, pure Chinese, no English tag)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    UniformMirrorButton(
                                        modifier = Modifier.weight(1f),
                                        label = "官方全球源",
                                        onClick = {
                                            onInstall("切换官方全球源", "pkg mirror global\n")
                                            onDismiss()
                                        },
                                    )
                                    UniformMirrorButton(
                                        modifier = Modifier.weight(1f),
                                        label = "中科大源",
                                        onClick = {
                                            onInstall("切换中科大源", "pkg mirror ustc\n")
                                            onDismiss()
                                        },
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Row 2 (Uniform height 42.dp, pure Chinese, no English tag)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    UniformMirrorButton(
                                        modifier = Modifier.weight(1f),
                                        label = "阿里云源",
                                        onClick = {
                                            onInstall("切换阿里云源", "pkg mirror aliyun\n")
                                            onDismiss()
                                        },
                                    )
                                    UniformMirrorButton(
                                        modifier = Modifier.weight(1f),
                                        label = "智能测速选源",
                                        onClick = {
                                            onInstall("智能测速选源", "pkg mirror auto\n")
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ========================================== PAGE 2: Terminal AI Agents Toolchain
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PREDEFINED_AGENTS.forEach { agent ->
                            AgentItemCard(
                                agent = agent,
                                onInstall = {
                                    onInstall("安装: ${agent.name}", "${agent.installCommand}\n")
                                    onDismiss()
                                },
                                onRun = {
                                    onInstall("运行: ${agent.name}", "${agent.runCommand}\n")
                                    onDismiss()
                                },
                                onCopy = {
                                    copyToClipboard(context, agent.installCommand)
                                },
                            )
                        }

                        // Custom Command Section
                        Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GeekColors.CardElevated,
                                border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "自定义包 / 指令安装",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = GeekColors.TextPrimary,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "输入自定义 npm 或 pip 包名/完整命令，将在当前终端直接执行",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = GeekColors.TextMuted,
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = customCommand,
                                        onValueChange = { customCommand = it },
                                        placeholder = {
                                            Text(
                                                "例如: npm i -g @anthropic-ai/claude-code",
                                                fontSize = 12.sp,
                                                color = GeekColors.TextMuted,
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GeekColors.BrandAccent,
                                            unfocusedBorderColor = GeekColors.BorderSubtle,
                                        ),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                        ),
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Button(
                                            onClick = {
                                                if (customCommand.isNotBlank()) {
                                                    val cmd = customCommand.trim()
                                                    val finalCmd = if (cmd.startsWith("npm") || cmd.startsWith("pip") || cmd.startsWith("git")) {
                                                        cmd
                                                    } else {
                                                        "npm install -g $cmd"
                                                    }
                                                    onInstall("运行安装", "$finalCmd\n")
                                                    onDismiss()
                                                }
                                            },
                                            enabled = customCommand.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GeekColors.BrandAccent,
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("执行安装", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlpineInstallProgressIndicator(envMgr: LinuxEnvironmentManager) {
    val installProgress by envMgr.installProgress.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { installProgress.first.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = GeekColors.TerminalCyan,
            trackColor = GeekColors.DeepCanvas,
        )
        Text(
            text = installProgress.second,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = GeekColors.TerminalCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Sleek, low-profile command code snippet box with built-in copy chip.
 * Uses basicMarquee to auto-scroll long commands without registering touch drag handlers,
 * eliminating nested gesture conflicts and keeping vertical scrolling silky smooth.
 */
@Composable
private fun CompactCommandBox(
    command: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = GeekColors.DeepCanvas,
        border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = GeekColors.TerminalCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = GeekColors.CardElevated,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onCopy),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = GeekColors.TextMuted,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("复制", fontSize = 10.sp, color = GeekColors.TextMuted)
                }
            }
        }
    }
}

/**
 * Individual Granular Card for Base Tools (GNU Bash, Git, Node, Python, Curl, BusyBox).
 */
@Composable
private fun BaseToolCard(
    tool: BaseToolConfig,
    onInstall: () -> Unit,
    onRun: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GeekColors.CardElevated,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Icon + Name + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (tool.isInstalled) GeekColors.NeonGreen.copy(alpha = 0.15f) else GeekColors.BrandAccentGlow,
                        border = BorderStroke(0.5.dp, if (tool.isInstalled) GeekColors.NeonGreen else GeekColors.BrandAccent),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = if (tool.isInstalled) GeekColors.NeonGreen else GeekColors.BrandAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (tool.isInstalled) GeekColors.NeonGreen.copy(alpha = 0.12f) else GeekColors.DeepCanvas,
                    border = BorderStroke(0.5.dp, if (tool.isInstalled) GeekColors.NeonGreen.copy(alpha = 0.3f) else GeekColors.BorderSubtle),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (tool.isInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (tool.isInstalled) GeekColors.NeonGreen else GeekColors.AmberWarn,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = tool.statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (tool.isInstalled) GeekColors.NeonGreen else GeekColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // Description
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = GeekColors.TextMuted,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(5.dp))

            CompactCommandBox(
                command = tool.installCommand,
                onCopy = onCopy,
            )

            Spacer(Modifier.height(6.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tool.isInstalled && tool.runCommand != null) {
                    OutlinedButton(
                        onClick = onRun,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GeekColors.TextPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (tool.id == "bash") "启动 Bash" else "运行测试",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = GeekColors.TextPrimary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tool.isInstalled) GeekColors.CardSurface else GeekColors.BrandAccent,
                    ),
                    border = if (tool.isInstalled) BorderStroke(1.dp, GeekColors.BorderSubtle) else null,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = if (tool.isInstalled) Icons.Default.Refresh else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (tool.isInstalled) GeekColors.TextPrimary else Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (tool.isInstalled) "重新安装" else "安装 ${tool.name}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = if (tool.isInstalled) GeekColors.TextPrimary else Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Pure Chinese, strictly uniform height (42.dp) button for mirror switching.
 */
@Composable
private fun UniformMirrorButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GeekColors.DeepCanvas,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                color = GeekColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AgentItemCard(
    agent: AgentPackageInfo,
    onInstall: () -> Unit,
    onRun: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GeekColors.CardElevated,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = agent.tagColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, agent.tagColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = agent.icon,
                                contentDescription = null,
                                tint = agent.tagColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = agent.tagColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = agent.tag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = agent.tagColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Description
            Text(
                text = agent.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = GeekColors.TextMuted,
                lineHeight = 16.sp,
            )

            Spacer(Modifier.height(6.dp))

            CompactCommandBox(
                command = agent.installCommand,
                onCopy = onCopy,
            )

            Spacer(Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                // Run Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GeekColors.CardElevated,
                    border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    onClick = onRun,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GeekColors.TextPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "运行",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GeekColors.TextPrimary,
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Install Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GeekColors.BrandAccent,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    onClick = onInstall,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "一键安装",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Agent Command", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "命令已复制到剪贴板", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
