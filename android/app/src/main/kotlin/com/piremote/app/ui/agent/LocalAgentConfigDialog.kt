package com.piremote.app.ui.agent

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piremote.app.data.SettingsStore
import com.piremote.app.data.agent.LocalAgentTools
import com.piremote.app.ui.GeekColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val modelsHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

data class ProviderPreset(
    val id: String,
    val name: String,
    val endpoint: String,
    val defaultModel: String,
)

val PRESETS = listOf(
    ProviderPreset("deepseek", "DeepSeek (推荐)", "https://api.deepseek.com/v1", "deepseek-chat"),
    ProviderPreset("siliconflow", "硅基流动 (SiliconFlow)", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3"),
    ProviderPreset("amd_radeon", "AMD Radeon Cloud", "https://developer.amd.com.cn/radeon/api/v1", "DeepSeek-V4-Flash-0731"),
    ProviderPreset("openrouter", "Claude (OpenRouter)", "https://openrouter.ai/api/v1", "anthropic/claude-3.7-sonnet"),
    ProviderPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
    ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o"),
    ProviderPreset("moonshot", "Moonshot (Kimi)", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
    ProviderPreset("commandcode", "Command Code", "https://api.commandcode.ai/provider/v1", "deepseek/deepseek-v4-flash"),
    ProviderPreset("ollama", "Ollama (本地局域网)", "http://192.168.1.100:11434/v1", "qwen2.5-coder"),
)

@Composable
fun LocalAgentConfigDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    var apiKey by remember { mutableStateOf(settings.localAgentApiKey) }
    var endpoint by remember { mutableStateOf(settings.localAgentEndpoint) }
    var model by remember { mutableStateOf(settings.localAgentModel) }
    var workspace by remember {
        mutableStateOf(
            settings.localAgentWorkspace.ifEmpty {
                LocalAgentTools.getDefaultWorkspace(context).absolutePath
            }
        )
    }
    var showPassword by remember { mutableStateOf(false) }

    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetchStatusText by remember { mutableStateOf<String?>(null) }

    fun fetchAvailableModels() {
        if (apiKey.isBlank()) {
            Toast.makeText(context, "请先填写 API Key 再识别可用模型", Toast.LENGTH_SHORT).show()
            return
        }
        isFetchingModels = true
        fetchStatusText = null
        scope.launch(Dispatchers.IO) {
            try {
                val raw = endpoint.trim().trimEnd('/')
                val url = if (raw.endsWith("/chat/completions")) {
                    raw.removeSuffix("/chat/completions") + "/models"
                } else if (raw.endsWith("/models")) {
                    raw
                } else {
                    "$raw/models"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .get()
                    .build()

                modelsHttpClient.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val json = JSONObject(respBody)
                        val dataArray = json.optJSONArray("data")
                        val list = mutableListOf<String>()
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.optJSONObject(i)
                                val mId = item?.optString("id")
                                if (!mId.isNullOrBlank()) {
                                    list.add(mId)
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isFetchingModels = false
                            if (list.isNotEmpty()) {
                                fetchedModels = list.distinct().sorted()
                                fetchStatusText = "成功识别到 ${list.size} 个可用模型 (点击选择)"
                                Toast.makeText(context, "已识别 ${list.size} 个可用模型！", Toast.LENGTH_SHORT).show()
                            } else {
                                fetchStatusText = "接口未返回模型列表，请手动输入"
                                Toast.makeText(context, "未解析到模型列表，可手动输入", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val parsedError = try {
                            val errJson = JSONObject(respBody)
                            val errObj = errJson.optJSONObject("error")
                            errObj?.optString("message") ?: errJson.optString("error", "HTTP ${resp.code}")
                        } catch (_: Exception) {
                            "HTTP ${resp.code}"
                        }
                        withContext(Dispatchers.Main) {
                            isFetchingModels = false
                            fetchStatusText = "获取失败: $parsedError"
                            Toast.makeText(context, "识别失败: $parsedError", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isFetchingModels = false
                    fetchStatusText = "网络错误: ${e.localizedMessage}，可手动输入"
                    Toast.makeText(context, "连接模型接口超时，可手动输入", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GeekColors.BrandAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("手机原生 AI Agent 模型配置", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "手机本地原生运行 Agent，无需外部终端或电脑。在此配置任意支持 OpenAI 兼容格式的模型端点：",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeekColors.TextSecondary,
                )

                // Quick Presets Selector
                Text(
                    text = "快速预设服务商",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextMuted,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PRESETS.forEach { preset ->
                        val isSelected = endpoint == preset.endpoint
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GeekColors.BrandAccent.copy(alpha = 0.2f) else GeekColors.CardElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val currentPreset = PRESETS.find { it.endpoint.trimEnd('/') == endpoint.trimEnd('/') }
                                    if (currentPreset != null && apiKey.isNotBlank()) {
                                        settings.setProviderApiKey(currentPreset.id, apiKey)
                                    }
                                    endpoint = preset.endpoint
                                    model = preset.defaultModel
                                    apiKey = settings.getProviderApiKey(preset.id)
                                    fetchedModels = emptyList()
                                    fetchStatusText = null
                                },
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.5.sp,
                                ),
                                color = if (isSelected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // API Key Field
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (必填)") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = GeekColors.TextMuted,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    )
                )

                // Endpoint Field
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    )
                )

                // Model Name Header & Auto-Detect Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "模型名称 (Model)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextMuted,
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GeekColors.BrandAccentGlow,
                        border = BorderStroke(0.5.dp, GeekColors.BrandAccent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isFetchingModels) { fetchAvailableModels() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = GeekColors.BrandAccent,
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "识别中...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = GeekColors.BrandAccent,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = GeekColors.BrandAccent,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "一键识别可用模型",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = GeekColors.BrandAccent,
                                )
                            }
                        }
                    }
                }

                // Model Name Input Field
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    placeholder = { Text("例如 deepseek-chat 或点击上方识别") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    )
                )

                // Model Selection Chips List
                if (fetchedModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = fetchStatusText ?: "点击下方模型名称快速选择：",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = GeekColors.TerminalCyan,
                            ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            fetchedModels.forEach { modelId ->
                                val isSelected = model == modelId
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) GeekColors.BrandAccent else GeekColors.CardElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { model = modelId },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = modelId,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                            color = if (isSelected) Color.White else GeekColors.TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (fetchStatusText != null) {
                    Text(
                        text = fetchStatusText ?: "",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = GeekColors.AmberWarn,
                    )
                }

                // Workspace Field
                OutlinedTextField(
                    value = workspace,
                    onValueChange = { workspace = it },
                    label = { Text("手机工作目录 (文档/代码保存路径)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeekColors.BrandAccent,
                        unfocusedBorderColor = GeekColors.BorderSubtle,
                        focusedTextColor = GeekColors.TextPrimary,
                        unfocusedTextColor = GeekColors.TextPrimary,
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val matchedPreset = PRESETS.find { it.endpoint.trimEnd('/') == endpoint.trimEnd('/') }
                    val pid = matchedPreset?.id ?: "custom"
                    settings.setProviderApiKey(pid, apiKey)
                    settings.localAgentProviderId = pid
                    settings.localAgentApiKey = apiKey
                    settings.localAgentEndpoint = endpoint
                    settings.localAgentModel = model
                    settings.localAgentWorkspace = workspace
                    Toast.makeText(context, "本地 AI Agent 配置已保存", Toast.LENGTH_SHORT).show()
                    onSaved()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeekColors.BrandAccent),
            ) {
                Text("保存配置", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = GeekColors.TextMuted)
            }
        },
        containerColor = GeekColors.CardSurface,
    )
}
