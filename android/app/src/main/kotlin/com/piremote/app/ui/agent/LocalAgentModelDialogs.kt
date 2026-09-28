package com.piremote.app.ui.agent

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piremote.app.data.SettingsStore
import com.piremote.app.ui.GeekColors
import com.piremote.app.ui.gui.PRESET_PROVIDER_ENDPOINTS
import com.piremote.app.ui.gui.PiModelOption
import com.piremote.app.ui.pressClickEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val phoneModelHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

data class PhoneProviderInfo(
    val id: String,
    val name: String,
    val defaultEndpoint: String,
    val recommendedModels: List<String>,
)

val PHONE_PROVIDER_PRESETS = listOf(
    PhoneProviderInfo("deepseek", "DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner")),
    PhoneProviderInfo("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", listOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-72B-Instruct")),
    PhoneProviderInfo("amd_radeon", "AMD Radeon", "https://developer.amd.com.cn/radeon/api/v1", listOf("DeepSeek-V4-Flash-0731", "deepseek-ai/DeepSeek-V3")),
    PhoneProviderInfo("openai", "OpenAI", "https://api.openai.com/v1", listOf("gpt-4o", "gpt-4o-mini", "o1", "o3-mini")),
    PhoneProviderInfo("anthropic", "Anthropic", "https://api.anthropic.com/v1", listOf("claude-3-7-sonnet-20250219", "claude-3-5-sonnet-20241022")),
    PhoneProviderInfo("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", listOf("gemini-2.5-flash", "gemini-2.0-flash")),
    PhoneProviderInfo("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", listOf("anthropic/claude-3.7-sonnet", "deepseek/deepseek-r1")),
    PhoneProviderInfo("ollama", "本地 Ollama", "http://127.0.0.1:11434/v1", listOf("qwen2.5-coder")),
    PhoneProviderInfo("custom", "自定义服务商", "", emptyList()),
)

/**
 * 手机本地 Agent 专用模型选择对话框
 * 遵循电脑端高颜值风格（搜索、服务商过滤、单选标记、使用中高亮、删除确认），
 * 完全基于手机本地 SettingsStore 运行，文案针对手机端。
 */
@Composable
fun LocalAgentModelPickerDialog(
    currentModelKey: String?,
    currentModelLabel: String?,
    currentModelProvider: String?,
    models: List<PiModelOption>,
    onPickModel: (PiModelOption) -> Unit,
    onAddNewModel: () -> Unit,
    onDeleteModel: (PiModelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProviderFilter by remember { mutableStateOf<String?>(null) }
    var modelToDelete by remember { mutableStateOf<PiModelOption?>(null) }

    val providers = remember(models) {
        models.map { it.provider.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    val filteredModels = remember(models, searchQuery, selectedProviderFilter) {
        val q = searchQuery.trim()
        models.filter { option ->
            val matchProvider = selectedProviderFilter == null || option.provider.equals(selectedProviderFilter, ignoreCase = true)
            val matchQuery = q.isBlank() || (
                option.label.contains(q, ignoreCase = true) ||
                option.cleanLabel.contains(q, ignoreCase = true) ||
                option.key.contains(q, ignoreCase = true) ||
                option.provider.contains(q, ignoreCase = true) ||
                option.id.contains(q, ignoreCase = true)
            )
            matchProvider && matchQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = GeekColors.CardSurface,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = GeekColors.BrandAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onAddNewModel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("配置服务商 / 添加模型", color = GeekColors.BrandAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        },
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GeekColors.BrandAccentGlow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "切换模型",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = GeekColors.TextPrimary,
                        )
                        val subText = buildString {
                            if (models.isNotEmpty()) {
                                append("共 ${models.size} 个已配置模型")
                                if (!currentModelProvider.isNullOrBlank()) {
                                    append(" · 当前: $currentModelProvider")
                                }
                            } else {
                                append("尚未配置可用模型")
                            }
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = GeekColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (models.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "搜索模型名称或服务商...",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清空",
                                        tint = GeekColors.TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.DeepCanvas,
                            unfocusedContainerColor = GeekColors.DeepCanvas,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                            cursorColor = GeekColors.TerminalCyan,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )

                    Spacer(Modifier.height(10.dp))
                }

                if (providers.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isAllSelected = selectedProviderFilter == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllSelected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                            border = BorderStroke(0.6.dp, if (isAllSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedProviderFilter = null },
                        ) {
                            Text(
                                text = "全部 (${models.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isAllSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        providers.forEach { prov ->
                            val isSelected = selectedProviderFilter.equals(prov, ignoreCase = true)
                            val count = models.count { it.provider.equals(prov, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                                border = BorderStroke(0.6.dp, if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedProviderFilter = if (isSelected) null else prov
                                    },
                            ) {
                                Text(
                                    text = "$prov ($count)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (models.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = GeekColors.TextMuted,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "暂无已配置的模型",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = GeekColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "未填入服务商 API Key，请先配置服务商与端点",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onAddNewModel,
                            colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("+ 配置服务商与 API Key", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (filteredModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到与 \"$searchQuery\" 匹配的模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextMuted,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        filteredModels.forEachIndexed { index, option ->
                            val selected = option.key == currentModelKey || option.id == currentModelKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) GeekColors.BrandAccent.copy(alpha = 0.12f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPickModel(option) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = if (selected) "●" else "○",
                                        color = if (selected) GeekColors.BrandAccent else GeekColors.TextMuted,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.cleanLabel,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (selected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (option.provider.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = if (selected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                                                    border = BorderStroke(0.6.dp, if (selected) GeekColors.BrandAccent.copy(alpha = 0.6f) else GeekColors.BorderSubtle),
                                                ) {
                                                    Text(
                                                        text = option.provider,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = if (selected) GeekColors.BrandAccent else GeekColors.TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            if (option.cleanId.isNotBlank() && !option.cleanId.equals(option.cleanLabel, ignoreCase = true)) {
                                                Text(
                                                    text = option.cleanId,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                    ),
                                                    color = GeekColors.TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                    if (selected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = GeekColors.BrandAccent.copy(alpha = 0.18f),
                                        ) {
                                            Text(
                                                text = "使用中",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = GeekColors.BrandAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { modelToDelete = option },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "删除模型",
                                            tint = GeekColors.TextMuted,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            if (index < filteredModels.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp)
                                        .height(0.6.dp)
                                        .background(GeekColors.TextMuted.copy(alpha = 0.12f)),
                                )
                            }
                        }
                    }
                }
            }
        },
    )

    // 删除模型确认弹窗（手机端文案）
    if (modelToDelete != null) {
        val target = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = GeekColors.CardElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = GeekColors.RoseError,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "删除模型",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "确定要从本地移除模型「${target.cleanLabel}」吗？\n移除后该模型将不会出现在切换列表中。如需重新启用，可在服务商配置中重新勾选或添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GeekColors.TextSecondary,
                        lineHeight = 20.sp,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.DeepCanvas,
                        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "模型 ID: ${target.id.ifEmpty { target.key }}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                                color = GeekColors.TextPrimary,
                            )
                            if (target.provider.isNotBlank()) {
                                Text(
                                    text = "所属服务商: ${target.provider}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeekColors.TextMuted,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = target
                        modelToDelete = null
                        onDeleteModel(toDel)
                    },
                ) {
                    Text("确认删除", color = GeekColors.RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("取消", color = GeekColors.TextMuted)
                }
            },
        )
    }
}

/**
 * 手机本地 Agent 专用服务商与端点配置对话框
 * 100% 本地直接读写 SettingsStore，通过手机网络检测端点模型，文案针对手机本地使用。
 */
@Composable
fun LocalAgentProviderConfigDialog(
    settings: SettingsStore,
    onConfirm: (provider: String, apiKey: String, baseUrl: String?, selectedModels: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProvider by remember {
        mutableStateOf(settings.localAgentProviderId.ifBlank { "deepseek" })
    }
    var customProviderText by remember { mutableStateOf("") }
    var apiKeyText by remember(selectedProvider) {
        mutableStateOf(settings.getProviderApiKey(selectedProvider))
    }
    var showPassword by remember { mutableStateOf(false) }

    var customBaseUrl by remember(selectedProvider) {
        mutableStateOf(settings.getProviderEndpoint(selectedProvider).ifBlank {
            PRESET_PROVIDER_ENDPOINTS[selectedProvider].orEmpty()
        })
    }
    var showEndpointConfig by remember(selectedProvider) {
        mutableStateOf(selectedProvider == "custom" || selectedProvider == "ollama" || customBaseUrl != PRESET_PROVIDER_ENDPOINTS[selectedProvider])
    }

    val effectiveProvider = if (selectedProvider == "custom") customProviderText.trim() else selectedProvider

    // 初始化模型列表：包含预设推荐模型与本地已添加模型
    val availableModels = remember(selectedProvider, effectiveProvider) {
        val preset = PHONE_PROVIDER_PRESETS.find { it.id.equals(effectiveProvider, ignoreCase = true) }?.recommendedModels.orEmpty()
        val custom = settings.getLocalAgentCustomModels()
            .filter { it.first.equals(effectiveProvider, ignoreCase = true) }
            .map { it.second }
        mutableStateListOf<String>().apply {
            addAll((preset + custom).distinct())
        }
    }
    val selectedModels = remember(selectedProvider, effectiveProvider) {
        val deleted = settings.getLocalAgentDeletedModels()
        val list = availableModels.filterNot { m ->
            deleted.contains(m.lowercase()) || deleted.contains("${effectiveProvider}/$m".lowercase())
        }
        mutableStateListOf<String>().apply { addAll(list) }
    }

    var isDetecting by remember { mutableStateOf(false) }
    var detectStatusMessage by remember { mutableStateOf<String?>(null) }
    var manualModelInput by remember { mutableStateOf("") }

    fun detectModelsDirectly() {
        val endpoint = customBaseUrl.trim().ifEmpty { PRESET_PROVIDER_ENDPOINTS[selectedProvider].orEmpty() }
        val key = apiKeyText.trim()
        if (key.isEmpty() && selectedProvider != "ollama") {
            detectStatusMessage = "未填入 Key，可填入凭据或直接在下方勾选/输入模型"
            Toast.makeText(context, "未填入 API Key，请输入凭据后再探测", Toast.LENGTH_SHORT).show()
            return
        }
        isDetecting = true
        detectStatusMessage = "正在通过手机网络连接端点探测可用模型..."

        scope.launch(Dispatchers.IO) {
            try {
                val candidateUrls = mutableListOf<String>()
                val reqBuilder = Request.Builder().addHeader("Accept", "application/json")

                if (effectiveProvider == "google") {
                    candidateUrls.add("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                } else if (effectiveProvider == "anthropic") {
                    candidateUrls.add("https://api.anthropic.com/v1/models")
                    reqBuilder.addHeader("x-api-key", key)
                    reqBuilder.addHeader("anthropic-version", "2023-06-01")
                } else if (effectiveProvider == "deepseek") {
                    candidateUrls.add("https://api.deepseek.com/models")
                    candidateUrls.add("https://api.deepseek.com/v1/models")
                    if (key.isNotBlank()) reqBuilder.addHeader("Authorization", "Bearer $key")
                } else {
                    val raw = endpoint.trim().trimEnd('/')
                    val base = raw.removeSuffix("/chat/completions").removeSuffix("/chat")
                    if (base.endsWith("/v1")) {
                        candidateUrls.add("$base/models")
                        candidateUrls.add(base.removeSuffix("/v1") + "/models")
                    } else {
                        candidateUrls.add("$base/models")
                        candidateUrls.add("$base/v1/models")
                    }
                    if (key.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $key")
                    }
                }

                var fetchedList: List<String>? = null
                var lastErr = ""

                for (url in candidateUrls.distinct()) {
                    try {
                        phoneModelHttpClient.newCall(reqBuilder.url(url).build()).execute().use { resp ->
                            val body = resp.body?.string().orEmpty().trim()
                            if (resp.isSuccessful) {
                                val list = mutableListOf<String>()
                                if (body.startsWith("[")) {
                                    val arr = JSONArray(body)
                                    for (i in 0 until arr.length()) {
                                        val item = arr.optJSONObject(i) ?: continue
                                        val id = item.optString("id").ifEmpty { item.optString("name") }
                                        if (id.isNotBlank()) list.add(id)
                                    }
                                } else if (body.startsWith("{")) {
                                    val obj = JSONObject(body)
                                    val data = obj.optJSONArray("data") ?: obj.optJSONArray("models")
                                    if (data != null) {
                                        for (i in 0 until data.length()) {
                                            val item = data.optJSONObject(i) ?: continue
                                            val id = item.optString("id").ifEmpty { item.optString("name") }
                                            if (id.isNotBlank()) list.add(id)
                                        }
                                    }
                                }
                                if (list.isNotEmpty()) {
                                    fetchedList = list
                                }
                            } else {
                                lastErr = "HTTP ${resp.code}"
                            }
                        }
                        if (!fetchedList.isNullOrEmpty()) break
                    } catch (e: Exception) {
                        lastErr = e.message ?: "连接失败"
                    }
                }

                withContext(Dispatchers.Main) {
                    isDetecting = false
                    if (!fetchedList.isNullOrEmpty()) {
                        for (m in fetchedList!!) {
                            if (!availableModels.contains(m)) availableModels.add(m)
                            if (!selectedModels.contains(m)) selectedModels.add(m)
                        }
                        detectStatusMessage = "探测成功：发现 ${fetchedList!!.size} 个可用模型"
                    } else {
                        detectStatusMessage = "端点未返回模型（$lastErr），可直接在下方勾选或手动添加"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDetecting = false
                    detectStatusMessage = "探测异常: ${e.message}"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = GeekColors.CardSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GeekColors.BrandAccentGlow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "配置服务商与端点",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                    Text(
                        text = "配置手机端本地 AI 模型与凭据",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = GeekColors.TextMuted,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "选择服务商",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextMuted,
                )
                Spacer(Modifier.height(6.dp))

                // 服务商横向切换标签
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PHONE_PROVIDER_PRESETS.forEach { preset ->
                        val isSelected = selectedProvider == preset.id
                        val isConfigured = settings.getProviderApiKey(preset.id).isNotBlank() ||
                                           (preset.id == "ollama" && settings.localAgentProviderId == "ollama")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                            border = BorderStroke(0.6.dp, if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedProvider = preset.id
                                    detectStatusMessage = null
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isConfigured) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(GeekColors.BrandAccent),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                )
                            }
                        }
                    }
                }

                if (selectedProvider == "custom") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customProviderText,
                        onValueChange = { customProviderText = it },
                        placeholder = { Text("服务商标识 (例如: my-provider)", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.DeepCanvas,
                            unfocusedContainerColor = GeekColors.DeepCanvas,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // API Key 填入区
                Text(
                    text = if (selectedProvider == "ollama") "API Key (Ollama 本地运行通常无需填写)" else "API Key 凭据",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextMuted,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    placeholder = {
                        Text(
                            text = if (selectedProvider == "ollama") "本地服务无需 Key (可选)" else "请输入 API Key (sk-...)",
                            fontSize = 12.sp,
                            color = GeekColors.TextMuted,
                        )
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPassword) "隐藏" else "显示",
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GeekColors.DeepCanvas,
                        unfocusedContainerColor = GeekColors.DeepCanvas,
                        focusedBorderColor = GeekColors.TerminalCyan,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                        cursorColor = GeekColors.TerminalCyan,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(Modifier.height(10.dp))

                // 端点设置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndpointConfig = !showEndpointConfig },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "API 端点 (Base URL)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (showEndpointConfig) "收起 ▲" else "自定义 ▼",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = GeekColors.BrandAccent,
                    )
                }

                if (showEndpointConfig) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        placeholder = { Text("https://api.example.com/v1", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.DeepCanvas,
                            unfocusedContainerColor = GeekColors.DeepCanvas,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 模型选择与探测栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "可用模型列表 (${selectedModels.size}/${availableModels.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextMuted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GeekColors.BrandAccent.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isDetecting) { detectModelsDirectly() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isDetecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = GeekColors.BrandAccent,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isDetecting) "探测中..." else "探测可用模型",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = GeekColors.BrandAccent,
                                )
                            }
                        }
                    }
                }

                detectStatusMessage?.let { msg ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (msg.contains("成功")) GeekColors.BrandAccent else GeekColors.TextMuted,
                    )
                }

                Spacer(Modifier.height(6.dp))

                // 手动添加模型 ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualModelInput,
                        onValueChange = { manualModelInput = it },
                        placeholder = { Text("输入自定义模型 ID (如 deepseek-chat)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GeekColors.DeepCanvas,
                            unfocusedContainerColor = GeekColors.DeepCanvas,
                            focusedBorderColor = GeekColors.TerminalCyan,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedTextColor = GeekColors.TextPrimary,
                            unfocusedTextColor = GeekColors.TextPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val clean = manualModelInput.trim()
                            if (clean.isNotBlank()) {
                                if (!availableModels.contains(clean)) availableModels.add(clean)
                                if (!selectedModels.contains(clean)) selectedModels.add(clean)
                                manualModelInput = ""
                            }
                        }),
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            val clean = manualModelInput.trim()
                            if (clean.isNotBlank()) {
                                if (!availableModels.contains(clean)) availableModels.add(clean)
                                if (!selectedModels.contains(clean)) selectedModels.add(clean)
                                manualModelInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 模型勾选卡片列表
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GeekColors.DeepCanvas,
                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (availableModels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "暂无模型，可点击上方「探测可用模型」或手动输入添加",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = GeekColors.TextMuted,
                                )
                            }
                        } else {
                            availableModels.forEach { modelId ->
                                val isChecked = selectedModels.contains(modelId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) {
                                                selectedModels.remove(modelId)
                                            } else {
                                                selectedModels.add(modelId)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (!selectedModels.contains(modelId)) selectedModels.add(modelId)
                                            } else {
                                                selectedModels.remove(modelId)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = GeekColors.BrandAccent,
                                            uncheckedColor = GeekColors.TextMuted,
                                        ),
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = modelId,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                        color = if (isChecked) GeekColors.TextPrimary else GeekColors.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prov = effectiveProvider.ifBlank { "deepseek" }
                    val key = apiKeyText.trim()
                    val endpoint = customBaseUrl.trim().ifEmpty { PRESET_PROVIDER_ENDPOINTS[prov].orEmpty() }
                    onConfirm(prov, key, endpoint, selectedModels.toList())
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("保存并启用", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = GeekColors.TextMuted)
            }
        },
    )
}
