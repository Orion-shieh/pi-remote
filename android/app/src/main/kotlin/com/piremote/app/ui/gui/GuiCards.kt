package com.piremote.app.ui.gui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import com.piremote.app.ui.GeekColors
import com.piremote.app.ui.LocalGuiFontScale
import com.piremote.app.ui.pressClickEffect

// Rough width model of the 0.85-width user bubble in body-medium text:
// one visual line ≈ 18 CJK characters ≈ 36 width units (CJK = 2, ASCII = 1).
private const val USER_BUBBLE_LINE_UNITS = 36

private fun charUnits(ch: Char): Int = if (ch.code >= 0x2E80) 2 else 1

private fun lineUnits(line: String): Int = line.sumOf { charUnits(it) }.coerceAtLeast(1)

private fun estimatedVisualLines(text: String): Int =
    text.lines().sumOf { line -> (lineUnits(line) + USER_BUBBLE_LINE_UNITS - 1) / USER_BUBBLE_LINE_UNITS }

/** Truncates the text to roughly [maxLines] visual lines, cutting mid-paragraph. */
private fun previewWithinVisualLines(text: String, maxLines: Int): String {
    var budget = maxLines * USER_BUBBLE_LINE_UNITS
    val sb = StringBuilder()
    for (line in text.lines()) {
        if (budget <= 0) break
        val units = lineUnits(line)
        if (units <= budget) {
            sb.append(line).append('\n')
            budget -= (units + USER_BUBBLE_LINE_UNITS - 1) / USER_BUBBLE_LINE_UNITS * USER_BUBBLE_LINE_UNITS
        } else {
            var acc = 0
            for (ch in line) {
                val w = charUnits(ch)
                if (acc + w > budget) break
                sb.append(ch)
                acc += w
            }
            sb.append('…')
            budget = 0
        }
    }
    return sb.toString().trimEnd()
}

/**
 * Bubble displaying the user's prompt, right-aligned with the time below.
 * Long prompts (more than ~10 rendered lines, counting wraps — not just
 * newline characters) auto-collapse to a preview with an expand/collapse
 * toggle inside the bubble. Tapping the bubble toggles it; [onCollapsed]
 * lets the host scroll the list back to this message after collapsing.
 */
@Composable
fun UserMessageBubble(
    message: UserMessage,
    modifier: Modifier = Modifier,
    onCollapsed: (() -> Unit)? = null,
    onCollapsedWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
    onFork: ((UserMessage) -> Unit)? = null,
    onNewSession: ((UserMessage) -> Unit)? = null,
) {
    val bubbleShape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
    val estimatedLines = remember(message.id) { estimatedVisualLines(message.text) }
    val isLong = estimatedLines > 10
    var expanded by remember(message.id) { mutableStateOf(false) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isDark = GeekColors.isDark
    val context = LocalContext.current

    val userBubbleBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF0071E3)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF0062D2)))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        val timeStr = remember(message.timestamp) { formatTime(message.timestamp) }
        val modelStr = message.model?.trim()
        val providerStr = message.provider?.trim()
        val displayModel = remember(modelStr, providerStr) {
            val cleanModel = stripProviderPrefix(modelStr, providerStr)?.trim() ?: modelStr
            when {
                !cleanModel.isNullOrEmpty() && !providerStr.isNullOrEmpty() -> "$cleanModel ($providerStr)"
                !cleanModel.isNullOrEmpty() -> cleanModel
                !providerStr.isNullOrEmpty() -> "($providerStr)"
                else -> null
            }
        }
        val isBtwMessage = message.sendType == "btw" ||
            message.text.startsWith("/btw", ignoreCase = true) ||
            message.text.startsWith("/side", ignoreCase = true) ||
            message.text.startsWith("【旁支提问", ignoreCase = true) ||
            message.text.startsWith("[btw", ignoreCase = true)
        if (timeStr.isNotEmpty() || !displayModel.isNullOrEmpty() || isBtwMessage) {
            Row(
                modifier = Modifier.padding(bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isBtwMessage) {
                    Text(
                        text = "BTW 旁支提问",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.TerminalCyan,
                    )
                } else if (message.sendType == "steer") {
                    Text(
                        text = "⚡ 实时引导 (Steer)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.AmberWarn,
                    )
                } else if (message.sendType == "follow_up") {
                    Text(
                        text = "⏱️ 排队跟进 (Follow-up)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GeekColors.TerminalCyan,
                    )
                }
                if (!displayModel.isNullOrEmpty()) {
                    Text(
                        text = displayModel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = GeekColors.TextMuted,
                    )
                }
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = GeekColors.TextMuted,
                    )
                }
            }
        }

        Surface(
            shape = bubbleShape,
            color = Color.Transparent,
            shadowElevation = if (isDark) 4.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(bubbleShape)
                .background(userBubbleBrush)
                .onGloballyPositioned { cardCoordinates = it }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    expanded = !expanded
                    if (!expanded) {
                        if (onCollapsedWithCoords != null) {
                            onCollapsedWithCoords.invoke(cardCoordinates)
                        } else {
                            onCollapsed?.invoke()
                        }
                    }
                },
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val visibleText = if (!isLong || expanded) message.text
                else previewWithinVisualLines(message.text, 10)
                MarkdownContentView(
                    content = visibleText,
                    customTextColor = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isLong) {
                    Text(
                        text = if (expanded) "收起 ▴" else "展开剩余约 ${estimatedLines - 10} 行 ▾",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // Action buttons row below user bubble: Fork, Tree, Edit, Copy
        Row(
            modifier = Modifier.padding(top = 7.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.5.dp),
        ) {
            if (onFork != null) {
                CardActionButton(
                    icon = Icons.Default.AltRoute,
                    label = "分叉",
                    onClick = { onFork.invoke(message) },
                )
            }
            if (onEdit != null) {
                CardActionButton(
                    icon = Icons.Default.Edit,
                    label = "编辑",
                    onClick = { onEdit.invoke(message.text) },
                )
            }
            if (onNewSession != null && onFork == null) {
                CardActionButton(
                    icon = Icons.Default.Add,
                    label = "新会话",
                    onClick = { onNewSession.invoke(message) },
                )
            }
            var isCopied by remember { mutableStateOf(false) }
            LaunchedEffect(isCopied) {
                if (isCopied) {
                    delay(2000)
                    isCopied = false
                }
            }
            CardActionButton(
                icon = Icons.Default.ContentCopy,
                activeIcon = Icons.Default.Check,
                isActive = isCopied,
                label = if (isCopied) "已复制" else "复制",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("user_message", message.text))
                    Toast.makeText(context, "已复制消息内容", Toast.LENGTH_SHORT).show()
                    isCopied = true
                },
            )
        }
    }
}

/**
 * Universal compact action button matching AI response card footer style.
 */
@Composable
fun CardActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeIcon: ImageVector? = null,
    isActive: Boolean = false,
    activeColor: Color = GeekColors.NeonGreen,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shape = RoundedCornerShape(4.dp)
    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor.copy(alpha = 0.12f)
            isPressed -> if (GeekColors.isDark) Color(0x18FFFFFF) else Color(0x0C000000)
            else -> if (GeekColors.isDark) Color(0x0AFFFFFF) else Color(0x05000000)
        },
        animationSpec = tween(120),
        label = "cardActionBtnBg",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            isPressed -> GeekColors.TextPrimary
            else -> GeekColors.TextSecondary
        },
        animationSpec = tween(120),
        label = "cardActionBtnContent",
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .pressClickEffect()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (isActive && activeIcon != null) activeIcon else icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(9.5.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            color = contentColor,
        )
    }
}

/**
 * Confirmation dialog shown before executing a session fork (/fork).
 * Explains the meaning of forking from this node and gives clear structured guidance,
 * matching the style of the missing session dialog in Screens.kt.
 */
enum class ForkSummaryMode {
    NO_SUMMARY,
    SUMMARIZE,
    CUSTOM_PROMPT,
}

/**
 * Confirmation dialog shown before executing a session fork (/fork).
 * Integrates Pi's native Session Tree "Summarize branch?" workflow with:
 * - No summary (direct branch)
 * - Summarize (auto LLM context compression)
 * - Summarize with custom prompt
 */
@Composable
fun ForkConfirmDialog(
    previewText: String,
    onConfirm: (() -> Unit)? = null,
    onConfirmWithSummary: ((ForkSummaryMode, String?) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var summaryMode by remember { mutableStateOf(ForkSummaryMode.NO_SUMMARY) }
    var customPromptText by remember { mutableStateOf("") }

    val cleanPreview = remember(previewText) {
        val trimmed = previewText.trim().replace('\n', ' ')
        if (trimmed.length > 28) trimmed.take(28) + "…" else trimmed.ifBlank { "当前提问节点" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = GeekColors.CardElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = GeekColors.BrandAccent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "从该节点分叉会话",
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
                // Session/node card capsule
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
                            text = "节点",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GeekColors.TextMuted,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = cleanPreview,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GeekColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    text = "离开分支总结 (Summarize branch?)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = GeekColors.TextPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "您正准备离开当前分支切入该节点。是否需要将刚才分支中尝试的探索、经验与改动成果进行提炼？",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = GeekColors.TextMuted,
                )

                // Option 1: No summary
                ForkOptionCard(
                    title = "不总结离开的分支 (No summary)",
                    subtitle = "直接切换至目标节点，不将刚才离开分支的探索过程带入新分支",
                    selected = (summaryMode == ForkSummaryMode.NO_SUMMARY),
                    onClick = { summaryMode = ForkSummaryMode.NO_SUMMARY },
                )

                // Option 2: Summarize
                ForkOptionCard(
                    title = "总结离开分支并带入 (Summarize)",
                    subtitle = "由 AI 提炼刚才离开分支的经验教训、探索成果与改动，注入新分支作为精炼背景",
                    selected = (summaryMode == ForkSummaryMode.SUMMARIZE),
                    onClick = { summaryMode = ForkSummaryMode.SUMMARIZE },
                )

                // Option 3: Summarize with custom prompt
                ForkOptionCard(
                    title = "自定义指令总结离开分支 (Custom prompt)",
                    subtitle = "指定对刚才离开分支的提炼重点（如仅提取排查出的 Bug 根因与修复方案）",
                    selected = (summaryMode == ForkSummaryMode.CUSTOM_PROMPT),
                    onClick = { summaryMode = ForkSummaryMode.CUSTOM_PROMPT },
                )

                AnimatedVisibility(
                    visible = (summaryMode == ForkSummaryMode.CUSTOM_PROMPT),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    OutlinedTextField(
                        value = customPromptText,
                        onValueChange = { customPromptText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = {
                            Text(
                                text = "输入对离开分支的提炼要求（例如：提取刚才分支排查出的根因）",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeekColors.TextMuted,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = GeekColors.TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeekColors.BrandAccent,
                            unfocusedBorderColor = GeekColors.BorderSubtle,
                            focusedContainerColor = GeekColors.CardSurface,
                            unfocusedContainerColor = GeekColors.CardSurface,
                        ),
                        maxLines = 3,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onConfirmWithSummary != null) {
                        onConfirmWithSummary(summaryMode, customPromptText.trim().takeIf { it.isNotBlank() })
                    } else {
                        onConfirm?.invoke()
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeekColors.BrandAccent,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.pressClickEffect(),
            ) {
                Text("确认分叉并切换", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = GeekColors.TextMuted)
            }
        },
    )
}

@Composable
private fun ForkOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) GeekColors.BrandAccentGlow else GeekColors.CardSurface,
        border = BorderStroke(
            if (selected) 1.2.dp else 0.6.dp,
            if (selected) GeekColors.BrandAccent else GeekColors.BorderSubtle,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 5.dp else 1.2.dp,
                        color = if (selected) GeekColors.BrandAccent else GeekColors.TextMuted,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) GeekColors.BrandAccent else GeekColors.TextPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = GeekColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Collapsible Thinking/Reasoning block matching the compact minimalist card style.
 */
@Composable
fun ThinkingAccordionCard(
    block: ThinkingBlock,
    modifier: Modifier = Modifier,
    onCollapsed: (() -> Unit)? = null,
    onCollapsedWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cardShape = RoundedCornerShape(10.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBgColor by animateColorAsState(
        targetValue = if (isPressed) GeekColors.CardHighlight else if (GeekColors.isDark) Color(0xFF1B2230) else Color(0xFFFFFFFF),
        animationSpec = tween(120),
        label = "thinkingBgColor",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "thinkingChevronRotation",
    )

    Surface(
        shape = cardShape,
        color = cardBgColor,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .onGloballyPositioned { cardCoordinates = it }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val wasExpanded = isExpanded
                    isExpanded = !isExpanded
                    if (wasExpanded) {
                        if (onCollapsedWithCoords != null) {
                            onCollapsedWithCoords.invoke(cardCoordinates)
                        } else {
                            onCollapsed?.invoke()
                        }
                    }
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Header Row (clean minimalist style, naked icon without background)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = GeekColors.BrandPurple,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (block.isFinished) "思考过程" else "AI 正在思考...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = GeekColors.TextPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val durationStr = if (block.durationSeconds > 0) {
                        if (block.durationSeconds >= 1f) {
                            String.format(java.util.Locale.US, "%.1fs", block.durationSeconds)
                        } else {
                            "${(block.durationSeconds * 1000).toInt()}ms"
                        }
                    } else if (!block.isFinished) {
                        "思考中..."
                    } else {
                        ""
                    }
                    if (durationStr.isNotEmpty()) {
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = GeekColors.TextMuted,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GeekColors.TextMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            // Collapsible Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(140)) + expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (block.content.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GeekColors.DeepCanvas,
                            border = null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(14.dp)
                                        .background(GeekColors.BrandPurple.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = block.content,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 17.sp,
                                    ),
                                    color = GeekColors.TextSecondary,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "（无思考内容）",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = GeekColors.TextMuted,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card representing a tool call (bash command, read/edit file, etc.)
 * matching the compact minimalist collapsible card style.
 */
@Composable
fun ToolExecutionCard(
    tool: ToolCallBlock,
    modifier: Modifier = Modifier,
    onCollapsed: (() -> Unit)? = null,
    onCollapsedWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cardShape = RoundedCornerShape(10.dp)

    val (toolIcon, toolAccentColor) = when (tool.toolName.lowercase()) {
        "bash", "shell", "exec" -> Icons.Default.Terminal to GeekColors.TerminalCyan
        "read_file", "view_file", "read" -> Icons.Default.Description to GeekColors.BrandAccent
        "edit_file", "write_to_file", "edit", "write" -> Icons.Default.Edit to GeekColors.NeonGreen
        "grep_search", "find_files", "grep", "find" -> Icons.Default.Search to GeekColors.AmberWarn
        else -> Icons.Default.Build to GeekColors.TextSecondary
    }

    val commandPreview = remember(tool.arguments, tool.summary) {
        try {
            val obj = org.json.JSONObject(tool.arguments)
            obj.optString("command", "").ifEmpty {
                obj.optString("path", "").ifEmpty {
                    obj.optString("file_path", "").ifEmpty {
                        obj.optString("pattern", "").ifEmpty {
                            obj.optString("query", "")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            if (tool.summary.isNotBlank() && tool.summary != tool.toolName) {
                tool.summary
            } else {
                tool.arguments.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            }
        }.take(50)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBgColor by animateColorAsState(
        targetValue = if (isPressed) GeekColors.CardHighlight else if (GeekColors.isDark) Color(0xFF1B2230) else Color(0xFFFFFFFF),
        animationSpec = tween(120),
        label = "toolBgColor",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "toolChevronRotation",
    )

    Surface(
        shape = cardShape,
        color = cardBgColor,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .onGloballyPositioned { cardCoordinates = it }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val wasExpanded = expanded
                    expanded = !expanded
                    if (wasExpanded) {
                        if (onCollapsedWithCoords != null) {
                            onCollapsedWithCoords.invoke(cardCoordinates)
                        } else {
                            onCollapsed?.invoke()
                        }
                    }
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Header: Naked icon without background + tool name + command preview, status + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    val accentColor = if (tool.status == ToolStatus.ERROR) GeekColors.AmberWarn else toolAccentColor
                    Icon(
                        imageVector = toolIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tool.toolName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = if (tool.toolName.lowercase() in listOf("bash", "shell", "exec")) FontFamily.Monospace else FontFamily.Default,
                        ),
                        color = GeekColors.TextPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (commandPreview.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = commandPreview,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = GeekColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when (tool.status) {
                        ToolStatus.RUNNING -> "运行中..."
                        ToolStatus.SUCCESS -> "完成"
                        ToolStatus.ERROR -> "出错"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = when (tool.status) {
                            ToolStatus.ERROR -> GeekColors.AmberWarn
                            ToolStatus.RUNNING -> toolAccentColor
                            ToolStatus.SUCCESS -> GeekColors.TextMuted
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GeekColors.TextMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            // Arguments + output, only when the card is expanded
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(140)) + expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tool.arguments.isNotBlank() && tool.arguments != tool.summary) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GeekColors.DeepCanvas,
                            border = null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = if (tool.toolName.lowercase() in listOf("bash", "shell", "exec")) "$" else "❯",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = toolAccentColor,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                                Text(
                                    text = if (commandPreview.isNotBlank() && tool.arguments.trim().startsWith("{")) commandPreview else tool.arguments,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                    ),
                                    color = GeekColors.TextPrimary,
                                )
                            }
                        }
                    }

                    if (!tool.output.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GeekColors.DeepCanvas,
                            border = null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = tool.output,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    lineHeight = 16.sp,
                                ),
                                color = if (tool.status == ToolStatus.ERROR) GeekColors.AmberWarn else GeekColors.TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp),
                            )
                        }
                    } else if (tool.status != ToolStatus.RUNNING && (tool.arguments.isBlank() || tool.arguments == tool.summary)) {
                        Text(
                            text = "（无输出内容）",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                color = GeekColors.TextMuted,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The per-turn fold card: a bordered box whose clickable header (执行详情 ·
 * N 条消息 · M 次工具调用) expands and collapses the turn's thinking, tool
 * cards and intermediate notes INSIDE the box. The final reply renders after
 * the card.
 */
@Composable
fun TurnSummaryCard(
    turnId: String,
    messageCount: Int = 0,
    toolCount: Int = 0,
    live: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    thinkingCount: Int = messageCount,
    onToggleWithCoords: ((LayoutCoordinates?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val parts = buildList {
        if (thinkingCount > 0) add(if (thinkingCount == 1) "思考过程" else "${thinkingCount} 次思考")
        if (toolCount > 0) add("${toolCount} 次工具")
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBgColor by animateColorAsState(
        targetValue = if (isPressed) GeekColors.CardHighlight else GeekColors.CardSurface,
        animationSpec = tween(120),
        label = "turnCardBg",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "turnChevronRotation",
    )

    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        shape = cardShape,
        color = cardBgColor,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .onGloballyPositioned { cardCoordinates = it }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onToggle()
                    onToggleWithCoords?.invoke(cardCoordinates)
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = GeekColors.TextPrimary.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (parts.isEmpty()) "执行详情" else "执行详情 · ${parts.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = GeekColors.TextPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) {
                        Text(
                            text = "思考中...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = GeekColors.TextPrimary.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GeekColors.TextPrimary.copy(alpha = 0.50f),
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(140)) + expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Assistant Markdown message view with syntax-highlighted code blocks and copy action,
 * styled consistently with the phone terminal AI workbench card.
 */
@Composable
fun AssistantMessageView(
    response: AssistantResponse,
    modifier: Modifier = Modifier,
) {
    AssistantMessageView(
        text = response.text,
        usage = response.usage,
        isError = response.isError,
        modifier = modifier,
    )
}

@Composable
fun AssistantMessageView(
    text: String,
    usage: MessageUsage? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    val containerColor = if (isError) GeekColors.RoseError.copy(alpha = 0.12f) else GeekColors.CardSurface
    val borderColor = if (isError) BorderStroke(1.dp, GeekColors.RoseError.copy(alpha = 0.45f)) else null

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = borderColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                top = 10.dp,
                end = 12.dp,
                bottom = 10.dp,
            )
        ) {
            if (isError) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = GeekColors.RoseError,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "请求异常",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeekColors.RoseError,
                    )
                }
            }

            MarkdownContentView(
                content = text.trimEnd(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (usage != null && usage.hasUsage) {
                    AssistantUsageTokensRow(usage = usage)
                } else {
                    Spacer(modifier = Modifier.weight(1f, fill = false))
                }

                CopyResponseButton(text = text)
            }
        }
    }
}

/**
 * Prominent error card displayed when model invocation, gateway, or agent encounters an error
 * (e.g. 429 rate limit, gateway failure, network timeout).
 */
@Composable
fun AgentErrorMessageCard(
    item: ErrorMessageBlock,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GeekColors.RoseError.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, GeekColors.RoseError.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(GeekColors.RoseError.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "错误",
                        tint = GeekColors.RoseError,
                        modifier = Modifier.size(15.dp),
                    )
                }

                Text(
                    text = if (item.error.contains("429")) "API 速率限制 (429 Too Many Requests)"
                           else if (item.error.contains("Gateway", ignoreCase = true)) "网关请求异常 (Gateway Error)"
                           else "Agent 遇到错误",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = GeekColors.RoseError,
                )
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (GeekColors.isDark) Color(0x24000000) else Color(0x0C000000),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.error,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    ),
                    color = GeekColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }

            if (!item.details.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "来源: ${item.details}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = GeekColors.TextMuted,
                )
            }
        }
    }
}

private fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1_000.0)
        count > 0 -> String.format(java.util.Locale.US, "%,d", count)
        else -> "0"
    }
}

/**
 * Compact token usage stats for an assistant response turn.
 * Displays total input context tokens, output tokens, and prompt cache hit tokens with percentage.
 */
@Composable
fun AssistantUsageTokensRow(
    usage: MessageUsage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 输入 tokens（未命中/新增输入 tokens）
        TokenStatBadge(
            icon = Icons.Default.ArrowUpward,
            count = usage.inputTokens,
            tint = GeekColors.TextSecondary,
        )

        // 输出 tokens
        TokenStatBadge(
            icon = Icons.Default.ArrowDownward,
            count = usage.outputTokens,
            tint = GeekColors.NeonGreen,
        )

        // 缓存命中（带百分比，例如 49.6%）
        val cachePercentSuffix = if (usage.cacheHitPercentText.isNotEmpty()) " (${usage.cacheHitPercentText})" else ""
        TokenStatBadge(
            icon = Icons.Default.Bolt,
            count = usage.cacheReadTokens,
            suffix = cachePercentSuffix,
            tint = if (usage.cacheReadTokens > 0) GeekColors.TerminalCyan else GeekColors.TextMuted,
        )
    }
}

@Composable
private fun TokenStatBadge(
    icon: ImageVector,
    count: Int,
    tint: Color,
    label: String = "",
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (GeekColors.isDark) Color(0x0EFFFFFF) else Color(0x06000000))
            .padding(horizontal = 4.5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label.takeIf { it.isNotBlank() },
            tint = tint,
            modifier = Modifier.size(9.5.dp),
        )
        val textStr = if (label.isNotBlank()) "$label ${formatTokenCount(count)}$suffix" else "${formatTokenCount(count)}$suffix"
        Text(
            text = textStr,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            color = tint,
        )
    }
}

/**
 * Code block card with language header.
 */
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null,
) {
    val blockShape = RoundedCornerShape(12.dp)
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Surface(
        shape = blockShape,
        color = Color(0xFF16161A),
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(blockShape),
    ) {
        Column {
            // macOS Window Titlebar with Traffic Lights (🔴 🟡 🟢) & Copy action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))

                    if (language.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = language.lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                            color = GeekColors.TextSecondary,
                        )
                    }
                }

                // Copy button in titlebar
                val copyBgColor by animateColorAsState(
                    targetValue = if (isCopied) GeekColors.NeonGreen.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                    animationSpec = tween(140),
                    label = "codeCopyBg",
                )
                val copyContentColor by animateColorAsState(
                    targetValue = if (isCopied) GeekColors.NeonGreen else GeekColors.TextSecondary,
                    animationSpec = tween(140),
                    label = "codeCopyContent",
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(copyBgColor)
                        .pressClickEffect()
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("code", code))
                            onCopy?.invoke(code)
                            isCopied = true
                        }
                        .padding(horizontal = 4.5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = copyContentColor,
                        modifier = Modifier.size(9.5.dp),
                    )
                    Text(
                        text = "复制",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        ),
                        color = copyContentColor,
                    )
                }
            }

            // Code content
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                color = Color(0xFFF1F5F9),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

/**
 * Copy button for assistant responses or output blocks with visual feedback.
 * Compact low-profile button with comfortable padding and clean status label.
 */
@Composable
fun CopyResponseButton(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "复制",
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shape = RoundedCornerShape(4.dp)
    val bgColor by animateColorAsState(
        targetValue = when {
            isCopied -> GeekColors.NeonGreen.copy(alpha = 0.14f)
            isPressed -> if (GeekColors.isDark) Color(0x28FFFFFF) else Color(0x18000000)
            else -> if (GeekColors.isDark) Color(0x0EFFFFFF) else Color(0x06000000)
        },
        animationSpec = tween(120),
        label = "copyBtnBg",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isCopied -> GeekColors.NeonGreen
            isPressed -> GeekColors.TextPrimary
            else -> GeekColors.TextSecondary
        },
        animationSpec = tween(120),
        label = "copyBtnContent",
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .pressClickEffect()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("response", text))
                Toast.makeText(context, "已复制回答内容", Toast.LENGTH_SHORT).show()
                isCopied = true
            }
            .padding(horizontal = 4.5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(9.5.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            color = contentColor,
        )
    }
}

/**
 * Centered badge for system messages.
 */
@Composable
fun SystemStatusBadge(
    message: SystemStatusMessage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = GeekColors.CardElevated.copy(alpha = 0.7f),
            border = null,
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.labelSmall,
                color = GeekColors.TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// ------------------------------------------------------------- Helpers & Parsers

private sealed interface MarkdownSection {
    data class TextSection(val content: String) : MarkdownSection
    data class CodeBlockSection(val language: String, val code: String) : MarkdownSection
}

private fun parseMarkdownSections(text: String): List<MarkdownSection> {
    val results = mutableListOf<MarkdownSection>()
    val lines = text.lines()
    var inCodeBlock = false
    var currentLanguage = ""
    val currentBuffer = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // Ending code block
                results.add(MarkdownSection.CodeBlockSection(currentLanguage, currentBuffer.toString().trimEnd()))
                currentBuffer.clear()
                inCodeBlock = false
            } else {
                // Starting code block
                if (currentBuffer.isNotEmpty()) {
                    results.add(MarkdownSection.TextSection(currentBuffer.toString().trimEnd()))
                    currentBuffer.clear()
                }
                currentLanguage = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else {
            currentBuffer.appendLine(line)
        }
    }

    if (currentBuffer.isNotEmpty()) {
        if (inCodeBlock) {
            results.add(MarkdownSection.CodeBlockSection(currentLanguage, currentBuffer.toString().trimEnd()))
        } else {
            results.add(MarkdownSection.TextSection(currentBuffer.toString().trimEnd()))
        }
    }

    return results
}

private sealed interface MdBlock {
    data class Blockquote(val content: String) : MdBlock
    data class Table(val lines: List<String>) : MdBlock
    data class HtmlTable(val html: String) : MdBlock
    data class DefinitionList(val items: List<Pair<String, String>>) : MdBlock
    data class Footnotes(val items: List<Pair<String, String>>) : MdBlock
    data class Code(val language: String, val content: String) : MdBlock
    data class AlignedColumns(val rows: List<List<String>>) : MdBlock
    data class Math(val latex: String) : MdBlock
    data class Details(val summary: String, val content: String, val defaultOpen: Boolean = false) : MdBlock
    data class ListItem(
        val level: Int,
        val isOrdered: Boolean,
        val prefix: String,
        val content: String,
        val isTask: Boolean = false,
        val isChecked: Boolean = false,
    ) : MdBlock
    data class ListContinuation(
        val level: Int,
        val content: String,
    ) : MdBlock
    data class Line(val raw: String) : MdBlock
}

private val ORDERED_LIST_REGEX = Regex("""^(\d+(?:\.\d+)*[\.\)])\s+(.*)""", RegexOption.DOT_MATCHES_ALL)
private val TASK_LIST_REGEX = Regex("""^\[([ xX])\]\s*(.*)""")
private val FOOTNOTE_DEF_REGEX = Regex("""^\[\^([^\]]+)\]:\s*(.*)""")

private fun parseDefinitionList(html: String): List<Pair<String, String>> {
    val items = mutableListOf<Pair<String, String>>()
    val cleanHtml = html
        .replace(Regex("""^<dl[^>]*>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""</dl>\s*$""", RegexOption.IGNORE_CASE), "")

    val tagRegex = Regex("""<(dt|dd)[^>]*>([\s\S]*?)(?:</\1>|(?=<(?:dt|dd)[^>]*>|$))""", RegexOption.IGNORE_CASE)
    var currentDt = ""
    for (m in tagRegex.findAll(cleanHtml)) {
        val tagName = m.groupValues[1].lowercase()
        val text = m.groupValues[2]
            .replace(Regex("""</?(?:dt|dd|dl)[^>]*>""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (tagName == "dt") {
            currentDt = text
        } else if (tagName == "dd") {
            if (currentDt.isNotEmpty() || text.isNotEmpty()) {
                items.add(currentDt to text)
                currentDt = ""
            }
        }
    }
    if (currentDt.isNotEmpty()) {
        items.add(currentDt to "")
    }
    return items
}

private fun getLeadingIndent(line: String): Int {
    var spaces = 0
    for (ch in line) {
        when (ch) {
            ' ' -> spaces += 1
            '\t' -> spaces += 4
            else -> return spaces
        }
    }
    return spaces
}

private fun isUnorderedListPrefix(trimmed: String): Boolean {
    return trimmed.startsWith("- ") ||
           trimmed.startsWith("* ") ||
           trimmed.startsWith("+ ") ||
           trimmed.startsWith("• ")
}

private fun updateIndentStack(stack: MutableList<Int>, spaces: Int) {
    if (stack.isEmpty()) {
        stack.add(spaces)
    } else if (spaces > stack.last()) {
        stack.add(spaces)
    } else if (spaces < stack.last()) {
        while (stack.isNotEmpty() && stack.last() > spaces) {
            stack.removeAt(stack.size - 1)
        }
        if (stack.isEmpty() || stack.last() < spaces) {
            stack.add(spaces)
        }
    }
}

private fun extractSummaryAndContent(lines: List<String>): Pair<String, String> {
    val fullText = lines.joinToString("\n")
    val summaryRegex = Regex("""<summary[^>]*>(.*?)</summary>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val match = summaryRegex.find(fullText)
    return if (match != null) {
        val summary = match.groupValues[1].trim()
        val restContent = fullText.replace(match.value, "").trim()
        Pair(summary, restContent)
    } else {
        Pair("点击展开详情", fullText.trim())
    }
}

private fun isStandaloneMathLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("$") || trimmed.startsWith("$$") || !trimmed.endsWith("$") || trimmed.endsWith("$$") || trimmed.length < 3) {
        return false
    }
    val inner = trimmed.substring(1, trimmed.length - 1).trim()
    if (inner.isEmpty() || inner.contains('$')) return false
    // Exclude plain currency numbers like "$100$", "$50.00$"
    if (inner.matches(Regex("""^\d+([.,]\d+)?$"""))) return false
    // Must contain math indicators (LaTeX commands, math symbols/operators, or variables with equals/powers)
    return inner.contains('\\') ||
           inner.contains('^') ||
           inner.contains('_') ||
           inner.contains('=') ||
           inner.contains('+') ||
           inner.contains('-') ||
           inner.contains('*') ||
           inner.contains('/') ||
           inner.contains('<') ||
           inner.contains('>') ||
           inner.contains('≤') ||
           inner.contains('≥') ||
           inner.contains('≠') ||
           inner.contains('±') ||
           inner.contains('×') ||
           inner.contains('÷')
}

/** Groups raw lines into plain lines, code blocks, math equations, details accordions, tables, and lists. */
private fun splitMarkdownBlocks(lines: List<String>): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val indentStack = mutableListOf<Int>()
    var i = 0
    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~") || trimmed.startsWith("、、、")) {
            indentStack.clear()
            val fence = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> "、、、"
            }
            val lang = trimmed.removePrefix(fence).trim()
            val codeLines = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size && !lines[j].trim().startsWith(fence)) {
                codeLines.add(lines[j])
                j += 1
            }
            if (j < lines.size) j += 1 // consume closing fence
            if (lang.equals("latex", ignoreCase = true) ||
                lang.equals("math", ignoreCase = true) ||
                lang.equals("tex", ignoreCase = true) ||
                lang.equals("katex", ignoreCase = true)
            ) {
                blocks.add(MdBlock.Math(codeLines.joinToString("\n").trim()))
            } else {
                blocks.add(MdBlock.Code(language = lang, content = codeLines.joinToString("\n")))
            }
            i = j
        } else if (isStandaloneMathLine(trimmed)) {
            indentStack.clear()
            val mathContent = trimmed.removePrefix("$").removeSuffix("$").trim()
            blocks.add(MdBlock.Math(mathContent))
            i += 1
        } else if (trimmed.startsWith("$$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\begin{")) {
            indentStack.clear()
            val isDollar = trimmed.startsWith("$$")
            val isBracket = trimmed.startsWith("\\[")
            val isBegin = trimmed.startsWith("\\begin{")
            val closingTag = when {
                isDollar -> "$$"
                isBracket -> "\\]"
                else -> {
                    val envMatch = Regex("""^\\begin\{([^}]+)\}""").find(trimmed)
                    val env = envMatch?.groupValues?.getOrNull(1) ?: ""
                    if (env.isNotEmpty()) "\\end{$env}" else "\\end"
                }
            }
            val prefixTag = when {
                isDollar -> "$$"
                isBracket -> "\\["
                else -> ""
            }

            if (closingTag.isNotEmpty() &&
                trimmed.length > closingTag.length + prefixTag.length &&
                trimmed.removePrefix(prefixTag).trim().endsWith(closingTag)
            ) {
                val mathContent = if (isBegin) {
                    trimmed
                } else {
                    trimmed.removePrefix(prefixTag).trim().removeSuffix(closingTag).trim()
                }
                blocks.add(MdBlock.Math(mathContent))
                i += 1
            } else {
                val mathLines = mutableListOf<String>()
                if (isBegin) {
                    mathLines.add(rawLine.trim())
                } else {
                    val firstLine = trimmed.removePrefix(prefixTag).trim()
                    if (firstLine.isNotEmpty() && firstLine != closingTag) {
                        mathLines.add(firstLine)
                    }
                }
                var j = i + 1
                while (j < lines.size && !lines[j].trim().endsWith(closingTag) && !lines[j].trim().contains(closingTag)) {
                    mathLines.add(lines[j])
                    j += 1
                }
                if (j < lines.size) {
                    val lastLine = lines[j].trim()
                    if (isBegin) {
                        mathLines.add(lastLine)
                    } else {
                        val beforeClosing = lastLine.removeSuffix(closingTag).trim()
                        if (beforeClosing.isNotEmpty()) {
                            mathLines.add(beforeClosing)
                        }
                    }
                    j += 1
                }
                blocks.add(MdBlock.Math(mathLines.joinToString("\n").trim()))
                i = j
            }
        } else if (trimmed.startsWith("<details", ignoreCase = true)) {
            indentStack.clear()
            val defaultOpen = trimmed.contains("open", ignoreCase = true)
            if (trimmed.contains("</details>", ignoreCase = true)) {
                val inner = trimmed
                    .replaceFirst(Regex("""^<details[^>]*>""", RegexOption.IGNORE_CASE), "")
                    .replaceFirst(Regex("""</details>.*$""", RegexOption.IGNORE_CASE), "")
                val (summary, content) = extractSummaryAndContent(listOf(inner))
                blocks.add(MdBlock.Details(summary = summary, content = content, defaultOpen = defaultOpen))
                i += 1
            } else {
                val detailsLines = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && !lines[j].trim().contains("</details>", ignoreCase = true)) {
                    detailsLines.add(lines[j])
                    j += 1
                }
                if (j < lines.size) {
                    val lastLine = lines[j].trim()
                    val beforeClosing = lastLine.replace(Regex("""</details>.*$""", RegexOption.IGNORE_CASE), "").trim()
                    if (beforeClosing.isNotEmpty()) {
                        detailsLines.add(beforeClosing)
                    }
                    j += 1
                }
                val (summary, content) = extractSummaryAndContent(detailsLines)
                blocks.add(MdBlock.Details(summary = summary, content = content, defaultOpen = defaultOpen))
                i = j
            }
        } else if (trimmed.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            indentStack.clear()
            val table = mutableListOf(trimmed)
            var j = i + 2
            while (j < lines.size && lines[j].trim().startsWith("|")) {
                table.add(lines[j].trim())
                j += 1
            }
            blocks.add(MdBlock.Table(table))
            i = j
        } else if (trimmed.startsWith("<table", ignoreCase = true)) {
            indentStack.clear()
            val tableLines = mutableListOf<String>()
            var j = i
            while (j < lines.size) {
                tableLines.add(lines[j])
                if (lines[j].contains("</table>", ignoreCase = true)) {
                    j += 1
                    break
                }
                j += 1
            }
            blocks.add(MdBlock.HtmlTable(tableLines.joinToString("\n")))
            i = j
        } else if (trimmed.startsWith("<dl", ignoreCase = true)) {
            indentStack.clear()
            val dlLines = mutableListOf<String>()
            var j = i
            while (j < lines.size) {
                dlLines.add(lines[j])
                if (lines[j].contains("</dl>", ignoreCase = true)) {
                    j += 1
                    break
                }
                j += 1
            }
            val fullDl = dlLines.joinToString("\n")
            val items = parseDefinitionList(fullDl)
            if (items.isNotEmpty()) {
                blocks.add(MdBlock.DefinitionList(items))
            }
            i = j
        } else if (i + 1 < lines.size && lines[i + 1].trim().startsWith(": ") && trimmed.isNotEmpty() &&
                   !trimmed.startsWith("#") && !trimmed.startsWith(">") && !isUnorderedListPrefix(trimmed) &&
                   !trimmed.startsWith("```") && !trimmed.startsWith("$$")
        ) {
            indentStack.clear()
            val items = mutableListOf<Pair<String, String>>()
            var j = i
            while (j < lines.size) {
                val currentTerm = lines[j].trim()
                if (j + 1 < lines.size && lines[j + 1].trim().startsWith(": ") && currentTerm.isNotEmpty()) {
                    val desc = lines[j + 1].trim().removePrefix(": ").trim()
                    items.add(currentTerm to desc)
                    j += 2
                    while (j < lines.size && lines[j].trim().isEmpty()) {
                        j += 1
                    }
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                blocks.add(MdBlock.DefinitionList(items))
                i = j
            } else {
                blocks.add(MdBlock.Line(rawLine))
                i += 1
            }
        } else if (FOOTNOTE_DEF_REGEX.find(trimmed) != null) {
            indentStack.clear()
            val footnotes = mutableListOf<Pair<String, String>>()
            var j = i
            while (j < lines.size) {
                val lineTrimmed = lines[j].trim()
                val fnMatch = FOOTNOTE_DEF_REGEX.find(lineTrimmed)
                if (fnMatch != null) {
                    val label = fnMatch.groupValues[1].trim()
                    val firstLine = fnMatch.groupValues[2].trim()
                    val contentLines = mutableListOf(firstLine)
                    j += 1
                    while (j < lines.size) {
                        val nextTrim = lines[j].trim()
                        if (lines[j].startsWith("    ") || lines[j].startsWith("\t")) {
                            contentLines.add(nextTrim)
                            j += 1
                        } else {
                            break
                        }
                    }
                    footnotes.add(label to contentLines.joinToString("\n"))
                    while (j < lines.size && lines[j].trim().isEmpty()) {
                        j += 1
                    }
                } else {
                    break
                }
            }
            if (footnotes.isNotEmpty()) {
                blocks.add(MdBlock.Footnotes(footnotes))
                i = j
            } else {
                blocks.add(MdBlock.Line(rawLine))
                i += 1
            }
        } else if (isColumnarStart(lines, i)) {
            indentStack.clear()
            val rows = mutableListOf<List<String>>()
            var j = i
            val expectedCols = splitColumnarLine(lines[i])?.size ?: 0
            while (j < lines.size) {
                val cols = splitColumnarLine(lines[j]) ?: break
                if (cols.size != expectedCols) break
                rows.add(cols)
                j += 1
            }
            if (rows.size >= 2) {
                blocks.add(MdBlock.AlignedColumns(rows))
                i = j
            } else {
                blocks.add(MdBlock.Line(lines[i]))
                i += 1
            }
        } else if (trimmed.startsWith(">")) {
            indentStack.clear()
            val quoteLines = mutableListOf<String>()
            var j = i
            while (j < lines.size) {
                val lineRaw = lines[j]
                val lineTrimmed = lineRaw.trim()
                if (lineTrimmed.startsWith(">")) {
                    val unquoted = when {
                        lineTrimmed == ">" -> ""
                        lineTrimmed.startsWith("> ") -> {
                            val idx = lineRaw.indexOf("> ")
                            lineRaw.substring(idx + 2)
                        }
                        else -> {
                            val idx = lineRaw.indexOf(">")
                            lineRaw.substring(idx + 1)
                        }
                    }
                    quoteLines.add(unquoted)
                    j += 1
                } else {
                    break
                }
            }
            blocks.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            i = j
        } else if (trimmed.isEmpty()) {
            blocks.add(MdBlock.Line(rawLine))
            i += 1
        } else if (isUnorderedListPrefix(trimmed)) {
            val leadingSpaces = getLeadingIndent(rawLine)
            updateIndentStack(indentStack, leadingSpaces)
            val level = (indentStack.size - 1).coerceAtLeast(0)

            val rest = trimmed.substring(2).trim()
            val taskMatch = TASK_LIST_REGEX.find(rest)
            val isTask = taskMatch != null
            val isChecked = isTask && taskMatch!!.groupValues[1].lowercase() == "x"
            val content = if (isTask) taskMatch!!.groupValues[2].trim() else rest

            blocks.add(
                MdBlock.ListItem(
                    level = level,
                    isOrdered = false,
                    prefix = "",
                    content = content,
                    isTask = isTask,
                    isChecked = isChecked,
                )
            )
            i += 1
        } else if (ORDERED_LIST_REGEX.find(trimmed) != null) {
            val match = ORDERED_LIST_REGEX.find(trimmed)!!
            val leadingSpaces = getLeadingIndent(rawLine)
            updateIndentStack(indentStack, leadingSpaces)
            val level = (indentStack.size - 1).coerceAtLeast(0)

            val prefix = match.groupValues[1]
            val rawContent = match.groupValues[2].trim()
            val taskMatch = TASK_LIST_REGEX.find(rawContent)
            val isTask = taskMatch != null
            val isChecked = isTask && taskMatch!!.groupValues[1].lowercase() == "x"
            val content = if (isTask) taskMatch!!.groupValues[2].trim() else rawContent

            blocks.add(
                MdBlock.ListItem(
                    level = level,
                    isOrdered = true,
                    prefix = prefix,
                    content = content,
                    isTask = isTask,
                    isChecked = isChecked,
                )
            )
            i += 1
        } else if (indentStack.isNotEmpty() &&
                   getLeadingIndent(rawLine) >= indentStack.last() &&
                   getLeadingIndent(rawLine) > 0 &&
                   !trimmed.startsWith("#") &&
                   !trimmed.startsWith(">") &&
                   !trimmed.startsWith("```") &&
                   !trimmed.startsWith("$$") &&
                   !trimmed.startsWith("<details", ignoreCase = true) &&
                   trimmed != "---" &&
                   trimmed != "***" &&
                   trimmed != "___"
        ) {
            val level = (indentStack.size - 1).coerceAtLeast(0)
            blocks.add(
                MdBlock.ListContinuation(
                    level = level,
                    content = trimmed,
                )
            )
            i += 1
        } else {
            indentStack.clear()
            blocks.add(MdBlock.Line(rawLine))
            i += 1
        }
    }
    return blocks
}

/** Checks if a line contains space-separated key-value/columnar data. */
private fun splitColumnarLine(line: String): List<String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("#") || trimmed.startsWith(">") ||
        isUnorderedListPrefix(trimmed) ||
        trimmed.startsWith("|") ||
        trimmed.startsWith("```") || trimmed.startsWith("~~~") ||
        trimmed.startsWith("、、、") ||
        trimmed.startsWith("$$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\begin{") ||
        trimmed.startsWith("<details", ignoreCase = true) ||
        ORDERED_LIST_REGEX.matches(trimmed)) {
        return null
    }
    if (!trimmed.contains("  ") && !trimmed.contains("\t")) return null
    val parts = trimmed.split(Regex("""\s{2,}|\t+""")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    if (visualWidth(parts[0]) > 22) return null
    return parts
}

private fun isColumnarStart(lines: List<String>, index: Int): Boolean {
    val first = splitColumnarLine(lines[index]) ?: return false
    if (index + 1 >= lines.size) return false
    val second = splitColumnarLine(lines[index + 1]) ?: return false
    return first.size == second.size
}

/** A GFM column separator: pipes wrapping dash-only cells (`---`, `:---:`). */
private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.startsWith("|")) return false
    val core = t.removePrefix("|").removeSuffix("|")
    if (!core.contains('-')) return false
    return core.split('|').all { cell ->
        val c = cell.trim().replace(":", "")
        c.isNotEmpty() && c.all { it == '-' }
    }
}

private fun tableCells(line: String): List<String> {
    val t = line.trim()
    val body = if (t.startsWith("|")) t.removePrefix("|") else t
    val cleaned = if (body.endsWith("|")) body.removeSuffix("|") else body
    return cleaned.split('|').map { it.trim() }
}

private fun parseMarkdownTable(lines: List<String>): Pair<List<String>, List<List<String>>>? {
    if (lines.size < 2) return null
    val rawHeader = tableCells(lines[0])
    if (rawHeader.isEmpty() || rawHeader.all { it.isEmpty() }) return null

    val rawRows = lines.drop(1)
        .filterNot { isTableSeparator(it) }
        .map { tableCells(it) }

    // Column count is defined by the table schema (header or max columns across valid rows)
    val colCount = maxOf(rawHeader.size, rawRows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return null

    val header = rawHeader + List((colCount - rawHeader.size).coerceAtLeast(0)) { "" }
    val rows = rawRows.map { cells ->
        if (cells.size >= colCount) {
            cells.take(colCount)
        } else {
            cells + List(colCount - cells.size) { "" }
        }
    }
    return header to rows
}

fun containsMathFormula(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("```") || trimmed.startsWith("~~~") || trimmed.startsWith("、、、")) return false
    if (trimmed.contains("$$") || trimmed.contains("\\[") || trimmed.contains("\\(") || trimmed.contains("\\begin{")) {
        return true
    }
    val singleDollarMatches = Regex("""(?<!\\)\$([^\s$](?:[^$\n]*?[^\s$])?)\$(?!\d)""").findAll(trimmed)
    for (m in singleDollarMatches) {
        val inner = m.groupValues[1].trim()
        if (inner.matches(Regex("""^\d+([.,]\d+)?$"""))) continue
        return true
    }
    return false
}

fun normalizeMarkdownUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("mailto:", ignoreCase = true) ||
        trimmed.startsWith("file:", ignoreCase = true) ||
        trimmed.startsWith("ftp:", ignoreCase = true) -> trimmed
        trimmed.startsWith("localhost:", ignoreCase = true) ||
        trimmed.startsWith("127.0.0.1:", ignoreCase = true) ||
        trimmed.startsWith("localhost/", ignoreCase = true) ||
        trimmed.startsWith("127.0.0.1/", ignoreCase = true) ||
        trimmed.equals("localhost", ignoreCase = true) ||
        trimmed == "127.0.0.1" -> "http://$trimmed"
        trimmed.contains("://") -> trimmed
        trimmed.startsWith("#") || trimmed.startsWith("/") -> trimmed
        else -> "https://$trimmed"
    }
}

fun buildMarkdownAnnotatedString(
    markdown: String,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
    isDark: Boolean = (primaryColor.red * 0.299f + primaryColor.green * 0.587f + primaryColor.blue * 0.114f) > 0.5f,
    boldColor: Color = primaryColor,
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        fun getLinkStyles(weight: FontWeight? = null): TextLinkStyles {
            val w = weight ?: FontWeight.Medium
            return TextLinkStyles(
                style = SpanStyle(
                    color = accentColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = w,
                ),
                focusedStyle = SpanStyle(
                    color = accentColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = w,
                ),
                hoveredStyle = SpanStyle(
                    color = accentColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = w,
                ),
                pressedStyle = SpanStyle(
                    color = accentColor.copy(alpha = 0.65f),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = w,
                ),
            )
        }

        fun appendInlineFormatted(
            text: String,
            baseFontWeight: FontWeight? = null,
            baseFontStyle: FontStyle? = null,
            baseColor: Color,
            isInsideLink: Boolean = false,
        ) {
            val inlineTokenRegex = Regex(
                """(`[^`]+`|""" +
                """(?<!!)(?<!\\)\[([^\]\n]+)\]\(([^)\s]+(?:\s+"[^"]*")?)\)|""" +
                """\*\*\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*\*\*|""" +
                """___([^\s_][\s\S]*?[^\s_]|[^\s_])___|""" +
                """\*\*_([^\s_][\s\S]*?[^\s_]|[^\s_])_\*\*|""" +
                """_\*\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*\*_|""" +
                """\*\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*\*|""" +
                """(?<!\w)__([^\s_][\s\S]*?[^\s_]|[^\s_])__(?!\w)|""" +
                """\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*|""" +
                """(?<!\w)_([^\s_][\s\S]*?[^\s_]|[^\s_])_(?!\w))"""
            )
            var cur = 0
            for (m in inlineTokenRegex.findAll(text)) {
                if (m.range.first > cur) {
                    withStyle(SpanStyle(fontWeight = baseFontWeight, fontStyle = baseFontStyle, color = baseColor)) {
                        append(text.substring(cur, m.range.first))
                    }
                }
                val v = m.value
                when {
                    // Inline code
                    v.startsWith("`") && v.endsWith("`") && v.length >= 2 -> {
                        var codeContent = v.substring(1, v.length - 1)
                        var innerWeight = baseFontWeight ?: FontWeight.SemiBold
                        var innerStyle = baseFontStyle
                        if ((codeContent.startsWith("***") && codeContent.endsWith("***") && codeContent.length >= 6) ||
                            (codeContent.startsWith("**_") && codeContent.endsWith("_**") && codeContent.length >= 6) ||
                            (codeContent.startsWith("_**") && codeContent.endsWith("**_") && codeContent.length >= 6)) {
                            codeContent = codeContent.substring(3, codeContent.length - 3)
                            innerWeight = FontWeight.Bold
                            innerStyle = FontStyle.Italic
                        } else if ((codeContent.startsWith("**") && codeContent.endsWith("**") && codeContent.length >= 4) ||
                                   (codeContent.startsWith("__") && codeContent.endsWith("__") && codeContent.length >= 4)) {
                            codeContent = codeContent.substring(2, codeContent.length - 2)
                            innerWeight = FontWeight.Bold
                        } else if ((codeContent.startsWith("*") && codeContent.endsWith("*") && codeContent.length >= 2) ||
                                   (codeContent.startsWith("_") && codeContent.endsWith("_") && codeContent.length >= 2)) {
                            codeContent = codeContent.substring(1, codeContent.length - 1)
                            innerStyle = FontStyle.Italic
                        }
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = innerWeight,
                                fontStyle = innerStyle,
                                color = accentColor,
                            )
                        ) {
                            append(codeContent)
                        }
                    }
                    // Markdown link: [label](url)
                    v.startsWith("[") && v.contains("](") && v.endsWith(")") -> {
                        val closeIdx = v.indexOf("](")
                        val label = v.substring(1, closeIdx)
                        val rawUrl = v.substring(closeIdx + 2, v.length - 1).trim()
                        val finalUrl = normalizeMarkdownUrl(rawUrl.split(Regex("""\s+""")).firstOrNull() ?: rawUrl)
                        val isBold = baseFontWeight == FontWeight.Bold ||
                                (label.startsWith("**") && label.endsWith("**")) ||
                                (label.startsWith("__") && label.endsWith("__"))
                        val isItalic = baseFontStyle == FontStyle.Italic ||
                                (label.startsWith("***") && label.endsWith("***")) ||
                                (label.startsWith("*") && label.endsWith("*"))
                        val targetWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
                        val targetStyle = if (isItalic) FontStyle.Italic else null

                        if (!isInsideLink) {
                            withLink(LinkAnnotation.Url(url = finalUrl, styles = getLinkStyles(targetWeight))) {
                                appendInlineFormatted(
                                    text = label,
                                    baseFontWeight = targetWeight,
                                    baseFontStyle = targetStyle,
                                    baseColor = accentColor,
                                    isInsideLink = true,
                                )
                            }
                        } else {
                            appendInlineFormatted(
                                text = label,
                                baseFontWeight = targetWeight,
                                baseFontStyle = targetStyle,
                                baseColor = baseColor,
                                isInsideLink = true,
                            )
                        }
                    }
                    // Bold-italic
                    (v.startsWith("***") && v.endsWith("***") && v.length >= 6) ||
                    (v.startsWith("___") && v.endsWith("___") && v.length >= 6) ||
                    (v.startsWith("**_") && v.endsWith("_**") && v.length >= 6) ||
                    (v.startsWith("_**") && v.endsWith("**_") && v.length >= 6) -> {
                        val inner = v.substring(3, v.length - 3)
                        appendInlineFormatted(
                            text = inner,
                            baseFontWeight = FontWeight.Bold,
                            baseFontStyle = FontStyle.Italic,
                            baseColor = if (isInsideLink) accentColor else baseColor,
                            isInsideLink = isInsideLink,
                        )
                    }
                    // Bold
                    (v.startsWith("**") && v.endsWith("**") && v.length >= 4) ||
                    (v.startsWith("__") && v.endsWith("__") && v.length >= 4) -> {
                        val inner = v.substring(2, v.length - 2)
                        appendInlineFormatted(
                            text = inner,
                            baseFontWeight = FontWeight.Bold,
                            baseFontStyle = baseFontStyle,
                            baseColor = if (isInsideLink) accentColor else baseColor,
                            isInsideLink = isInsideLink,
                        )
                    }
                    // Italic
                    (v.startsWith("*") && v.endsWith("*") && v.length >= 2) ||
                    (v.startsWith("_") && v.endsWith("_") && v.length >= 2) -> {
                        val inner = v.substring(1, v.length - 1)
                        appendInlineFormatted(
                            text = inner,
                            baseFontWeight = baseFontWeight,
                            baseFontStyle = FontStyle.Italic,
                            baseColor = if (isInsideLink) accentColor else baseColor,
                            isInsideLink = isInsideLink,
                        )
                    }
                    else -> {
                        withStyle(SpanStyle(fontWeight = baseFontWeight, fontStyle = baseFontStyle, color = baseColor)) {
                            append(v)
                        }
                    }
                }
                cur = m.range.last + 1
            }
            if (cur < text.length) {
                withStyle(SpanStyle(fontWeight = baseFontWeight, fontStyle = baseFontStyle, color = baseColor)) {
                    append(text.substring(cur))
                }
            }
        }

        val regex = Regex(
            """(""" +
            // Markdown link: [text](url) - negative lookbehind for ! to avoid image tags
            """(?<!!)(?<!\\)\[([^\]\n]+)\]\(([^)\s]+(?:\s+"[^"]*")?)\)|""" +
            // Angle-bracket autolink: <http...> or <mailto:...>
            """<((?:https?|ftp|file)://[^\s>]+|mailto:[^\s>]+)>|""" +
            // Bold-Italic (3 delimiters): ***text***, ___text___, **_text_**, __*text*__, *__text__*, _**text**_
            """\*\*\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*\*\*|""" +
            """___([^\s_][\s\S]*?[^\s_]|[^\s_])___|""" +
            """\*\*_([^\s_][\s\S]*?[^\s_]|[^\s_])_\*\*|""" +
            """__\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*__|""" +
            """\*__([^\s_][\s\S]*?[^\s_]|[^\s_])__\*|""" +
            """_\*\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*\*_|""" +
            // Bold (2 delimiters): **text**, __text__
            """\*\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*\*|""" +
            """(?<!\w)__([^\s_][\s\S]*?[^\s_]|[^\s_])__(?!\w)|""" +
            // Footnote reference: [^1], [^note] (not followed by colon)
            """\[\^([^\]]+)\](?!\s*:)|""" +
            // Highlight: ==text== or <mark>text</mark>
            """==([^=\n]+)==|""" +
            """<mark[^>]*>(.*?)</mark>|""" +
            // Keyboard key: <kbd>text</kbd>
            """<kbd[^>]*>(.*?)</kbd>|""" +
            // Inline code: `text`
            """`([^`]+)`|""" +
            // Strikethrough: ~~text~~, <del>text</del>, <s>text</s>
            """~~([^~]+)~~|""" +
            """<del[^>]*>(.*?)</del>|""" +
            """<s[^>]*>(.*?)</s>|""" +
            // Subscript: ~text~ or <sub>text</sub>
            """(?<!~)~([^~\s](?:[^~]*?[^~\s])?)~(?!~)|""" +
            """<sub[^>]*>(.*?)</sub>|""" +
            // Superscript: ^text^ or <sup>text</sup>
            """\^([^\^\s](?:[^\^]*?[^\^\s])?)\^|""" +
            """<sup[^>]*>(.*?)</sup>|""" +
            // Italic (1 delimiter): *text*, _text_, <em>text</em>, <i>text</i>
            """\*([^\s*][\s\S]*?[^\s*]|[^\s*])\*|""" +
            """(?<!\w)_([^\s_][\s\S]*?[^\s_]|[^\s_])_(?!\w)|""" +
            """<em[^>]*>(.*?)</em>|""" +
            """<i[^>]*>(.*?)</i>|""" +
            // LaTeX inline math: $$...$$, $...$, \(...\), \[...\]
            """(?<!\\)\$\$([^\n]+?)\$\$|""" +
            """(?<!\\)\$([^\s$](?:[^$\n]*?[^\s$])?)\$(?!\d)|""" +
            """\\\((.+?)\\\)|""" +
            """\\\[(.+?)\\\]|""" +
            // Plain bare URL: http://..., https://..., localhost:port, 127.0.0.1:port (excluding trailing punctuation)
            """(?<![\w/])(?:(?:https?|ftp|file)://[^\s<>"'{}|\\^`\[\]]*|(?:localhost|127\.0\.0\.1):\d{2,5}(?:/[^\s<>"'{}|\\^`\[\]]*)?)[^\s<>"'{}|\\^`\[\].,;:!?)\]'"。，！？）】»]""" +
            """)""",
            RegexOption.IGNORE_CASE
        )
        val matches = regex.findAll(markdown)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(markdown.substring(cursor, match.range.first))
            }
            val matchText = match.value
            when {
                // 0. Markdown link: [label](url)
                matchText.startsWith("[") && matchText.contains("](") && matchText.endsWith(")") -> {
                    val closeBracketIdx = matchText.indexOf("](")
                    val label = matchText.substring(1, closeBracketIdx)
                    val rawTarget = matchText.substring(closeBracketIdx + 2, matchText.length - 1).trim()
                    val cleanUrl = rawTarget.split(Regex("""\s+""")).firstOrNull() ?: rawTarget
                    val finalUrl = normalizeMarkdownUrl(cleanUrl)
                    val isBold = (label.startsWith("**") && label.endsWith("**")) ||
                                 (label.startsWith("__") && label.endsWith("__"))
                    val isItalic = (label.startsWith("***") && label.endsWith("***")) ||
                                   (label.startsWith("*") && label.endsWith("*"))
                    val targetWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
                    val targetStyle = if (isItalic) FontStyle.Italic else null
                    withLink(LinkAnnotation.Url(url = finalUrl, styles = getLinkStyles(targetWeight))) {
                        appendInlineFormatted(
                            text = label,
                            baseFontWeight = targetWeight,
                            baseFontStyle = targetStyle,
                            baseColor = accentColor,
                            isInsideLink = true,
                        )
                    }
                }

                // 0.1 Autolink: <http...> or <mailto:...>
                matchText.startsWith("<") && matchText.endsWith(">") &&
                    (matchText.startsWith("<http", ignoreCase = true) ||
                     matchText.startsWith("<file", ignoreCase = true) ||
                     matchText.startsWith("<ftp", ignoreCase = true) ||
                     matchText.startsWith("<mailto", ignoreCase = true)) -> {
                    val target = matchText.substring(1, matchText.length - 1).trim()
                    val finalUrl = normalizeMarkdownUrl(target)
                    withLink(LinkAnnotation.Url(url = finalUrl, styles = getLinkStyles())) {
                        append(target)
                    }
                }

                // 1. Bold-italic: ***...***, ___...___, **_..._**, __*...*__, *__...__*, _**...**_
                (matchText.startsWith("***") && matchText.endsWith("***") && matchText.length >= 6) ||
                (matchText.startsWith("___") && matchText.endsWith("___") && matchText.length >= 6) ||
                (matchText.startsWith("**_") && matchText.endsWith("_**") && matchText.length >= 6) ||
                (matchText.startsWith("__*") && matchText.endsWith("*__") && matchText.length >= 6) ||
                (matchText.startsWith("*__") && matchText.endsWith("__*") && matchText.length >= 6) ||
                (matchText.startsWith("_**") && matchText.endsWith("**_") && matchText.length >= 6) -> {
                    val inner = matchText.substring(3, matchText.length - 3)
                    appendInlineFormatted(
                        text = inner,
                        baseFontWeight = FontWeight.Bold,
                        baseFontStyle = FontStyle.Italic,
                        baseColor = boldColor,
                    )
                }

                // 2. Bold: **...**, __...__
                (matchText.startsWith("**") && matchText.endsWith("**") && matchText.length >= 4) ||
                (matchText.startsWith("__") && matchText.endsWith("__") && matchText.length >= 4) -> {
                    val inner = matchText.substring(2, matchText.length - 2)
                    appendInlineFormatted(
                        text = inner,
                        baseFontWeight = FontWeight.Bold,
                        baseColor = boldColor,
                    )
                }

                // 3. Footnote reference: [^1]
                matchText.startsWith("[^") && matchText.endsWith("]") && matchText.length >= 4 -> {
                    val inner = matchText.substring(2, matchText.length - 1)
                    withStyle(
                        SpanStyle(
                            fontSize = 10.sp,
                            baselineShift = BaselineShift.Superscript,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    ) {
                        append("[$inner]")
                    }
                }

                // 4. Highlight: ==text== or <mark>text</mark>
                (matchText.startsWith("==") && matchText.endsWith("==") && matchText.length >= 5) ||
                matchText.startsWith("<mark", ignoreCase = true) -> {
                    val inner = if (matchText.startsWith("==")) {
                        matchText.substring(2, matchText.length - 2)
                    } else {
                        matchText.replace(Regex("""^<mark[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</mark>$""", RegexOption.IGNORE_CASE), "")
                    }
                    val hlBg = if (isDark) Color(0x50FACC15) else Color(0xFFFEF08A)
                    val hlText = if (isDark) Color(0xFFFEF08A) else Color(0xFF854D0E)
                    withStyle(
                        SpanStyle(
                            background = hlBg,
                            color = hlText,
                            fontWeight = FontWeight.Medium,
                        )
                    ) {
                        append(inner)
                    }
                }

                // 5. Keyboard key: <kbd>text</kbd>
                matchText.startsWith("<kbd", ignoreCase = true) -> {
                    val inner = matchText.replace(Regex("""^<kbd[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</kbd>$""", RegexOption.IGNORE_CASE), "")
                    val kbdBg = if (isDark) Color(0xFF263248) else Color(0xFFE2E8F0)
                    val kbdText = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            background = kbdBg,
                            color = kbdText,
                        )
                    ) {
                        append(" $inner ")
                    }
                }

                // 6. Inline code: `...`
                matchText.startsWith("`") && matchText.endsWith("`") && matchText.length >= 2 -> {
                    var inner = matchText.substring(1, matchText.length - 1)
                    var codeWeight = FontWeight.SemiBold
                    var codeStyle: FontStyle? = null
                    if ((inner.startsWith("***") && inner.endsWith("***") && inner.length >= 6) ||
                        (inner.startsWith("**_") && inner.endsWith("_**") && inner.length >= 6) ||
                        (inner.startsWith("_**") && inner.endsWith("**_") && inner.length >= 6)) {
                        inner = inner.substring(3, inner.length - 3)
                        codeWeight = FontWeight.Bold
                        codeStyle = FontStyle.Italic
                    } else if ((inner.startsWith("**") && inner.endsWith("**") && inner.length >= 4) ||
                               (inner.startsWith("__") && inner.endsWith("__") && inner.length >= 4)) {
                        inner = inner.substring(2, inner.length - 2)
                        codeWeight = FontWeight.Bold
                    } else if ((inner.startsWith("*") && inner.endsWith("*") && inner.length >= 2) ||
                               (inner.startsWith("_") && inner.endsWith("_") && inner.length >= 2)) {
                        inner = inner.substring(1, inner.length - 1)
                        codeStyle = FontStyle.Italic
                    }
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = codeWeight,
                            fontStyle = codeStyle,
                            color = accentColor,
                        ),
                    ) {
                        append(inner)
                    }
                }

                // 7. Strikethrough: ~~...~~, <del>...</del>, <s>...</s>
                (matchText.startsWith("~~") && matchText.endsWith("~~") && matchText.length >= 4) ||
                matchText.startsWith("<del", ignoreCase = true) ||
                matchText.startsWith("<s", ignoreCase = true) -> {
                    val inner = when {
                        matchText.startsWith("~~") -> matchText.substring(2, matchText.length - 2)
                        matchText.startsWith("<del", ignoreCase = true) -> matchText.replace(Regex("""^<del[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</del>$""", RegexOption.IGNORE_CASE), "")
                        else -> matchText.replace(Regex("""^<s[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</s>$""", RegexOption.IGNORE_CASE), "")
                    }
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(inner)
                    }
                }

                // 8. Subscript: ~...~ or <sub>...</sub>
                (matchText.startsWith("~") && matchText.endsWith("~") && !matchText.startsWith("~~") && matchText.length >= 3) ||
                matchText.startsWith("<sub", ignoreCase = true) -> {
                    val inner = if (matchText.startsWith("~")) {
                        matchText.substring(1, matchText.length - 1)
                    } else {
                        matchText.replace(Regex("""^<sub[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</sub>$""", RegexOption.IGNORE_CASE), "")
                    }
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = 10.sp,
                        )
                    ) {
                        append(inner)
                    }
                }

                // 9. Superscript: ^...^ or <sup>...</sup>
                (matchText.startsWith("^") && matchText.endsWith("^") && matchText.length >= 3) ||
                matchText.startsWith("<sup", ignoreCase = true) -> {
                    val inner = if (matchText.startsWith("^")) {
                        matchText.substring(1, matchText.length - 1)
                    } else {
                        matchText.replace(Regex("""^<sup[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</sup>$""", RegexOption.IGNORE_CASE), "")
                    }
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 10.sp,
                        )
                    ) {
                        append(inner)
                    }
                }

                // 10. Italic: *...*, _..._, <em>...</em>, <i>...</i>
                (matchText.startsWith("*") && matchText.endsWith("*") && matchText.length >= 2) ||
                (matchText.startsWith("_") && matchText.endsWith("_") && matchText.length >= 2) ||
                matchText.startsWith("<em", ignoreCase = true) ||
                matchText.startsWith("<i", ignoreCase = true) -> {
                    val inner = when {
                        matchText.startsWith("*") || matchText.startsWith("_") -> matchText.substring(1, matchText.length - 1)
                        matchText.startsWith("<em", ignoreCase = true) -> matchText.replace(Regex("""^<em[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</em>$""", RegexOption.IGNORE_CASE), "")
                        else -> matchText.replace(Regex("""^<i[^>]*>""", RegexOption.IGNORE_CASE), "").replace(Regex("""</i>$""", RegexOption.IGNORE_CASE), "")
                    }
                    appendInlineFormatted(
                        text = inner,
                        baseFontStyle = FontStyle.Italic,
                        baseColor = primaryColor,
                    )
                }

                // 11. LaTeX inline math: $$...$$, $...$, \(...\), \[...\]
                (matchText.startsWith("$$") && matchText.endsWith("$$") && matchText.length >= 4) ||
                (matchText.startsWith("$") && matchText.endsWith("$") && matchText.length >= 2) ||
                (matchText.startsWith("\\(") && matchText.endsWith("\\)") && matchText.length >= 4) ||
                (matchText.startsWith("\\[") && matchText.endsWith("\\]") && matchText.length >= 4) -> {
                    val raw = when {
                        matchText.startsWith("$$") -> matchText.substring(2, matchText.length - 2)
                        matchText.startsWith("$") -> matchText.substring(1, matchText.length - 1)
                        matchText.startsWith("\\(") -> matchText.removePrefix("\\(").removeSuffix("\\)")
                        matchText.startsWith("\\[") -> matchText.removePrefix("\\[").removeSuffix("\\]")
                        else -> matchText
                    }
                    val isCurrency = matchText.startsWith("$") && !matchText.startsWith("$$") &&
                        raw.trim().matches(Regex("""^\d+([.,]\d+)?$"""))
                    if (isCurrency) {
                        append(matchText)
                    } else {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                color = accentColor,
                            )
                        ) {
                            append(raw.trim())
                        }
                    }
                }

                // 12. Plain bare URL: http://..., https://..., file://..., ftp://..., localhost:8765, 127.0.0.1:8765
                matchText.startsWith("http://", ignoreCase = true) ||
                matchText.startsWith("https://", ignoreCase = true) ||
                matchText.startsWith("file://", ignoreCase = true) ||
                matchText.startsWith("ftp://", ignoreCase = true) ||
                matchText.startsWith("localhost:", ignoreCase = true) ||
                matchText.startsWith("127.0.0.1:", ignoreCase = true) -> {
                    val finalUrl = normalizeMarkdownUrl(matchText)
                    withLink(LinkAnnotation.Url(url = finalUrl, styles = getLinkStyles())) {
                        append(matchText)
                    }
                }

                else -> {
                    append(matchText)
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < markdown.length) {
            append(markdown.substring(cursor))
        }
    }
}

/**
 * GFM table rendering: shaded bold header row, hairline separators, and
 * column widths proportional to the longest cell so narrow columns (章节)
 * stay narrow while wide ones (内容) take the rest.
 */
private fun visualWidth(text: String): Int {
    if (text.isEmpty()) return 0
    var maxWidth = 0
    for (line in text.lines()) {
        var w = 0
        for (ch in line) {
            w += if (ch.code >= 0x2E80 || ch in '\u3000'..'\u9FFF' || ch in '\uFF00'..'\uFFEF') 2 else 1
        }
        if (w > maxWidth) {
            maxWidth = w
        }
    }
    return maxWidth
}

/**
 * GFM table rendering: shaded bold header row, hairline horizontal and vertical grid lines,
 * and intelligent content-aware column widths with horizontal scroll fallback for dense tables.
 */
@Composable
private fun MarkdownTableView(
    tableLines: List<String>,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
    boldColor: Color = primaryColor,
) {
    val parsed = remember(tableLines) { parseMarkdownTable(tableLines) } ?: return
    val (header, rows) = parsed
    val columnCount = header.size
    if (columnCount == 0) return

    val isDark = GeekColors.isDark
    val tableBg = if (isDark) Color(0xFF121722) else Color(0xFFFFFFFF)
    val tableBorderColor = if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8F0)
    val headerBg = if (isDark) Color(0xFF1B2332) else Color(0xFFF1F4F8)
    val dividerColor = if (isDark) Color(0x1EFFFFFF) else Color(0xFFE2E8F0)
    val altRowBg = if (isDark) Color(0x06FFFFFF) else Color(0xFFF9FAFB)

    // Measure maximum visual character units per column (CJK = 2, ASCII = 1)
    val colMaxUnits = remember(tableLines) {
        List(columnCount) { col ->
            var maxUnits = 4
            val allCells = rows.mapNotNull { it.getOrNull(col) } + header.getOrElse(col) { "" }
            for (cell in allCells) {
                val u = visualWidth(cell)
                if (u > maxUnits) {
                    maxUnits = u
                }
            }
            maxUnits
        }
    }

    // Determine layout strategy
    val isTwoColumn = columnCount == 2
    val col0Units = colMaxUnits.getOrElse(0) { 4 }
    val col1Units = colMaxUnits.getOrElse(1) { 4 }

    // In a 2-column table with a compact index/label column (e.g. 章节, 序号, 状态)
    val isCompactCol0 = isTwoColumn && col0Units <= 10
    val col0ExactWidth = if (isCompactCol0) (col0Units * 8.5f + 28f).coerceIn(68f, 115f).dp else 0.dp

    // For 3+ columns, check if horizontal scroll is needed
    val neededColWidths = remember(tableLines) {
        colMaxUnits.map { units -> (units * 8.5f + 24f).coerceIn(64f, 220f).dp }
    }
    val totalNeededWidth = remember(neededColWidths) {
        neededColWidths.fold(0f) { acc, dp -> acc + dp.value } + (0.8f * (columnCount - 1))
    }
    val needHorizontalScroll = !isTwoColumn && totalNeededWidth > 320f

    val shape = RoundedCornerShape(10.dp)

    Surface(
        shape = shape,
        color = tableBg,
        border = BorderStroke(0.8.dp, tableBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape),
    ) {
        val tableModifier = if (needHorizontalScroll) {
            Modifier.horizontalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }

        Column(modifier = tableModifier) {
            // Header Row
            Row(
                modifier = (if (needHorizontalScroll) Modifier.width(totalNeededWidth.dp) else Modifier.fillMaxWidth())
                    .height(IntrinsicSize.Min)
                    .background(headerBg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                header.take(columnCount).forEachIndexed { col, cell ->
                    if (col > 0) {
                        // Vertical divider line (竖线)
                        Box(
                            modifier = Modifier
                                .width(0.8.dp)
                                .fillMaxHeight()
                                .background(dividerColor),
                        )
                    }

                    val colWidth = neededColWidths.getOrElse(col) { 64.dp }
                    val cellMod = when {
                        isCompactCol0 && col == 0 -> Modifier.width(col0ExactWidth)
                        isCompactCol0 && col == 1 -> Modifier.weight(1f)
                        needHorizontalScroll -> Modifier.width(colWidth)
                        isTwoColumn -> {
                            val ratio = (col0Units.toFloat() / (col0Units + col1Units).toFloat()).coerceIn(0.35f, 0.65f)
                            if (col == 0) Modifier.weight(ratio) else Modifier.weight(1f - ratio)
                        }
                        else -> Modifier.weight(colWidth.value.coerceAtLeast(0.1f))
                    }

                    Box(
                        modifier = cellMod
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = buildMarkdownAnnotatedString(cell, boldColor, accentColor, codeBgColor, boldColor = boldColor),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                            ),
                            color = boldColor,
                        )
                    }
                }
            }

            // Horizontal divider below header
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )

            // Data Rows
            rows.forEachIndexed { rowIndex, row ->
                val isOdd = rowIndex % 2 == 1
                val rowBg = if (isOdd) altRowBg else Color.Transparent

                Row(
                    modifier = (if (needHorizontalScroll) Modifier.width(totalNeededWidth.dp) else Modifier.fillMaxWidth())
                        .height(IntrinsicSize.Min)
                        .background(rowBg),
                    verticalAlignment = Alignment.Top,
                ) {
                    row.take(columnCount).forEachIndexed { col, cell ->
                        if (col > 0) {
                            // Vertical divider line (竖线)
                            Box(
                                modifier = Modifier
                                    .width(0.8.dp)
                                    .fillMaxHeight()
                                    .background(dividerColor.copy(alpha = 0.85f)),
                            )
                        }

                        val colWidth = neededColWidths.getOrElse(col) { 64.dp }
                        val cellMod = when {
                            isCompactCol0 && col == 0 -> Modifier.width(col0ExactWidth)
                            isCompactCol0 && col == 1 -> Modifier.weight(1f)
                            needHorizontalScroll -> Modifier.width(colWidth)
                            isTwoColumn -> {
                                val ratio = (col0Units.toFloat() / (col0Units + col1Units).toFloat()).coerceIn(0.35f, 0.65f)
                                if (col == 0) Modifier.weight(ratio) else Modifier.weight(1f - ratio)
                            }
                            else -> Modifier.weight(colWidth.value.coerceAtLeast(0.1f))
                        }

                        Box(
                            modifier = cellMod
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Text(
                                text = buildMarkdownAnnotatedString(cell, primaryColor, accentColor, codeBgColor, boldColor = boldColor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.5.sp,
                                ),
                                color = primaryColor,
                            )
                        }
                    }
                }

                // Horizontal divider between rows
                if (rowIndex < rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.8.dp)
                            .background(dividerColor.copy(alpha = 0.75f)),
                    )
                }
            }
        }
    }
}

private fun estimateHtmlTableHeight(html: String): Dp {
    val rowCount = Regex("""<tr[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).count().coerceAtLeast(2)
    return (rowCount * 42 + 24).dp.coerceIn(70.dp, 800.dp)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlTableView(
    htmlContent: String,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = GeekColors.isDark
    val textColorHex = remember(primaryColor) {
        val argb = primaryColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }
    val accentColorHex = remember(accentColor) {
        val argb = accentColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }
    val tableBgHex = if (isDark) "#121722" else "#FFFFFF"
    val tableBorderHex = if (isDark) "#263348" else "#E2E8F0"
    val headerBgHex = if (isDark) "#1B2332" else "#F1F4F8"
    val dividerHex = if (isDark) "rgba(255, 255, 255, 0.12)" else "#E2E8F0"
    val altRowBgHex = if (isDark) "rgba(255, 255, 255, 0.03)" else "#F9FAFB"

    val initialEstimatedHeightDp = remember(htmlContent) {
        estimateHtmlTableHeight(htmlContent)
    }
    var viewHeightDp by remember(htmlContent) { mutableStateOf(initialEstimatedHeightDp) }
    var currentWebView by remember { mutableStateOf<MathFormulaWebView?>(null) }

    DisposableEffect(htmlContent) {
        onDispose {
            currentWebView?.let { wv ->
                MathWebViewPool.release(wv)
                currentWebView = null
            }
        }
    }

    val guiFontScale = LocalGuiFontScale.current
    val fullHtml = remember(htmlContent, textColorHex, accentColorHex, isDark, guiFontScale) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            html, body {
              background-color: transparent;
              color: $textColorHex;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif;
              font-size: ${12.5f * guiFontScale}px;
              line-height: 1.45;
              margin: 0;
              padding: 0;
              width: 100%;
              height: auto !important;
              overflow-x: auto;
              overflow-y: hidden;
            }
            #table-container {
              display: block;
              width: 100%;
              overflow-x: auto;
              padding: 1px 0;
              box-sizing: border-box;
            }
            table {
              border-collapse: collapse;
              width: 100%;
              margin: 0;
              background-color: $tableBgHex;
              border: 1px solid $tableBorderHex;
              border-radius: 8px;
              overflow: hidden;
            }
            th, td {
              border: 1px solid $dividerHex;
              padding: 8px 12px;
              text-align: left;
              vertical-align: middle;
              color: $textColorHex;
            }
            th {
              background-color: $headerBgHex;
              font-weight: 600;
              color: $accentColorHex;
              white-space: nowrap;
            }
            tr:nth-child(even) td {
              background-color: $altRowBgHex;
            }
          </style>
        </head>
        <body>
          <div id="table-container">
            $htmlContent
          </div>
          <script>
            function reportHeight() {
              var container = document.getElementById('table-container');
              if (!container) return;
              var rect = container.getBoundingClientRect();
              var h = Math.ceil(rect.height);
              if (h > 0 && window.AndroidBridge && window.AndroidBridge.onContentHeightDp) {
                window.AndroidBridge.onContentHeightDp(h + 6);
              }
            }
            window.addEventListener('load', reportHeight);
            if (window.ResizeObserver) {
              new ResizeObserver(reportHeight).observe(document.body);
            }
            setTimeout(reportHeight, 30);
            setTimeout(reportHeight, 150);
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF121722) else Color(0xFFFFFFFF),
        border = BorderStroke(0.8.dp, if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8F0)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewHeightDp),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewHeightDp),
                factory = { ctx ->
                    val wv = MathWebViewPool.acquire(ctx)
                    currentWebView = wv
                    wv.apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.textZoom = 100
                        settings.setSupportZoom(false)

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onContentHeightDp(heightDp: Int) {
                                    post {
                                        if (heightDp > 0) {
                                            val newDp = heightDp.dp.coerceIn(30.dp, 800.dp)
                                            if (kotlin.math.abs(viewHeightDp.value - newDp.value) >= 3f) {
                                                viewHeightDp = newDp
                                            }
                                        }
                                    }
                                }
                            },
                            "AndroidBridge"
                        )

                        tag = htmlContent to isDark
                        loadDataWithBaseURL("https://piremote.local/", fullHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    val lastTag = webView.tag as? Pair<*, *>
                    if (lastTag?.first != htmlContent || lastTag?.second != isDark) {
                        webView.tag = htmlContent to isDark
                        webView.loadDataWithBaseURL("https://piremote.local/", fullHtml, "text/html", "UTF-8", null)
                    }
                }
            )
        }
    }
}

@Composable
private fun MarkdownDefinitionListView(
    items: List<Pair<String, String>>,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GeekColors.CardElevated.copy(alpha = 0.5f),
        border = BorderStroke(0.8.dp, GeekColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            items.forEachIndexed { index, (term, desc) ->
                if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(GeekColors.BorderSubtle.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (term.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(13.dp)
                                .background(GeekColors.BrandAccent, RoundedCornerShape(1.5.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (containsMathFormula(term)) {
                            LatexMixedTextView(
                                content = term,
                                textColor = accentColor,
                                accentColor = accentColor,
                                codeBgColor = codeBgColor,
                                fontSizeSp = 14.5f,
                                fontWeightBold = true,
                            )
                        } else {
                            Text(
                                text = buildMarkdownAnnotatedString(term, accentColor, accentColor, codeBgColor),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                ),
                                color = accentColor,
                            )
                        }
                    }
                }
                if (desc.isNotEmpty()) {
                    Box(modifier = Modifier.padding(start = 11.dp, top = 2.dp)) {
                        if (containsMathFormula(desc)) {
                            LatexMixedTextView(
                                content = desc,
                                textColor = primaryColor,
                                accentColor = accentColor,
                                codeBgColor = codeBgColor,
                                fontSizeSp = 13.5f,
                            )
                        } else {
                            Text(
                                text = buildMarkdownAnnotatedString(desc, primaryColor, accentColor, codeBgColor),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = primaryColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownFootnotesView(
    items: List<Pair<String, String>>,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = GeekColors.CardElevated.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            items.forEachIndexed { index, (label, content) ->
                if (index > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(GeekColors.BorderSubtle.copy(alpha = 0.3f)),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "[$label]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = accentColor,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                    )
                    if (containsMathFormula(content)) {
                        LatexMixedTextView(
                            content = content,
                            textColor = GeekColors.TextSecondary,
                            accentColor = accentColor,
                            codeBgColor = codeBgColor,
                            fontSizeSp = 12.5f,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = buildMarkdownAnnotatedString(content, GeekColors.TextSecondary, accentColor, codeBgColor),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 18.5.sp,
                            ),
                            color = GeekColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = when {
        cal.get(java.util.Calendar.YEAR) != now.get(java.util.Calendar.YEAR) -> "yyyy-MM-dd HH:mm"
        cal.get(java.util.Calendar.DAY_OF_YEAR) != now.get(java.util.Calendar.DAY_OF_YEAR) -> "MM-dd HH:mm"
        else -> "HH:mm"
    }
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(millis))
}

@Composable
private fun AlignedColumnsView(
    rows: List<List<String>>,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
) {
    if (rows.isEmpty()) return
    val columnCount = rows.first().size
    val isDark = GeekColors.isDark
    val cardBg = if (isDark) Color(0xFF141923) else Color(0xFFF6F8FA)
    val borderColor = if (isDark) Color(0x20FFFFFF) else Color(0xFFE2E8F0)
    val dividerColor = if (isDark) Color(0x14FFFFFF) else Color(0xFFEDF2F7)

    val col0MaxUnits = remember(rows) {
        var m = 4
        for (r in rows) {
            val w = visualWidth(r.getOrElse(0) { "" })
            if (w > m) m = w
        }
        m
    }
    val col0Width = remember(col0MaxUnits) {
        (col0MaxUnits * 8.5f + 16f).coerceIn(60f, 150f).dp
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = cardBg,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            rows.forEachIndexed { index, row ->
                if (columnCount == 2) {
                    val key = row.getOrElse(0) { "" }
                    val value = row.getOrElse(1) { "" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildMarkdownAnnotatedString(key, GeekColors.TextSecondary, accentColor, codeBgColor),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                            ),
                            color = GeekColors.TextSecondary,
                            modifier = Modifier.width(col0Width),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = buildMarkdownAnnotatedString(value, primaryColor, accentColor, codeBgColor),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 18.5.sp,
                            ),
                            color = primaryColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEachIndexed { cIdx, cell ->
                            Text(
                                text = buildMarkdownAnnotatedString(cell, if (cIdx == 0) GeekColors.TextSecondary else primaryColor, accentColor, codeBgColor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = if (cIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (cIdx == 0) GeekColors.TextSecondary else primaryColor,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (index < rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(dividerColor),
                    )
                }
            }
        }
    }
}

private val formulaHeightCache = java.util.concurrent.ConcurrentHashMap<String, Dp>()

private fun estimateFormulaHeight(latex: String): Dp {
    formulaHeightCache[latex]?.let { return it }
    val lines = latex.split(Regex("""\\\\|\n""")).filter { it.isNotBlank() }
    val lineCount = lines.size
    val isMatrix = latex.contains("\\begin{matrix}") || latex.contains("\\begin{pmatrix}") ||
                   latex.contains("\\begin{bmatrix}") || latex.contains("\\begin{cases}") ||
                   latex.contains("\\begin{aligned}")
    val hasFraction = latex.contains("\\frac")
    val hasBigOp = latex.contains("\\sum") || latex.contains("\\int") || latex.contains("\\prod") || latex.contains("\\lim")

    return when {
        isMatrix -> (lineCount.coerceAtLeast(2) * 26 + 24).dp.coerceIn(60.dp, 400.dp)
        lineCount > 1 -> (lineCount * 28 + 16).dp.coerceIn(50.dp, 300.dp)
        hasFraction && hasBigOp -> 62.dp
        hasFraction -> 52.dp
        hasBigOp -> 50.dp
        else -> 38.dp
    }
}

private fun estimateMixedTextHeight(text: String): Dp {
    formulaHeightCache[text]?.let { return it }
    val lineCount = text.lines().size.coerceAtLeast(1)
    val hasBlockMath = text.contains("$$") || text.contains("\\[")
    val hasFraction = text.contains("\\frac")
    val base = (lineCount * 22).dp
    return when {
        hasBlockMath -> (base.value + 40).dp
        hasFraction -> (base.value + 12).dp
        else -> (base.value + 4).dp
    }.coerceIn(22.dp, 600.dp)
}

private object MathWebViewPool {
    private val pool = ArrayDeque<MathFormulaWebView>()
    private const val MAX_POOL = 8
    private var currentContextRef: java.lang.ref.WeakReference<Context>? = null

    fun acquire(context: Context): MathFormulaWebView {
        synchronized(pool) {
            val lastContext = currentContextRef?.get()
            if (lastContext != null && lastContext !== context) {
                while (pool.isNotEmpty()) {
                    try {
                        pool.removeFirst().destroy()
                    } catch (_: Throwable) {}
                }
            }
            currentContextRef = java.lang.ref.WeakReference(context)

            while (pool.isNotEmpty()) {
                val wv = pool.removeFirst()
                (wv.parent as? ViewGroup)?.removeView(wv)
                return wv
            }
        }
        return MathFormulaWebView(context)
    }

    fun release(wv: MathFormulaWebView) {
        synchronized(pool) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            if (pool.size < MAX_POOL) {
                try {
                    wv.stopLoading()
                } catch (_: Throwable) {}
                pool.addLast(wv)
            } else {
                try {
                    wv.destroy()
                } catch (_: Throwable) {}
            }
        }
    }
}

private class MathFormulaWebView(context: Context) : WebView(context) {
    private var startX = 0f
    private var startY = 0f
    private var isHorizontalDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isHorizontalDragging = false
                // 不在 DOWN 阶段提前锁定拦截，确保外层容器（LazyColumn）正常接收初始触控，保持上下滑动绝对顺畅无卡顿
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.x - startX)
                val dy = kotlin.math.abs(event.y - startY)

                if (!isHorizontalDragging) {
                    if (dx > touchSlop && dx > dy) {
                        // 判定为先左右滑动：锁定父级，禁止外层页面上下滚动
                        isHorizontalDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dy > touchSlop && dy > dx) {
                        // 判定为先上下滑动：明确允许外层页面拦截并正常翻页
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                } else {
                    // 已锁定横向滑动：全程禁止外层页面上下滚动
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isHorizontalDragging = false
            }
        }
        return super.dispatchTouchEvent(event)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LatexMathView(
    latex: String,
    textColor: Color,
    backgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
) {
    val initialEstimatedHeightDp = remember(latex) {
        estimateFormulaHeight(latex)
    }
    var viewHeightDp by remember(latex) { mutableStateOf(initialEstimatedHeightDp) }
    var currentWebView by remember { mutableStateOf<MathFormulaWebView?>(null) }

    DisposableEffect(latex) {
        onDispose {
            currentWebView?.let { wv ->
                MathWebViewPool.release(wv)
                currentWebView = null
            }
        }
    }

    val textColorHex = remember(textColor) {
        val argb = textColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }
    val quotedLatex = remember(latex) { JSONObject.quote(latex.trim()) }

    val guiFontScale = LocalGuiFontScale.current
    val html = remember(latex, textColorHex, guiFontScale) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
          <style>
            * {
              box-sizing: border-box;
              margin: 0;
              padding: 0;
            }
            html, body {
              background-color: transparent;
              color: $textColorHex;
              font-family: 'KaTeX_Main', 'Cambria Math', 'Times New Roman', serif;
              font-size: ${16f * guiFontScale}px;
              line-height: 1.2;
              margin: 0;
              padding: 0;
              width: 100%;
              height: auto !important;
              min-height: 0 !important;
              overflow-x: auto;
              overflow-y: hidden;
            }
            #math-wrapper {
              display: flex;
              justify-content: center;
              align-items: center;
              min-width: 100%;
              width: max-content;
              margin: 0;
              padding: 4px 6px;
              box-sizing: border-box;
              min-height: 0 !important;
            }
            #math-target {
              display: inline-block;
              text-align: center;
              min-height: 0 !important;
            }
            .katex-display {
              margin: 0 !important;
              padding: 0 !important;
              text-align: center;
            }
            .katex {
              font-size: 1.15em;
              color: $textColorHex;
              text-rendering: geometricPrecision;
            }
          </style>
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        </head>
        <body>
          <div id="math-wrapper">
            <div id="math-target"></div>
          </div>
          <script>
            var rendered = false;

            function reportFormattedHeight() {
              var target = document.getElementById('math-target');
              if (!target) return;

              // 严格根据格式化后的 KaTeX 渲染节点判定高度
              var katexEl = target.querySelector('.katex-display') || target.querySelector('.katex');
              if (!katexEl) {
                // 尚未完成 KaTeX 格式化渲染，不作判定
                return;
              }

              var rect = katexEl.getBoundingClientRect();
              if (!rect || rect.height <= 0) return;

              // 获取格式化后的实际高度 (CSS 像素即对应 Android dp)
              var h = Math.ceil(rect.height);

              var wrapper = document.getElementById('math-wrapper');
              if (wrapper) {
                var wrapRect = wrapper.getBoundingClientRect();
                if (wrapRect && wrapRect.height > h) {
                  h = Math.ceil(wrapRect.height);
                }
              }

              // 增加微小上下留白边距 (4px)，确保复杂上下标/分式线不贴边
              var finalDp = h + 4;

              if (window.AndroidBridge && window.AndroidBridge.onContentHeightDp) {
                window.AndroidBridge.onContentHeightDp(finalDp);
              }
            }

            function doRender() {
              if (rendered) {
                reportFormattedHeight();
                return;
              }
              var target = document.getElementById('math-target');
              if (!window.katex || !target) return;

              try {
                katex.render($quotedLatex, target, {
                  displayMode: true,
                  throwOnError: false
                });
                rendered = true;
              } catch (e) {
                target.innerText = $quotedLatex;
                rendered = true;
              }

              reportFormattedHeight();

              if (window.requestAnimationFrame) {
                requestAnimationFrame(function() {
                  reportFormattedHeight();
                  requestAnimationFrame(reportFormattedHeight);
                });
              }
              if (document.fonts && document.fonts.ready) {
                document.fonts.ready.then(function() {
                  reportFormattedHeight();
                });
              }
            }

            function waitAndRender() {
              if (window.katex) {
                doRender();
              } else {
                setTimeout(waitAndRender, 25);
              }
            }

            if (window.ResizeObserver) {
              var ro = new ResizeObserver(function() {
                if (rendered) {
                  reportFormattedHeight();
                }
              });
              var target = document.getElementById('math-target');
              if (target) ro.observe(target);
              var wrapper = document.getElementById('math-wrapper');
              if (wrapper) ro.observe(wrapper);
            }

            if (document.readyState === 'loading') {
              document.addEventListener('DOMContentLoaded', waitAndRender);
            } else {
              waitAndRender();
            }
            window.addEventListener('load', waitAndRender);

            // 弱网离线兜底
            setTimeout(function() {
              if (!rendered) {
                var target = document.getElementById('math-target');
                if (target) {
                  target.innerText = $quotedLatex;
                  rendered = true;
                  var rect = target.getBoundingClientRect();
                  var h = Math.ceil(rect.height) + 6;
                  if (window.AndroidBridge && window.AndroidBridge.onContentHeightDp) {
                    window.AndroidBridge.onContentHeightDp(h);
                  }
                }
              }
            }, 3000);
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(viewHeightDp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewHeightDp),
            factory = { ctx ->
                val wv = MathWebViewPool.acquire(ctx)
                currentWebView = wv
                wv.apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = true
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.textZoom = 100
                    settings.setSupportZoom(false)

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onContentHeightDp(heightDp: Int) {
                                post {
                                    if (heightDp > 0) {
                                        val newDp = heightDp.dp.coerceIn(24.dp, 800.dp)
                                        formulaHeightCache[latex] = newDp
                                        if (kotlin.math.abs(viewHeightDp.value - newDp.value) >= 3f) {
                                            viewHeightDp = newDp
                                        }
                                    }
                                }
                            }
                        },
                        "AndroidBridge"
                    )

                    tag = latex to textColorHex
                    loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                val lastTag = webView.tag as? Pair<*, *>
                if (lastTag?.first != latex || lastTag?.second != textColorHex) {
                    webView.tag = latex to textColorHex
                    webView.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                }
            }
        )
    }
}

@Composable
private fun MarkdownMathCard(
    latex: String,
    primaryColor: Color,
    accentColor: Color,
) {
    val isDark = GeekColors.isDark
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GeekColors.DeepCanvas,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF263348) else Color(0xFFE2E7F0)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        LatexMathView(
            latex = latex,
            textColor = primaryColor,
            backgroundColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LatexMixedTextView(
    content: String,
    textColor: Color,
    accentColor: Color,
    codeBgColor: Color,
    fontSizeSp: Float = 15f,
    fontWeightBold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = GeekColors.isDark
    val initialEstimatedHeightDp = remember(content) {
        estimateMixedTextHeight(content)
    }
    var viewHeightDp by remember(content) { mutableStateOf(initialEstimatedHeightDp) }
    var currentWebView by remember { mutableStateOf<MathFormulaWebView?>(null) }

    DisposableEffect(content) {
        onDispose {
            currentWebView?.let { wv ->
                MathWebViewPool.release(wv)
                currentWebView = null
            }
        }
    }

    val textColorHex = remember(textColor) {
        val argb = textColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }
    val accentColorHex = remember(accentColor) {
        val argb = accentColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }
    val codeBgHex = remember(codeBgColor) {
        val argb = codeBgColor.toArgb()
        val a = ((argb shr 24) and 0xFF) / 255f
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        "rgba($r, $g, $b, $a)"
    }
    val quotedContent = remember(content) { JSONObject.quote(content.trim()) }
    val guiFontScale = LocalGuiFontScale.current
    val effectiveFontSizeSp = fontSizeSp * guiFontScale

    val html = remember(content, textColorHex, accentColorHex, codeBgHex, effectiveFontSizeSp, fontWeightBold, isDark) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
          <style>
            * {
              box-sizing: border-box;
              margin: 0;
              padding: 0;
            }
            html, body {
              background-color: transparent;
              color: $textColorHex;
              font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
              font-size: ${effectiveFontSizeSp}px;
              font-weight: ${if (fontWeightBold) "700" else "500"};
              line-height: 1.55;
              margin: 0;
              padding: 0;
              width: 100%;
              height: auto !important;
              min-height: 0 !important;
              overflow-x: auto;
              overflow-y: hidden;
              word-break: break-word;
              -webkit-font-smoothing: antialiased;
              text-rendering: optimizeLegibility;
            }
            #math-wrapper {
              display: block;
              width: 100%;
              margin: 0;
              padding: 1px 0;
              box-sizing: border-box;
              min-height: 0 !important;
            }
            #math-target {
              display: block;
              width: 100%;
              min-height: 0 !important;
            }
            .katex {
              font-size: 1.1em;
              color: $textColorHex;
              text-rendering: geometricPrecision;
            }
            .katex-display {
              margin: 6px 0 !important;
              padding: 0 !important;
              text-align: center;
              overflow-x: auto;
              overflow-y: hidden;
            }
            code {
              font-family: 'JetBrains Mono', 'Roboto Mono', Menlo, Monaco, Consolas, monospace;
              font-size: 0.9em;
              background-color: $codeBgHex;
              padding: 1px 4px;
              border-radius: 4px;
              color: $accentColorHex;
            }
            kbd {
              display: inline-block;
              padding: 1px 5px;
              font-family: -apple-system, BlinkMacSystemFont, "JetBrains Mono", Consolas, monospace;
              font-size: 0.85em;
              font-weight: 600;
              line-height: 1.2;
              color: $textColorHex;
              background-color: ${if (isDark) "#263248" else "#E5E9F0"};
              border: 1px solid ${if (isDark) "#3B4B68" else "#CBD5E1"};
              border-bottom: 2px solid ${if (isDark) "#1E2738" else "#94A3B8"};
              border-radius: 4px;
              box-shadow: 0 1px 1px rgba(0,0,0,0.15);
              margin: 0 2px;
              vertical-align: baseline;
            }
            mark {
              background-color: ${if (isDark) "rgba(250, 204, 21, 0.3)" else "#FEF08A"};
              color: ${if (isDark) "#FEF08A" else "#854D0E"};
              padding: 1px 4px;
              border-radius: 3px;
              font-weight: 500;
            }
            .footnote-ref {
              color: $accentColorHex;
              font-size: 0.75em;
              font-weight: 600;
            }
            sub, sup {
              font-size: 75%;
              line-height: 0;
              position: relative;
              vertical-align: baseline;
            }
            sup { top: -0.5em; }
            sub { bottom: -0.25em; }
            strong {
              font-weight: 700;
              color: $textColorHex;
            }
            em {
              font-style: italic;
            }
            .math-error {
              color: #F87171;
              font-family: monospace;
              font-size: 0.9em;
            }
          </style>
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        </head>
        <body>
          <div id="math-wrapper">
            <div id="math-target"></div>
          </div>
          <script>
            var rendered = false;

            function escapeHtml(str) {
              return str
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
            }

            function renderMixed(raw) {
              var tokenRegex = /(\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|\$(?:[^\s$]|(?:[^\s$][^$\n]*?[^\s$]))\$)/g;
              var parts = raw.split(tokenRegex);
              var res = "";

              for (var i = 0; i < parts.length; i++) {
                var part = parts[i];
                if (!part) continue;

                if (part.startsWith('$$') && part.endsWith('$$') && part.length >= 4) {
                  var tex = part.slice(2, -2).trim();
                  try {
                    res += katex.renderToString(tex, { displayMode: true, throwOnError: false });
                  } catch (e) {
                    res += escapeHtml(part);
                  }
                } else if (part.startsWith('\\[') && part.endsWith('\\]') && part.length >= 4) {
                  var tex = part.slice(2, -2).trim();
                  try {
                    res += katex.renderToString(tex, { displayMode: true, throwOnError: false });
                  } catch (e) {
                    res += escapeHtml(part);
                  }
                } else if (part.startsWith('\\(') && part.endsWith('\\)') && part.length >= 4) {
                  var tex = part.slice(2, -2).trim();
                  try {
                    res += katex.renderToString(tex, { displayMode: false, throwOnError: false });
                  } catch (e) {
                    res += escapeHtml(part);
                  }
                } else if (part.startsWith('$') && part.endsWith('$') && part.length >= 2) {
                  var tex = part.slice(1, -1).trim();
                  if (/^\d+([.,]\d+)?$/.test(tex)) {
                    res += escapeHtml(part);
                  } else {
                    try {
                      res += katex.renderToString(tex, { displayMode: false, throwOnError: false });
                    } catch (e) {
                      res += escapeHtml(part);
                    }
                  }
                } else {
                  var textHtml = escapeHtml(part);
                  textHtml = textHtml.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
                  textHtml = textHtml.replace(/\*([^\s*].*?[^\s*]|[^\s*])\*/g, '<em>$1</em>');
                  textHtml = textHtml.replace(/`([^`]+)`/g, '<code>$1</code>');
                  textHtml = textHtml.replace(/==([^=\n]+)==/g, '<mark>$1</mark>');
                  textHtml = textHtml.replace(/\[\^([^\]]+)\](?!\s*:)/g, '<sup class="footnote-ref">[$1]</sup>');
                  textHtml = textHtml.replace(/(?<!~)~([^~\s](?:[^~]*?[^~\s])?)~(?!~)/g, '<sub>$1</sub>');
                  textHtml = textHtml.replace(/\^([^\^\s](?:[^\^]*?[^\^\s])?)\^/g, '<sup>$1</sup>');
                  textHtml = textHtml.replace(/&lt;kbd&gt;([\s\S]*?)&lt;\/kbd&gt;/gi, '<kbd>$1</kbd>');
                  textHtml = textHtml.replace(/&lt;sub&gt;([\s\S]*?)&lt;\/sub&gt;/gi, '<sub>$1</sub>');
                  textHtml = textHtml.replace(/&lt;sup&gt;([\s\S]*?)&lt;\/sup&gt;/gi, '<sup>$1</sup>');
                  textHtml = textHtml.replace(/&lt;mark&gt;([\s\S]*?)&lt;\/mark&gt;/gi, '<mark>$1</mark>');
                  textHtml = textHtml.replace(/&lt;del&gt;([\s\S]*?)&lt;\/del&gt;/gi, '<del>$1</del>');
                  textHtml = textHtml.replace(/&lt;s&gt;([\s\S]*?)&lt;\/s&gt;/gi, '<s>$1</s>');
                  textHtml = textHtml.replace(/\n/g, '<br>');
                  res += textHtml;
                }
              }
              return res;
            }

            function reportFormattedHeight() {
              var target = document.getElementById('math-target');
              if (!target) return;
              var rect = target.getBoundingClientRect();
              if (!rect || rect.height <= 0) return;
              var h = Math.ceil(rect.height);
              var wrapper = document.getElementById('math-wrapper');
              if (wrapper) {
                var wrapRect = wrapper.getBoundingClientRect();
                if (wrapRect && wrapRect.height > h) {
                  h = Math.ceil(wrapRect.height);
                }
              }
              var finalDp = h + 2;
              if (window.AndroidBridge && window.AndroidBridge.onContentHeightDp) {
                window.AndroidBridge.onContentHeightDp(finalDp);
              }
            }

            function doRender() {
              if (rendered) {
                reportFormattedHeight();
                return;
              }
              var target = document.getElementById('math-target');
              if (!window.katex || !target) return;

              try {
                target.innerHTML = renderMixed($quotedContent);
                rendered = true;
              } catch (e) {
                target.innerText = $quotedContent;
                rendered = true;
              }

              reportFormattedHeight();

              if (window.requestAnimationFrame) {
                requestAnimationFrame(function() {
                  reportFormattedHeight();
                  requestAnimationFrame(reportFormattedHeight);
                });
              }
              if (document.fonts && document.fonts.ready) {
                document.fonts.ready.then(function() {
                  reportFormattedHeight();
                });
              }
            }

            function waitAndRender() {
              if (window.katex) {
                doRender();
              } else {
                setTimeout(waitAndRender, 25);
              }
            }

            if (window.ResizeObserver) {
              var ro = new ResizeObserver(function() {
                if (rendered) {
                  reportFormattedHeight();
                }
              });
              var target = document.getElementById('math-target');
              if (target) ro.observe(target);
              var wrapper = document.getElementById('math-wrapper');
              if (wrapper) ro.observe(wrapper);
            }

            if (document.readyState === 'loading') {
              document.addEventListener('DOMContentLoaded', waitAndRender);
            } else {
              waitAndRender();
            }
            window.addEventListener('load', waitAndRender);

            setTimeout(function() {
              if (!rendered) {
                var target = document.getElementById('math-target');
                if (target) {
                  target.innerText = $quotedContent;
                  rendered = true;
                  var rect = target.getBoundingClientRect();
                  var h = Math.ceil(rect.height) + 4;
                  if (window.AndroidBridge && window.AndroidBridge.onContentHeightDp) {
                    window.AndroidBridge.onContentHeightDp(h);
                  }
                }
              }
            }, 3000);
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(viewHeightDp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewHeightDp),
            factory = { ctx ->
                val wv = MathWebViewPool.acquire(ctx)
                currentWebView = wv
                wv.apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = true
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.textZoom = 100
                    settings.setSupportZoom(false)

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onContentHeightDp(heightDp: Int) {
                                post {
                                    if (heightDp > 0) {
                                        val newDp = heightDp.dp.coerceIn(18.dp, 800.dp)
                                        formulaHeightCache[content] = newDp
                                        if (kotlin.math.abs(viewHeightDp.value - newDp.value) >= 3f) {
                                            viewHeightDp = newDp
                                        }
                                    }
                                }
                            }
                        },
                        "AndroidBridge"
                    )

                    tag = content to (textColorHex + accentColorHex)
                    loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                val lastTag = webView.tag as? Pair<*, *>
                val currentKey = textColorHex + accentColorHex
                if (lastTag?.first != content || lastTag?.second != currentKey) {
                    webView.tag = content to currentKey
                    webView.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                }
            }
        )
    }
}

@Composable
private fun MarkdownDetailsCard(
    summary: String,
    content: String,
    defaultOpen: Boolean,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
) {
    var expanded by remember { mutableStateOf(defaultOpen) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "details_arrow",
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GeekColors.CardElevated.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (GeekColors.isDark) Color(0xFF263348) else Color(0xFFE2E7F0)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = GeekColors.BrandAccent,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = rotationState },
                )
                Spacer(Modifier.width(8.dp))
                if (containsMathFormula(summary)) {
                    LatexMixedTextView(
                        content = summary.ifBlank { "点击展开详情" },
                        textColor = primaryColor,
                        accentColor = accentColor,
                        codeBgColor = codeBgColor,
                        fontSizeSp = 14f,
                        fontWeightBold = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = buildMarkdownAnnotatedString(
                            markdown = summary.ifBlank { "点击展开详情" },
                            primaryColor = primaryColor,
                            accentColor = accentColor,
                            codeBgColor = codeBgColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = primaryColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(if (GeekColors.isDark) Color(0xFF263348) else Color(0xFFE2E7F0)),
                    )
                    Spacer(Modifier.height(8.dp))
                    MarkdownContentView(
                        content = content.trim(),
                        customTextColor = primaryColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockquoteView(
    content: String,
    primaryColor: Color,
    accentColor: Color,
    codeBgColor: Color,
    boldColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = GeekColors.CardElevated.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, GeekColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(GeekColors.BrandAccent, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                MarkdownContentView(
                    content = content.trim(),
                    customTextColor = GeekColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * Renders Markdown text with headings, blockquotes, lists, bold, italic, and inline code.
 */
@Composable
fun MarkdownContentView(
    content: String,
    modifier: Modifier = Modifier,
    customTextColor: Color? = null,
) {
    val lines = remember(content) { content.lines() }
    val blocks = remember(content) { splitMarkdownBlocks(lines) }
    val boldColor = customTextColor ?: GeekColors.TextPrimary
    val primaryColor = customTextColor ?: GeekColors.TextPrimary.copy(alpha = 0.75f)
    val accentColor = if (customTextColor != null) customTextColor.copy(alpha = 0.9f) else GeekColors.TerminalCyan
    val codeBgColor = if (customTextColor != null) Color.White.copy(alpha = 0.15f) else GeekColors.CardElevated

    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Blockquote -> {
                    MarkdownBlockquoteView(
                        content = block.content,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        codeBgColor = codeBgColor,
                        boldColor = boldColor,
                    )
                    continue
                }
                is MdBlock.HtmlTable -> {
                    HtmlTableView(block.html, primaryColor, accentColor)
                    continue
                }
                is MdBlock.DefinitionList -> {
                    MarkdownDefinitionListView(block.items, primaryColor, accentColor, codeBgColor)
                    continue
                }
                is MdBlock.Footnotes -> {
                    MarkdownFootnotesView(block.items, primaryColor, accentColor, codeBgColor)
                    continue
                }
                is MdBlock.Table -> {
                    MarkdownTableView(block.lines, primaryColor, accentColor, codeBgColor, boldColor = boldColor)
                    continue
                }
                is MdBlock.AlignedColumns -> {
                    AlignedColumnsView(block.rows, primaryColor, accentColor, codeBgColor)
                    continue
                }
                is MdBlock.Math -> {
                    MarkdownMathCard(block.latex, primaryColor, accentColor)
                    continue
                }
                is MdBlock.Details -> {
                    MarkdownDetailsCard(
                        summary = block.summary,
                        content = block.content,
                        defaultOpen = block.defaultOpen,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        codeBgColor = codeBgColor,
                    )
                    continue
                }
                is MdBlock.Code -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeekColors.DeepCanvas,
                        border = BorderStroke(1.dp, GeekColors.BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = block.content,
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
                    continue
                }
                is MdBlock.ListItem -> {
                    val startPadding = when (block.level) {
                        0 -> 0.dp
                        1 -> 18.dp
                        2 -> 34.dp
                        3 -> 48.dp
                        else -> (block.level * 16).dp
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = startPadding)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (block.isTask) {
                            val boxBorderColor = if (block.isChecked) GeekColors.BrandAccent else if (GeekColors.isDark) Color(0xFF6A7994) else Color(0xFF9AA5B8)
                            val boxBgColor = if (block.isChecked) GeekColors.BrandAccent else if (GeekColors.isDark) Color(0xFF1B2230) else Color(0xFFEDF0F5)
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp, end = 8.dp)
                                    .size(16.dp)
                                    .background(
                                        color = boxBgColor,
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = boxBorderColor,
                                        shape = RoundedCornerShape(4.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (block.isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已完成",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                            if (containsMathFormula(block.content)) {
                                LatexMixedTextView(
                                    content = block.content,
                                    textColor = if (block.isChecked) GeekColors.TextSecondary else primaryColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 15f,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(
                                        block.content,
                                        if (block.isChecked) GeekColors.TextSecondary else primaryColor,
                                        accentColor,
                                        codeBgColor,
                                        boldColor = boldColor,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp,
                                        textDecoration = if (block.isChecked) TextDecoration.LineThrough else null,
                                    ),
                                    color = if (block.isChecked) GeekColors.TextSecondary else primaryColor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else if (block.isOrdered) {
                            val numberColor = when (block.level) {
                                0 -> GeekColors.BrandAccent
                                1 -> GeekColors.TerminalCyan
                                2 -> GeekColors.TextSecondary
                                else -> GeekColors.TextSecondary.copy(alpha = 0.8f)
                            }
                            Text(
                                text = block.prefix,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = numberColor,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            if (containsMathFormula(block.content)) {
                                LatexMixedTextView(
                                    content = block.content,
                                    textColor = primaryColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 15f,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(block.content, primaryColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = primaryColor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            val (bulletText, bulletColor) = when (block.level) {
                                0 -> Pair("•", GeekColors.TerminalCyan)
                                1 -> Pair("◦", GeekColors.BrandAccent)
                                2 -> Pair("▪", GeekColors.TextSecondary)
                                3 -> Pair("▫", GeekColors.TextSecondary.copy(alpha = 0.8f))
                                else -> Pair("–", GeekColors.TextSecondary.copy(alpha = 0.6f))
                            }
                            Text(
                                text = bulletText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (block.level == 1) 16.sp else 14.sp,
                                ),
                                color = bulletColor,
                                modifier = Modifier.width(16.dp),
                            )
                            if (containsMathFormula(block.content)) {
                                LatexMixedTextView(
                                    content = block.content,
                                    textColor = primaryColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 15f,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(block.content, primaryColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = primaryColor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                is MdBlock.ListContinuation -> {
                    val startPadding = when (block.level) {
                        0 -> 16.dp
                        1 -> 34.dp
                        2 -> 50.dp
                        3 -> 64.dp
                        else -> (block.level * 16 + 16).dp
                    }
                    if (containsMathFormula(block.content)) {
                        LatexMixedTextView(
                            content = block.content,
                            textColor = primaryColor,
                            accentColor = accentColor,
                            codeBgColor = codeBgColor,
                            fontSizeSp = 15f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = startPadding)
                                .padding(vertical = 1.dp),
                        )
                    } else {
                        Text(
                            text = buildMarkdownAnnotatedString(block.content, primaryColor, accentColor, codeBgColor, boldColor = boldColor),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = primaryColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = startPadding)
                                .padding(vertical = 1.dp),
                        )
                    }
                }
                is MdBlock.Line -> {
                    val rawLine = block.raw
                    val trimmed = rawLine.trim()
                    when {
                        trimmed.isEmpty() -> {
                            Spacer(Modifier.height(4.dp))
                        }
                        trimmed.startsWith("#### ") -> {
                            val hText = trimmed.removePrefix("#### ").trim()
                            Spacer(Modifier.height(3.dp))
                            if (containsMathFormula(hText)) {
                                LatexMixedTextView(
                                    content = hText,
                                    textColor = boldColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 14f,
                                    fontWeightBold = true,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(hText, boldColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = boldColor,
                                )
                            }
                        }
                        trimmed.startsWith("### ") -> {
                            val hText = trimmed.removePrefix("### ").trim()
                            Spacer(Modifier.height(4.dp))
                            if (containsMathFormula(hText)) {
                                LatexMixedTextView(
                                    content = hText,
                                    textColor = boldColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 15f,
                                    fontWeightBold = true,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(hText, boldColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = boldColor,
                                )
                            }
                        }
                        trimmed.startsWith("## ") -> {
                            val hText = trimmed.removePrefix("## ").trim()
                            Spacer(Modifier.height(6.dp))
                            if (containsMathFormula(hText)) {
                                LatexMixedTextView(
                                    content = hText,
                                    textColor = boldColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 17f,
                                    fontWeightBold = true,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(hText, boldColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = boldColor,
                                )
                            }
                        }
                        trimmed.startsWith("# ") -> {
                            val hText = trimmed.removePrefix("# ").trim()
                            Spacer(Modifier.height(8.dp))
                            if (containsMathFormula(hText)) {
                                LatexMixedTextView(
                                    content = hText,
                                    textColor = boldColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 19f,
                                    fontWeightBold = true,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(hText, boldColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = boldColor,
                                )
                            }
                        }
                        trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(GeekColors.BorderSubtle),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        trimmed.startsWith(">") -> {
                            MarkdownBlockquoteView(
                                content = trimmed.removePrefix(">").trim(),
                                primaryColor = primaryColor,
                                accentColor = accentColor,
                                codeBgColor = codeBgColor,
                                boldColor = boldColor,
                            )
                        }
                        else -> {
                            if (containsMathFormula(rawLine)) {
                                LatexMixedTextView(
                                    content = rawLine,
                                    textColor = primaryColor,
                                    accentColor = accentColor,
                                    codeBgColor = codeBgColor,
                                    fontSizeSp = 15f,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            } else {
                                Text(
                                    text = buildMarkdownAnnotatedString(rawLine, primaryColor, accentColor, codeBgColor, boldColor = boldColor),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = primaryColor,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive options card for agent questions, confirmation prompts, and selection menus.
 *
 * Four answer shapes driven by [QuestionOptionBlock.method] / [QuestionOptionBlock.isMultiSelect]:
 * - select → tappable option buttons ([onSelectOption])
 * - multi_select / multiselect → checkboxes with batch submission ([onSelectMultiple])
 * - confirm → 是/否 buttons ([onConfirmAnswer])
 * - input/editor → inline free-text field ([onSubmitText]), so text questions
 *   never force the user down to the global composer
 *
 * For RPC sessions ([QuestionOptionBlock.requestId] set) the answers travel
 * back as `extension_ui_response`; PTY sessions fall back to key injection.
 */
@Composable
fun QuestionOptionCard(
    item: QuestionOptionBlock,
    onSelectOption: (option: InteractiveOption) -> Unit,
    modifier: Modifier = Modifier,
    onConfirmAnswer: ((confirmed: Boolean) -> Unit)? = null,
    onSubmitText: ((text: String) -> Unit)? = null,
    onSelectMultiple: ((options: List<InteractiveOption>, customText: String?) -> Unit)? = null,
) {
    val autoExtractedOptions = remember(item.question, item.options) {
        if (item.options.isNotEmpty()) item.options
        else TerminalChatParser.extractOptionsFromText(item.question)
    }
    val hasInlineOptions = autoExtractedOptions.size >= 2
    val effectiveOptions = if (item.options.isNotEmpty()) item.options else autoExtractedOptions

    val isMulti = item.isMultiSelect || item.method == "multi_select" || item.method == "multiselect" ||
        (hasInlineOptions && TerminalChatParser.detectIsMulti(item.question, item.placeholder))

    val displayQuestionText = remember(item.question, hasInlineOptions) {
        if (hasInlineOptions) TerminalChatParser.extractTitleOnly(item.question)
        else item.question
    }

    var localSelectedKey by remember(item.id, item.selectedKey) { mutableStateOf(item.selectedKey) }
    var localSelectedKeys by remember(item.id, item.selectedKeys, item.selectedKey) {
        mutableStateOf(
            if (item.selectedKeys.isNotEmpty()) item.selectedKeys
            else item.selectedKey?.let { str ->
                str.split(", ", ",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } ?: emptySet()
        )
    }
    var localCustomText by remember(item.id, item.customInput) { mutableStateOf(item.customInput.orEmpty()) }
    var showCustomInput by remember(item.id, item.allowCustomInput) { mutableStateOf(item.allowCustomInput || item.customInput != null) }

    val isAnswered = item.isAnswered || (if (isMulti) item.isAnswered || item.selectedKeys.isNotEmpty() else localSelectedKey != null)

    // Answered cards default to collapsed summary view; pending cards start expanded
    var isCollapsed by remember(item.id, isAnswered) { mutableStateOf(isAnswered) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBgColor by animateColorAsState(
        targetValue = if (isPressed) GeekColors.CardHighlight else GeekColors.CardSurface,
        animationSpec = tween(120),
        label = "optCardBg",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (!isCollapsed) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "optChevronRotation",
    )

    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        shape = cardShape,
        color = cardBgColor,
        border = null,
            modifier = modifier
                .fillMaxWidth()
                .clip(cardShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        isCollapsed = !isCollapsed
                    },
                ),
        ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            val summaryTitle = when {
                item.method == "confirm" -> "确认操作"
                isMulti -> "多选选项"
                !hasInlineOptions && (item.method == "input" || item.method == "editor") -> "文本输入"
                else -> "选项详情"
            }

            // Header row (Fixed title matching TurnSummaryCard, never changes on expand/collapse)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = if (isAnswered) Icons.Default.Check else Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isAnswered) GeekColors.NeonGreen else if (isMulti) GeekColors.TerminalCyan else GeekColors.BrandAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = summaryTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = GeekColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val selectedCount = effectiveOptions.count { opt ->
                        if (isMulti) {
                            localSelectedKeys.contains(opt.key) || localSelectedKeys.contains(opt.label) ||
                                (localSelectedKey != null && localSelectedKey!!.split(", ", ",").map { it.trim() }.any { it == opt.key || it == opt.label })
                        } else {
                            localSelectedKey == opt.key || localSelectedKey == opt.label || localSelectedKeys.contains(opt.key) || localSelectedKeys.contains(opt.label)
                        }
                    }
                    val statusText = when {
                        isAnswered && isMulti -> if (selectedCount > 0) "已选 $selectedCount 项" else "已完成"
                        isAnswered -> "已选择"
                        isMulti -> "待多选"
                        item.method == "confirm" -> "待确认"
                        !hasInlineOptions && (item.method == "input" || item.method == "editor") -> "待输入"
                        else -> "待选择"
                    }
                    val statusColor = if (isAnswered) GeekColors.NeonGreen else if (isMulti) GeekColors.TerminalCyan else GeekColors.BrandAccent

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal),
                        color = statusColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = GeekColors.TextMuted,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            // Expandable Body
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = fadeIn(tween(140)) + expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* absorb inner clicks */ },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Question text rendered below in the expanded area without truncation
                    if (displayQuestionText.isNotBlank()) {
                        Text(
                            text = displayQuestionText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = GeekColors.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                        )
                    }

                    when {
                        !hasInlineOptions && (item.method == "input" || item.method == "editor") -> {
                            InlineInputAnswer(
                                item = item,
                                enabled = !isAnswered,
                                onSubmit = { text ->
                                    localSelectedKey = text
                                    isCollapsed = true
                                    onSubmitText?.invoke(text)
                                },
                            )
                        }

                        else -> {
                            // Unified Option Rows for both single and multi select
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (option in effectiveOptions) {
                                    val isSelected = if (isMulti) {
                                        localSelectedKeys.contains(option.key) ||
                                        localSelectedKeys.contains(option.label) ||
                                        (localSelectedKey != null && (
                                            localSelectedKey == option.key ||
                                            localSelectedKey == option.label ||
                                            localSelectedKey!!.split(", ", ",").map { it.trim() }.any { it == option.key || it == option.label }
                                        ))
                                    } else {
                                        localSelectedKey == option.key ||
                                        localSelectedKey == option.label ||
                                        localSelectedKeys.contains(option.key) ||
                                        localSelectedKeys.contains(option.label)
                                    }

                                    val accentColor = if (isMulti) GeekColors.TerminalCyan else GeekColors.BrandAccent

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .pressClickEffect()
                                            .clickable(
                                                enabled = !isAnswered,
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                if (isMulti) {
                                                    // Toggle multiple selection: track uniquely by option.key
                                                    val isCurrentlySelected = localSelectedKeys.contains(option.key) || localSelectedKeys.contains(option.label)
                                                    localSelectedKeys = if (isCurrentlySelected) {
                                                        localSelectedKeys - option.key - option.label
                                                    } else {
                                                        (localSelectedKeys - option.label) + option.key
                                                    }
                                                } else {
                                                    // Single selection: select and collapse into summary bar
                                                    localSelectedKey = option.key
                                                    localSelectedKeys = setOf(option.key, option.label)
                                                    isCollapsed = true
                                                    onSelectOption(option)
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = when {
                                            isSelected -> accentColor.copy(alpha = 0.15f)
                                            isAnswered -> GeekColors.DeepCanvas.copy(alpha = 0.4f)
                                            else -> GeekColors.CardElevated
                                        },
                                        border = BorderStroke(
                                            if (isSelected) 1.2.dp else 0.8.dp,
                                            when {
                                                isSelected -> accentColor
                                                isAnswered -> GeekColors.BorderSubtle.copy(alpha = 0.4f)
                                                else -> GeekColors.BorderSubtle
                                            },
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Unified Key Badge (e.g. [1], [2], [A], [Y]/[N])
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when {
                                                    isSelected -> accentColor
                                                    isAnswered -> GeekColors.CardSurface
                                                    else -> accentColor.copy(alpha = 0.15f)
                                                },
                                            ) {
                                                Text(
                                                    text = option.key.uppercase(),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                    color = if (isSelected) (if (isMulti) Color.Black else Color.White) else accentColor,
                                                )
                                            }

                                            Spacer(Modifier.width(10.dp))

                                            // Label & optional description
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = option.label,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = when {
                                                        isSelected -> accentColor
                                                        isAnswered -> GeekColors.TextMuted
                                                        else -> GeekColors.TextPrimary
                                                    },
                                                )
                                                if (!option.description.isNullOrBlank()) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = option.description,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GeekColors.TextMuted,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            // Distinct check indicator box on the right
                                            Surface(
                                                modifier = Modifier.size(20.dp),
                                                shape = if (isMulti) RoundedCornerShape(5.dp) else CircleShape,
                                                color = if (isSelected) accentColor else Color.Transparent,
                                                border = BorderStroke(
                                                    width = if (isSelected) 0.dp else 1.2.dp,
                                                    color = if (isSelected) accentColor else (if (isAnswered) GeekColors.BorderSubtle.copy(alpha = 0.5f) else GeekColors.TextMuted.copy(alpha = 0.6f)),
                                                ),
                                            ) {
                                                if (isSelected) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "已勾选",
                                                            tint = if (isMulti) Color.Black else Color.White,
                                                            modifier = Modifier.size(13.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Optional write-in / custom input field
                            if (!isAnswered && (showCustomInput || item.allowCustomInput)) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = localCustomText,
                                    onValueChange = { localCustomText = it },
                                    placeholder = {
                                        Text(
                                            text = if (item.placeholder != null && !item.placeholder.contains("comma-separated") && !item.placeholder.contains("numbers")) item.placeholder else "补充自定义说明 (可选)...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GeekColors.TextMuted,
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GeekColors.TerminalCyan,
                                        unfocusedBorderColor = GeekColors.BorderSubtle,
                                        cursorColor = GeekColors.TerminalCyan,
                                        focusedTextColor = GeekColors.TextPrimary,
                                        unfocusedTextColor = GeekColors.TextPrimary,
                                    ),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    )
                            }

                            // Multi-select Bottom Action Toolbar
                            if (isMulti) {
                                if (!isAnswered) {
                                    val selectedOptions = effectiveOptions.filter { opt ->
                                        localSelectedKeys.contains(opt.key) || localSelectedKeys.contains(opt.label) ||
                                        (localSelectedKey != null && localSelectedKey!!.split(", ", ",").map { it.trim() }.any { it == opt.key || it == opt.label })
                                    }
                                    val selectedCount = selectedOptions.size
                                    val canSubmit = selectedCount > 0 || localCustomText.isNotBlank()

                                    Spacer(Modifier.height(12.dp))

                                    val btnBg = if (canSubmit) GeekColors.TerminalCyan.copy(alpha = 0.14f) else GeekColors.CardElevated
                                    val btnBorder = if (canSubmit) GeekColors.TerminalCyan.copy(alpha = 0.6f) else GeekColors.BorderSubtle
                                    val btnContentColor = if (canSubmit) GeekColors.TerminalCyan else GeekColors.TextMuted

                                    // Submit Pill with light theme harmony
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .pressClickEffect()
                                            .clickable(
                                                enabled = canSubmit,
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                if (canSubmit) {
                                                    val selected = selectedOptions
                                                    val custom = localCustomText.trim().takeIf { it.isNotEmpty() }
                                                    isCollapsed = true
                                                    if (onSelectMultiple != null) {
                                                        onSelectMultiple.invoke(selected, custom)
                                                    } else {
                                                        val keysText = selected.joinToString(",") { it.key }
                                                        val fullText = if (custom.isNullOrBlank()) keysText else "$keysText,$custom"
                                                        onSubmitText?.invoke(fullText.trim())
                                                    }
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = btnBg,
                                        border = BorderStroke(1.dp, btnBorder),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            if (canSubmit) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = btnContentColor,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = if (selectedCount > 0) "确认提交 (已选 $selectedCount 项)" else "确认提交",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = btnContentColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            } else {
                                                Text(
                                                    text = "点击上方选项直接勾选 (支持多选)",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    color = btnContentColor,
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        val allSelected = effectiveOptions.isNotEmpty() && selectedCount == effectiveOptions.size
                                        TextButton(
                                            onClick = {
                                                localSelectedKeys = if (allSelected) emptySet() else effectiveOptions.map { it.key }.toSet()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = if (allSelected) "取消全选" else "全选所有选项",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = GeekColors.TextSecondary,
                                            )
                                        }

                                        if (!showCustomInput && !item.allowCustomInput) {
                                            TextButton(
                                                onClick = { showCustomInput = true },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "+ 附言说明",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                    color = GeekColors.TextMuted,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    if (localCustomText.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "附言: $localCustomText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GeekColors.TextMuted,
                                            fontStyle = FontStyle.Italic,
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
}


/** Inline free-text answer for input/editor dialogs (HANDOFF §8.2). */
@Composable
private fun InlineInputAnswer(
    item: QuestionOptionBlock,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(
                    text = item.placeholder ?: "在此输入...",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeekColors.TextMuted,
                )
            },
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GeekColors.TerminalCyan,
                unfocusedBorderColor = GeekColors.BorderSubtle,
                cursorColor = GeekColors.TerminalCyan,
                focusedTextColor = GeekColors.TextPrimary,
                unfocusedTextColor = GeekColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.width(8.dp))

        val canSend = enabled && text.isNotBlank()
        Surface(
            shape = CircleShape,
            color = if (canSend) GeekColors.TerminalCyan else GeekColors.CardElevated,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape),
            onClick = { if (canSend) onSubmit(text.trim()) },
            enabled = canSend,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "↑",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (canSend) Color.Black else GeekColors.TextMuted,
                )
            }
        }
    }
}

