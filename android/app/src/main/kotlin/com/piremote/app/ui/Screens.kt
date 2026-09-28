package com.piremote.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piremote.app.R
import com.piremote.app.data.ConnectionState
import com.piremote.app.data.ControlMessage
import com.piremote.app.data.SessionRepository
import com.piremote.app.data.SettingsStore
import com.piremote.app.data.StoredSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Settings Screen

@Composable
fun SettingsScreen(
    repository: SessionRepository,
    connection: ConnectionState,
    onDone: () -> Unit,
) {
    val store = repository.settingsStore
    val themeMode by store.themeModeFlow.collectAsState()
    val guiFontScale by store.guiFontScaleFlow.collectAsState()
    var endpoints by remember { mutableStateOf(store.endpoints.joinToString("\n")) }
    var token by remember { mutableStateOf(store.deviceToken) }
    var pin by remember { mutableStateOf(store.pinnedSha256.orEmpty()) }
    var endpointsVisible by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }
    var pinVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current


    Column(
        Modifier
            .fillMaxSize()
            .background(GeekColors.DeepCanvas)
            .statusBarsPadding()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusManager.clearFocus()
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // App Hero Header (Apple iOS Large Title style)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Apple Settings squircle badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardSurface,
                    border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "系统设置",
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
                                text = "配置中心",
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
                        text = "智能工作台 · 视觉主题与中继通信参数",
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

        // Theme Selection Card (iOS Inset Grouped style)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "界面外观与主题",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = GeekColors.TextPrimary,
                    )
                }
                Text(
                    text = "深色模式、浅色模式，或跟随手机系统自动适配",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeekColors.TextMuted,
                    lineHeight = 18.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeOptionButton(
                        label = "深色模式",
                        icon = Icons.Default.DarkMode,
                        selected = themeMode == SettingsStore.THEME_DARK,
                        modifier = Modifier.weight(1f),
                        onClick = { store.themeMode = SettingsStore.THEME_DARK },
                    )
                    ThemeOptionButton(
                        label = "浅色模式",
                        icon = Icons.Default.LightMode,
                        selected = themeMode == SettingsStore.THEME_LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = { store.themeMode = SettingsStore.THEME_LIGHT },
                    )
                    ThemeOptionButton(
                        label = "跟随系统",
                        icon = Icons.Default.SettingsBrightness,
                        selected = themeMode == SettingsStore.THEME_SYSTEM,
                        modifier = Modifier.weight(1f),
                        onClick = { store.themeMode = SettingsStore.THEME_SYSTEM },
                    )
                }
            }
        }

        // Graphical View Font Size Card (iOS Inset Grouped style)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = null,
                            tint = GeekColors.BrandAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "图形视图文字大小",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GeekColors.TextPrimary,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GeekColors.BrandAccent.copy(alpha = 0.12f),
                    ) {
                        val percent = (guiFontScale * 100).roundToInt()
                        Text(
                            text = "${percent}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = GeekColors.BrandAccent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = "调节图形视图中用户提问与 AI 回复的所有文字显示大小",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeekColors.TextMuted,
                    lineHeight = 18.sp,
                )

                // 4 Preset Buttons (小 85%、标准 100%、大 115%、特大 130%)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val presets = listOf(
                        Triple("小", "85%", SettingsStore.GUI_FONT_SCALE_SMALL),
                        Triple("标准", "100%", SettingsStore.GUI_FONT_SCALE_NORMAL),
                        Triple("大", "115%", SettingsStore.GUI_FONT_SCALE_LARGE),
                        Triple("特大", "130%", SettingsStore.GUI_FONT_SCALE_HUGE),
                    )
                    for ((label, sub, scale) in presets) {
                        val isSelected = abs(guiFontScale - scale) < 0.04f
                        val shape = RoundedCornerShape(10.dp)
                        val bgColor = if (isSelected) GeekColors.BrandAccent else GeekColors.CardElevated
                        val borderColor = if (isSelected) GeekColors.BrandAccent else GeekColors.BorderSubtle
                        val contentColor = if (isSelected) Color.White else GeekColors.TextPrimary
                        val subColor = if (isSelected) Color.White.copy(alpha = 0.8f) else GeekColors.TextMuted

                        Surface(
                            shape = shape,
                            color = bgColor,
                            border = BorderStroke(0.8.dp, borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .pressClickEffect(),
                            onClick = { store.guiFontScale = scale },
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                    ),
                                    color = contentColor,
                                )
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                    ),
                                    color = subColor,
                                )
                            }
                        }
                    }
                }

                // Smooth Stepped Slider
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.TextMuted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = guiFontScale,
                        onValueChange = { store.guiFontScale = (it * 20).roundToInt() / 20f },
                        valueRange = 0.85f..1.30f,
                        steps = 8,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = GeekColors.BrandAccent,
                            activeTrackColor = GeekColors.BrandAccent,
                            inactiveTrackColor = GeekColors.BorderSubtle,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.TextPrimary,
                    )
                }

                // Live Preview Container (所见即所得实时效果预览)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.DeepCanvas,
                    border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "效果实时预览",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                            ),
                            color = GeekColors.TextMuted,
                        )

                        val previewBaseDensity = LocalDensity.current
                        val effectivePreviewFontScale = guiFontScale * 1.05f
                        val previewScaledDensity = remember(previewBaseDensity, effectivePreviewFontScale) {
                            Density(
                                density = previewBaseDensity.density,
                                fontScale = previewBaseDensity.fontScale * effectivePreviewFontScale,
                            )
                        }

                        CompositionLocalProvider(
                            LocalDensity provides previewScaledDensity,
                            LocalGuiFontScale provides effectivePreviewFontScale,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Mini User Bubble
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    val userBrush = if (GeekColors.isDark) {
                                        Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF0071E3)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF0062D2)))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                                            .background(userBrush),
                                    ) {
                                        Text(
                                            text = "帮我写一个快速排序算法",
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        )
                                    }
                                }

                                // Mini AI Assistant Bubble
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = GeekColors.CardSurface,
                                    border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                                    modifier = Modifier.fillMaxWidth(0.96f),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "好的！这是快速排序的标准实现，平均时间复杂度为 O(n log n)：",
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                            color = GeekColors.TextPrimary,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = GeekColors.DeepCanvas,
                                            border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = "fun quickSort(list: List<Int>): List<Int> {\n    if (list.size <= 1) return list\n    val pivot = list[list.size / 2]\n    return quickSort(...) + pivot + ...\n}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 18.5.sp,
                                                ),
                                                color = GeekColors.TextPrimary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(10.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Config Form Card (iOS Inset Grouped)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = GeekColors.BrandAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "中继服务通信参数",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }
                Text(
                    text = "配置参数均来自电脑上的 .secrets/relay.json。请务必确认 SHA-256 指纹准确。",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeekColors.TextMuted,
                    lineHeight = 18.sp,
                )

                OutlinedTextField(
                    value = endpoints,
                    onValueChange = { endpoints = it },
                    label = { Text("中继地址 (每行一个，优先主用)") },
                    placeholder = { Text("wss://relay.example.com:443/relay") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    visualTransformation = if (endpointsVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { endpointsVisible = !endpointsVisible }) {
                            Icon(
                                imageVector = if (endpointsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (endpointsVisible) "隐藏地址" else "显示地址",
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = geekTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.replace("\n", "").replace("\r", "").trim() },
                    label = { Text("设备认证 Token") },
                    placeholder = { Text("Secret device token") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = geekTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.replace("\n", "").replace("\r", "").trim() },
                    label = { Text("证书 SHA-256 绑定指纹") },
                    placeholder = { Text("如 6a2c7b...") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (pinVisible) "隐藏指纹" else "显示指纹",
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = geekTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        store.endpoints = endpoints.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        store.deviceToken = token
                        store.pinnedSha256 = pin
                        repository.connect()
                        Toast.makeText(context, "配置已保存，正在连接中继...", Toast.LENGTH_SHORT).show()
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .pressClickEffect(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeekColors.BrandAccent,
                        contentColor = Color.White,
                    ),
                    enabled = endpoints.isNotBlank() && token.isNotBlank() && pin.isNotBlank(),
                ) {
                    Text("保存配置并连接", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

// ------------------------------------------------ Common Settings & Status Helpers

@Composable
fun PulsingDot(color: Color, glowColor: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        Box(
            Modifier
                .size(16.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(glowColor),
        )
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun ConnectionCapsule(
    connection: ConnectionState,
    onReconnect: (() -> Unit)? = null,
) {
    val (statusColor, glowColor, titleText) = when (connection) {
        is ConnectionState.Connected -> {
            val agent = connection.agentId
            if (agent != null) {
                Triple(GeekColors.BrandAccent, GeekColors.BrandAccentGlow, "安全链路已就绪")
            } else {
                Triple(GeekColors.AmberWarn, Color(0x33FF9F0A), "中继已连 · 电脑端未上线")
            }
        }
        is ConnectionState.Connecting ->
            Triple(GeekColors.AmberWarn, Color(0x33FF9F0A), "正在连接中继...")
        is ConnectionState.Disconnected -> {
            val title = if (connection.reason.equals("replaced", ignoreCase = true)) "连接已被其他设备顶掉" else "连接断开"
            Triple(GeekColors.RoseError, GeekColors.RoseErrorGlow, title)
        }
        is ConnectionState.Idle ->
            Triple(GeekColors.TextMuted, Color.Transparent, "网络空闲")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(18.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
        onClick = { onReconnect?.invoke() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = statusColor, glowColor = glowColor)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor,
                )
            }
            val detailText = when (connection) {
                is ConnectionState.Connected -> if (connection.agentId == null && onReconnect != null) "点击重试" else ""
                is ConnectionState.Connecting -> "WSS 连接中"
                is ConnectionState.Disconnected -> {
                    if (connection.reason.equals("replaced", ignoreCase = true)) "已被顶号 · 点击抢回"
                    else if (onReconnect != null) "点击重连"
                    else connection.reason.ifBlank { "离线" }
                }
                is ConnectionState.Idle -> if (onReconnect != null) "点击连接" else "未连接"
            }
            if (detailText.isNotBlank()) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = GeekColors.TextMuted,
                )
            }
        }
    }
}

@Composable
fun ThemeOptionButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (selected) GeekColors.BrandAccent else GeekColors.CardElevated
    val borderColor = if (selected) GeekColors.BrandAccent else GeekColors.BorderSubtle
    val contentColor = if (selected) Color.White else GeekColors.TextPrimary

    Surface(
        shape = shape,
        color = bgColor,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun geekTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GeekColors.BrandAccent,
    unfocusedBorderColor = GeekColors.BorderSubtle,
    focusedLabelColor = GeekColors.BrandAccent,
    unfocusedLabelColor = GeekColors.TextMuted,
    cursorColor = GeekColors.TerminalCyan,
    focusedTextColor = GeekColors.TextPrimary,
    unfocusedTextColor = GeekColors.TextPrimary,
)

// ----------------------------------------------------------- Session List Screen

@Composable
fun SessionListScreen(
    repository: SessionRepository,
    connection: ConnectionState,
    onOpenSettings: () -> Unit,
    onOpenRemotePath: () -> Unit = {},
) {
    val presets by repository.presets.collectAsState()
    val known by repository.knownSessions.collectAsState()
    val error by repository.lastError.collectAsState()
    val missingSession by repository.missingSessionPrompt.collectAsState()
    val pending by repository.pendingResize.collectAsState()
    val store = repository.settingsStore
    val (cols, rows) = pending ?: (80 to 24)

    val defaultCwd = presets.firstOrNull { it.id.equals("pi", ignoreCase = true) }?.cwd?.takeIf { it.isNotBlank() }
        ?: presets.firstOrNull()?.cwd?.takeIf { it.isNotBlank() }
        ?: ""
    val launchCwd = store.quickLaunchCwd.ifBlank { defaultCwd }

    val running = remember(known) { known.filter { it.alive }.sortedByDescending { it.lastSeenAt } }
    val ended = remember(known) { known.filterNot { it.alive }.sortedByDescending { it.lastSeenAt } }

    var showAgentConfigDialog by remember { mutableStateOf(false) }
    var expandedPresetId by remember { mutableStateOf<String?>(null) }
    var expandedCardBounds by remember { mutableStateOf<Rect?>(null) }

    val listState = rememberLazyListState()

    var isCompactAtTop by remember { mutableStateOf(false) }
    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }
    val isCompact = isListScrolled || isCompactAtTop

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val collapseThresholdPx = with(density) { 56.dp.toPx() }
    var collapseConsumedPx by remember { mutableFloatStateOf(0f) }
    var isAbsorbingCollapse by remember { mutableStateOf(false) }
    var lastCollapseTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isCompactAtTop) {
        if (!isCompactAtTop) {
            collapseConsumedPx = 0f
            isAbsorbingCollapse = false
        }
    }

    LaunchedEffect(isCompact) {
        if (isCompact && expandedPresetId != null) {
            expandedPresetId = null
        }
    }

    val maxBouncePx = with(density) { 60.dp.toPx() }
    var bounceOffsetPx by remember { mutableFloatStateOf(0f) }
    var springJob by remember { mutableStateOf<Job?>(null) }

    fun triggerSpringBack() {
        if (bounceOffsetPx < -0.5f) {
            springJob?.cancel()
            springJob = scope.launch {
                Animatable(bounceOffsetPx).animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) {
                    bounceOffsetPx = value
                }
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && bounceOffsetPx < -0.5f && springJob?.isActive != true) {
            triggerSpringBack()
        }
    }

    val nestedScrollConnection = remember(density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user touches and drags while spring animation is running, cancel it
                if (source == NestedScrollSource.UserInput && springJob?.isActive == true) {
                    springJob?.cancel()
                    springJob = null
                }

                val now = System.currentTimeMillis()
                if (isAbsorbingCollapse && now - lastCollapseTime > 400L) {
                    isAbsorbingCollapse = false
                    collapseConsumedPx = 0f
                }

                // If at the top of the list and user pulls down, immediately expand!
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    if (isAbsorbingCollapse) {
                        isAbsorbingCollapse = false
                        collapseConsumedPx = 0f
                    }
                    if (isCompactAtTop && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        isCompactAtTop = false
                        collapseConsumedPx = 0f
                        return Offset(0f, available.y)
                    }
                }

                // If at the top of the list and user swipes up while expanded:
                // Absorb the swipe to collapse presets; session list does NOT move upward!
                if (source == NestedScrollSource.UserInput && available.y < 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    if (!isCompactAtTop && !isAbsorbingCollapse) {
                        isAbsorbingCollapse = true
                        collapseConsumedPx = 0f
                        isCompactAtTop = true
                    }
                }

                if (isAbsorbingCollapse && available.y < 0f) {
                    lastCollapseTime = now
                    val remaining = (collapseThresholdPx - collapseConsumedPx).coerceAtLeast(0f)
                    if (remaining > 0f) {
                        val toConsume = available.y.coerceAtLeast(-remaining)
                        collapseConsumedPx += -toConsume
                        return Offset(0f, toConsume)
                    }
                }

                // If currently overscrolled at bottom (pulled up) and user drags back down,
                // consume downward scroll to bring bounce offset back to 0 before scrolling the list
                if (bounceOffsetPx < 0f && available.y > 0f) {
                    val consumedY = available.y.coerceAtMost(-bounceOffsetPx)
                    bounceOffsetPx += consumedY
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (abs(available.y) > abs(available.x)) {
                    // 1. Pull down at top: immediately expand presets bar
                    if (available.y > 0f && isCompactAtTop) {
                        isCompactAtTop = false
                    }
                    // 2. Collapse presets when content is short and user swipes up
                    else if (available.y < -10f && !isCompactAtTop) {
                        isCompactAtTop = true
                    }

                    // 3. iOS-style rubber-band overscroll when pulled up at the bottom
                    if (source == NestedScrollSource.UserInput && available.y < 0f) {
                        val damping = (1f - (-bounceOffsetPx / maxBouncePx).coerceIn(0f, 0.85f)) * 0.35f
                        val delta = available.y * damping
                        val newOffset = (bounceOffsetPx + delta).coerceIn(-maxBouncePx, 0f)
                        val consumedDelta = newOffset - bounceOffsetPx
                        bounceOffsetPx = newOffset
                        return Offset(0f, if (damping > 0.01f) consumedDelta / damping else available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (bounceOffsetPx < -0.5f) {
                    triggerSpringBack()
                    return available
                }
                if (isAbsorbingCollapse) {
                    isAbsorbingCollapse = false
                    collapseConsumedPx = 0f
                    // If the list is still at top (light short swipe/flick), swallow the fling completely!
                    if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        return available
                    }
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isAbsorbingCollapse = false
                collapseConsumedPx = 0f
                if (abs(available.y) > abs(available.x)) {
                    if (available.y < -100f && !isCompactAtTop) {
                        isCompactAtTop = true
                    } else if (available.y > 40f && isCompactAtTop) {
                        isCompactAtTop = false
                    }
                }
                if (bounceOffsetPx < -0.5f) {
                    triggerSpringBack()
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GeekColors.DeepCanvas)
            .nestedScroll(nestedScrollConnection)
            .pointerInput(expandedPresetId) {
                if (expandedPresetId == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed }
                        if (down != null) {
                            val pos = down.position
                            val bounds = expandedCardBounds
                            if (bounds != null && !bounds.contains(pos)) {
                                expandedPresetId = null
                                down.consume()
                            }
                        }
                    }
                }
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // App Hero Header (Apple iOS Large Title style)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Apple App Icon squircle badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeekColors.CardSurface,
                    border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = ">_",
                            color = GeekColors.BrandAccent,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "终端会话",
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
                                text = "会话中心",
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
                        text = "智能工作台 · 远程终端控制与会话管理",
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

        // Error message if any
        if (error != null) {
            ErrorCard(
                message = error!!,
                onRetry = if (error!!.contains("不在线") || error!!.contains("agent") || error!!.contains("断开")) {
                    { repository.refresh() }
                } else null,
                onDismiss = { repository.clearError() },
            )
        }

        // Presets horizontal quick launcher
        if (presets.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .pointerInput(isCompact) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) {
                                    val deltaY = change.position.y - change.previousPosition.y
                                    if (deltaY > 5f && isCompact) {
                                        isCompactAtTop = false
                                        collapseConsumedPx = 0f
                                        isAbsorbingCollapse = false
                                        if (isListScrolled) {
                                            scope.launch { listState.animateScrollToItem(0) }
                                        }
                                    } else if (deltaY < -5f && !isCompact && listState.firstVisibleItemIndex == 0) {
                                        isCompactAtTop = true
                                        collapseConsumedPx = collapseThresholdPx
                                        isAbsorbingCollapse = false
                                    }
                                }
                            }
                        }
                    },
            ) {
                QuickPresetsRow(
                    presets = presets,
                    expandedPresetId = expandedPresetId,
                    isCompact = isCompact,
                    onExpandedPresetChange = { expandedPresetId = it },
                    onCardBoundsChange = { expandedCardBounds = it },
                    onCompactToggle = {
                        if (isCompact) {
                            isCompactAtTop = false
                            collapseConsumedPx = 0f
                            isAbsorbingCollapse = false
                            scope.launch { listState.animateScrollToItem(0) }
                        } else {
                            isCompactAtTop = true
                            collapseConsumedPx = collapseThresholdPx
                            isAbsorbingCollapse = false
                        }
                    },
                ) { preset, startInGui ->
                    repository.createSession(
                        presetId = preset.id,
                        cols = cols,
                        rows = rows,
                        cwd = launchCwd.takeIf { it.isNotBlank() },
                        startInGui = startInGui,
                    )
                }
            }
        }

        // Main Sessions List with strict viewport boundary clipping
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = bounceOffsetPx },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
            // Running Section
            item {
                SectionHeader("运行中的会话", count = running.size, isActive = true)
            }

            if (running.isEmpty()) {
                item {
                    EmptyStateCard("暂无运行中的会话。点击上方「Pi Agent」可立即发起全新对话。")
                }
            }

            items(running, key = { "run-${it.sid}" }) { session ->
                RunningSessionCard(
                    session = session,
                    onOpen = { repository.openSession(session.sid) },
                    onKill = { repository.killSession(session.sid) },
                )
            }

            // Ended Section
            if (ended.isNotEmpty()) {
                item {
                    SectionHeader("历史会话", count = ended.size, isActive = false)
                }

                item {
                    EndedSessionsGroupCard(
                        sessions = ended,
                        onOpen = { session ->
                            if (repository.isSessionMissingOnPc(session.sid)) {
                                repository.promptMissingSession(session)
                            } else {
                                repository.openSession(session.sid)
                            }
                        },
                        onDelete = { session -> repository.deleteSessionWithPc(session.sid) },
                    )
                }
            }
        }
    }

    if (missingSession != null) {
        val session = missingSession!!
        val isAi = isPiPresetName(session.name) || isPiPresetName(session.preset) || session.kind == "rpc"
        val rawName = if (isPiPresetName(session.name)) "Pi Agent" else session.name.ifBlank { session.sid.take(8) }
        val displayName = if (rawName.length > 20) rawName.take(20) + "…" else rawName

        AlertDialog(
            onDismissRequest = { repository.dismissMissingSessionPrompt() },
            shape = RoundedCornerShape(20.dp),
            containerColor = GeekColors.CardElevated,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = GeekColors.AmberWarn,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "会话在电脑上已不存在",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.TextPrimary,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Session card capsule
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GeekColors.CardSurface,
                        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "会话",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextMuted,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = GeekColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isAi) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GeekColors.CardElevated,
                                ) {
                                    Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = GeekColors.TextMuted,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Multi-line structured explanation
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• 电脑端当前未找到该会话记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextSecondary,
                        )
                        Text(
                            text = "• 可能已在电脑端结束，或 Agent 服务曾重启",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeekColors.TextMuted,
                        )
                    }

                    // Action question
                    Text(
                        text = "是否从历史列表中移除该记录？",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = GeekColors.TextPrimary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.forgetSession(session.sid)
                        repository.dismissMissingSessionPrompt()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeekColors.RoseError,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.pressClickEffect(),
                ) {
                    Text("移除", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { repository.dismissMissingSessionPrompt() },
                ) {
                    Text("取消", color = GeekColors.TextMuted)
                }
            },
        )
    }

    if (showAgentConfigDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val isConnected = connection is ConnectionState.Connected
        AgentConfigDialog(
            targetEnvironmentName = "电脑端 (PC)",
            onDismiss = { showAgentConfigDialog = false },
            onInstall = { title, cmd ->
                showAgentConfigDialog = false
                if (!isConnected) {
                    Toast.makeText(context, "电脑端尚未连接，请先在电脑启动 Agent 或检查中继！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "正在为电脑端启动: $title", Toast.LENGTH_SHORT).show()
                    val shellPreset = presets.firstOrNull { it.id.contains("ps", true) || it.id.contains("bash", true) }?.id
                        ?: presets.firstOrNull()?.id ?: "ps"
                    repository.createSession(
                        presetId = shellPreset,
                        cols = cols,
                        rows = rows,
                        cwd = launchCwd.takeIf { it.isNotBlank() },
                        command = cmd,
                        startInGui = false,
                    )
                }
            },
        )
        }
    }
}

// ------------------------------------------------------------- UI Subcomponents

/**
 * Modern Presets Quick Launcher with Pi Agent Hero options.
 */
@Composable
fun QuickPresetsRow(
    presets: List<ControlMessage.Preset>,
    expandedPresetId: String? = null,
    isCompact: Boolean = false,
    onExpandedPresetChange: (String?) -> Unit = {},
    onCardBoundsChange: (Rect?) -> Unit = {},
    onCompactToggle: (() -> Unit)? = null,
    onLaunch: (preset: ControlMessage.Preset, startInGui: Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (onCompactToggle != null) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onCompactToggle() }
                    } else Modifier
                )
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "新建会话",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    fontSize = 12.sp,
                ),
                color = GeekColors.TextMuted,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCompact) "展开" else "收起",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = GeekColors.TextMuted.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (isCompact) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isCompact) "展开" else "收起",
                    tint = GeekColors.TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        val visiblePresets = presets.filter {
            !it.id.equals("pi-c", ignoreCase = true) && !it.id.equals("pi-r", ignoreCase = true) &&
                !it.id.equals("pi-gui", ignoreCase = true)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            for (preset in visiblePresets) {
                key(preset.id) {
                    QuickPresetCard(
                        preset = preset,
                        allPresets = presets,
                        isExpanded = isPiPreset(preset) && expandedPresetId == preset.id,
                        isCompact = isCompact,
                        onExpandedPresetChange = onExpandedPresetChange,
                        onCardBoundsChange = onCardBoundsChange,
                        onLaunch = onLaunch,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickPresetCard(
    preset: ControlMessage.Preset,
    allPresets: List<ControlMessage.Preset>,
    isExpanded: Boolean,
    isCompact: Boolean,
    onExpandedPresetChange: (String?) -> Unit,
    onCardBoundsChange: (Rect?) -> Unit,
    onLaunch: (preset: ControlMessage.Preset, startInGui: Boolean?) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val isPi = isPiPreset(preset)
    val isPwsh = isPwshPreset(preset)
    val displayName = if (isPi) {
        "Pi Agent"
    } else if (isPwsh && (preset.name.isBlank() || preset.name.equals("pwsh", ignoreCase = true))) {
        "Pwsh 7"
    } else {
        preset.name
    }

    // Rectangular card: constant 16dp rounded corner
    val shape = RoundedCornerShape(16.dp)

    // Target dimensions
    val targetWidth = when {
        isExpanded -> 235.dp
        isCompact -> 170.dp
        else -> 140.dp
    }
    val targetHeight = when {
        isExpanded -> 160.dp
        isCompact -> 52.dp
        else -> 126.dp
    }

    val cardWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardWidth",
    )
    val cardHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardHeight",
    )

    val cardBgColor = if (GeekColors.isDark) Color(0xFF19202D) else Color(0xFFF2F4F7)
    val rippleColor = if (GeekColors.isDark) Color(0x38D1D5DB) else Color(0x286B7280)
    val presetInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(cardBgColor, shape)
            .clip(shape)
            .onGloballyPositioned { coords ->
                if (isExpanded) {
                    onCardBoundsChange(coords.boundsInRoot())
                }
            }
            .then(
                if (!isExpanded) {
                    Modifier.clickable(
                        interactionSource = presetInteraction,
                        indication = if (isPwsh || !isPi) ripple(color = rippleColor) else null,
                    ) {
                        if (isPi) {
                            onExpandedPresetChange(preset.id)
                        } else {
                            coroutineScope.launch {
                                delay(120)
                                onLaunch(preset, null)
                            }
                        }
                    }
                } else Modifier
            ),
    ) {
        // 1. Left icon with squircle container (always top-left: x = 11.dp, y = 10.dp, size = 32.dp)
        Box(
            modifier = Modifier
                .padding(start = 11.dp, top = 10.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isPi) Color(0xFFF09082).copy(alpha = 0.14f)
                    else if (isPwsh) Color(0xFF264973).copy(alpha = 0.16f)
                    else GeekColors.CardHighlight
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isPi) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_preset_pi),
                    contentDescription = "Pi Agent",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp),
                )
            } else if (isPwsh) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_preset_pwsh),
                    contentDescription = "PowerShell",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = GeekColors.BrandAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 2. Right side: Close button when expanded, Badge when collapsed (slides right naturally with card width)
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 11.dp, top = 10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(GeekColors.CardHighlight)
                    .clickable { onExpandedPresetChange(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "收起",
                    tint = GeekColors.TextMuted,
                    modifier = Modifier.size(15.dp),
                )
            }
        } else {
            val badgeText = if (isPi) "双视图" else if (isPwsh) "CLI" else "终端"
            val badgeBg = if (isPi) {
                GeekColors.BrandPurple.copy(alpha = 0.14f)
            } else if (isPwsh) {
                GeekColors.BrandAccent.copy(alpha = 0.12f)
            } else {
                GeekColors.CardHighlight
            }
            val badgeTint = if (isPi) {
                GeekColors.BrandPurple
            } else if (isPwsh) {
                GeekColors.BrandAccent
            } else {
                GeekColors.TextMuted
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 11.dp, top = 14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = badgeTint,
                )
            }
        }

        // 3. Title: smoothly glides from bottom-left (11dp, 56dp) to icon's right (51dp, 17dp)
        val titleX by animateDpAsState(
            targetValue = if (isCompact || isExpanded) 51.dp else 11.dp,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "titleX",
        )
        val titleY by animateDpAsState(
            targetValue = if (isCompact || isExpanded) 17.dp else 56.dp,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "titleY",
        )

        val maxTitleWidth = when {
            isExpanded -> 135.dp
            isCompact -> (cardWidth - 51.dp - 56.dp).coerceAtLeast(60.dp)
            else -> 118.dp
        }

        Text(
            text = displayName,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            color = GeekColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = titleX, y = titleY)
                .widthIn(max = maxTitleWidth),
        )

        // 4. Tagline & CWD (fade out when compact or expanded)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isCompact && !isExpanded,
            enter = fadeIn(animationSpec = tween(160, delayMillis = 40)),
            exit = fadeOut(animationSpec = tween(80)),
            modifier = Modifier
                .padding(start = 11.dp, top = 78.dp, end = 11.dp)
                .fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val tagline = if (isPi) {
                    "极简 Agent Harness"
                } else if (isPwsh) {
                    "Windows 命令行"
                } else {
                    preset.description.ifBlank { "远程终端环境" }
                }
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                    ),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val displayCwd = preset.cwd.ifBlank { "默认工作区" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = GeekColors.TextMuted,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = displayCwd,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = GeekColors.TextMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                            ),
                    )
                }
            }
        }

        // 5. Expanded mode options: EXACT ORIGINAL ANIMATION FOR 图形视图 AND 终端视图
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(220, delayMillis = 60)) +
                expandVertically(animationSpec = tween(240, delayMillis = 40)),
            exit = fadeOut(animationSpec = tween(100)) +
                shrinkVertically(animationSpec = tween(140)),
            modifier = Modifier
                .padding(start = 11.dp, top = 48.dp, end = 11.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PiModeOptionCard(
                    icon = Icons.Default.Chat,
                    iconTint = GeekColors.BrandPurple,
                    title = "图形视图",
                    subtitle = "结构化事件流 · 推荐",
                    tag = "GUI",
                    tagColor = GeekColors.BrandPurple,
                    onClick = {
                        val piPreset = allPresets.find { it.id.equals("pi-gui", ignoreCase = true) }
                            ?: allPresets.find { it.id.equals("pi", ignoreCase = true) }
                            ?: preset
                        onLaunch(piPreset, true)
                    },
                )
                PiModeOptionCard(
                    icon = Icons.Default.Terminal,
                    iconTint = GeekColors.TerminalCyan,
                    title = "终端视图",
                    subtitle = "底层 PTY 原生命令行",
                    tag = "PTY",
                    tagColor = GeekColors.TerminalCyan,
                    onClick = {
                        val piPreset = allPresets.find { it.id.equals("pi", ignoreCase = true) } ?: preset
                        onLaunch(piPreset, false)
                    },
                )
            }
        }
    }
}


@Composable
fun PiModeOptionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    tag: String,
    tagColor: Color,
    onClick: () -> Unit,
) {
    val optionBgColor = if (GeekColors.isDark) Color(0xFF222B3B) else Color.White
    val rippleColor = if (GeekColors.isDark) Color(0x38D1D5DB) else Color(0x286B7280)
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(optionBgColor, shape)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = rippleColor),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                        color = GeekColors.TextPrimary,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagColor.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                            ),
                            color = tagColor,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                    ),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GeekColors.TextMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = GeekColors.TextMuted,
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isActive && count > 0) GeekColors.BrandAccentGlow else GeekColors.CardSurface,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (isActive && count > 0) GeekColors.BrandAccent else GeekColors.TextMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Adaptive Running Session Card.
 */
@Composable
fun RunningSessionCard(
    session: StoredSession,
    onOpen: () -> Unit,
    onKill: () -> Unit,
) {
    val isAi = isPiPresetName(session.name) || isPiPresetName(session.preset) || session.kind == "rpc"
    val displayName = if (isPiPresetName(session.name)) "Pi Agent" else session.name.ifBlank { session.sid.take(8) }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .pressClickEffect(),
        shape = shape,
        color = GeekColors.CardSurface,
        border = null,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left pulse indicator
            PulsingDot(
                color = if (isAi) GeekColors.BrandPurple else GeekColors.BrandAccent,
                glowColor = if (isAi) GeekColors.BrandPurpleGlow else GeekColors.BrandAccentGlow,
            )
            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GeekColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = session.cwd.ifBlank { "默认工作目录" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GeekSmallButton(
                    label = "打开",
                    containerColor = GeekColors.BrandAccent,
                    contentColor = Color.White,
                    borderColor = GeekColors.BrandAccent,
                    onClick = onOpen,
                )
                GeekSmallButton(
                    label = "终止",
                    containerColor = GeekColors.CardElevated,
                    contentColor = GeekColors.RoseError,
                    borderColor = GeekColors.BorderSubtle,
                    onClick = onKill,
                )
            }
        }
    }
}

/**
 * Adaptive Grouped Ended Sessions Card.
 */
@Composable
fun EndedSessionsGroupCard(
    sessions: List<StoredSession>,
    onOpen: (StoredSession) -> Unit,
    onDelete: (StoredSession) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .alpha(0.9f),
        shape = shape,
        color = GeekColors.CardSurface,
        border = BorderStroke(0.6.dp, GeekColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            sessions.forEachIndexed { index, session ->
                if (index > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .height(1.1.dp)
                            .background(GeekColors.TextMuted.copy(alpha = 0.3f)),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                EndedSessionRow(
                    session = session,
                    onOpen = { onOpen(session) },
                    onDelete = { onDelete(session) },
                )
            }
        }
    }
}

@Composable
fun EndedSessionRow(
    session: StoredSession,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val isAi = isPiPresetName(session.name) || isPiPresetName(session.preset) || session.kind == "rpc"
    val displayName = if (isPiPresetName(session.name)) "Pi Agent" else session.name.ifBlank { session.sid.take(8) }
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressClickEffect()
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = GeekColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GeekColors.CardElevated,
                ) {
                    Text(
                        text = if (isAi) "AI" else "Shell",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${session.cwd.ifBlank { "默认工作目录" }} · ${relativeTime(session.lastSeenAt)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                color = GeekColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GeekSmallButton("查看", onClick = onOpen)
            GeekSmallButton("删除", onClick = { showConfirm = true })
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = GeekColors.CardElevated,
            title = {
                Text(
                    text = "删除历史会话",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextPrimary,
                )
            },
            text = {
                Text(
                    text = "确定要删除会话「$displayName」吗？\n此操作将从历史列表中移除，并同步通知电脑端终端彻底删除该会话持久化文件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeekColors.TextSecondary,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onDelete()
                    },
                ) {
                    Text("彻底删除", color = GeekColors.RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("取消", color = GeekColors.TextMuted)
                }
            },
        )
    }
}

@Composable
fun PresetBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = GeekColors.CardElevated,
        border = BorderStroke(0.5.dp, GeekColors.BorderHighlight),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = GeekColors.TerminalCyan,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun KindBadge(text: String, isRpc: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isRpc) GeekColors.BrandPurpleGlow else GeekColors.CardElevated,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (isRpc) GeekColors.BrandPurple else GeekColors.TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun EmptyStateCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GeekColors.CardSurface,
        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = GeekColors.TextMuted,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = GeekColors.TextMuted,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x1AF43F5E),
        border = BorderStroke(1.dp, GeekColors.RoseErrorGlow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                color = GeekColors.RoseError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (onRetry != null) {
                GeekSmallButton(
                    label = "重试",
                    onClick = onRetry,
                    containerColor = GeekColors.BrandAccent,
                    contentColor = Color.White,
                    borderColor = Color.Transparent,
                )
                Spacer(Modifier.width(6.dp))
            }
            GeekSmallButton(
                label = "关闭",
                onClick = onDismiss,
                containerColor = GeekColors.CardElevated,
                contentColor = GeekColors.TextSecondary,
                borderColor = Color.Transparent,
            )
        }
    }
}

@Composable
fun GeekSmallButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color = GeekColors.CardElevated,
    contentColor: Color = GeekColors.TextPrimary,
    borderColor: Color = Color.Transparent,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = containerColor,
        border = if (borderColor != Color.Transparent) BorderStroke(0.6.dp, borderColor) else null,
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
fun GeekPrimaryButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = GeekColors.BrandAccent,
        modifier = Modifier
            .clip(shape)
            .pressClickEffect(),
        onClick = onClick,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}

private fun isPiPreset(preset: ControlMessage.Preset): Boolean {
    return preset.id.equals("pi", ignoreCase = true) ||
           preset.name.contains("pi agent", ignoreCase = true) ||
           preset.name.trim().equals("pi", ignoreCase = true)
}

private fun isPwshPreset(preset: ControlMessage.Preset): Boolean {
    return preset.id.contains("pwsh", ignoreCase = true) ||
           preset.name.contains("powershell", ignoreCase = true) ||
           preset.name.contains("pwsh", ignoreCase = true)
}

private fun isPiPresetName(name: String): Boolean {
    return name.equals("pi", ignoreCase = true) ||
           name.equals("pi-c", ignoreCase = true) ||
           name.equals("pi-r", ignoreCase = true) ||
           name.contains("pi agent", ignoreCase = true)
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "刚刚"
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000} 小时前"
        else -> "${diff / 86_400_000} 天前"
    }
}
