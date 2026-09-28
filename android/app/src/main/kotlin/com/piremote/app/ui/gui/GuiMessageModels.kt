package com.piremote.app.ui.gui

import java.util.UUID

/**
 * Common interface for items displayed in the GUI stream.
 */
sealed interface GuiMessageItem {
    val id: String
    val timestamp: Long
}

/**
 * One row of the quick-navigation outline (☰ in the top bar): a stable item id
 * to scroll to plus the two-column display (type tag + preview). A user message
 * starts a new turn — the dialog draws a divider before it and indents the
 * turn's thinking, tools and reply under it.
 */
data class GuiChatOutlineItem(
    val id: String,
    val tag: String,
    val label: String,
    val turnStart: Boolean = false,
    val entryId: String? = null,
)

/**
 * User prompt message.
 */
data class UserMessage(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val model: String? = null,
    val provider: String? = null,
    val sendType: String? = null,
    val entryId: String? = null,
) : GuiMessageItem

/**
 * Chain of thought / reasoning block from the model.
 */
data class ThinkingBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val content: String,
    val durationSeconds: Float = 0f,
    val isFinished: Boolean = true,
) : GuiMessageItem

/**
 * Execution status for tool calls.
 */
enum class ToolStatus {
    RUNNING,
    SUCCESS,
    ERROR
}

/**
 * Tool call representation (e.g. bash execution, file edit, file read).
 */
data class ToolCallBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val toolName: String,
    val summary: String,
    val arguments: String,
    val output: String? = null,
    val status: ToolStatus = ToolStatus.SUCCESS,
) : GuiMessageItem

/**
 * Token usage stats for an assistant response turn.
 */
data class MessageUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val totalTokens: Int = 0,
) {
    /**
     * Total prompt context tokens = uncached new input tokens + cached read tokens.
     */
    val totalPromptTokens: Int
        get() = inputTokens + cacheReadTokens

    /**
     * Effective input tokens (backward compatibility alias).
     */
    val effectiveInputTokens: Int
        get() = totalPromptTokens

    /**
     * Cache hit percentage as float (e.g. 49.6f).
     * Formula matches Pi Agent CLI: cacheReadTokens / (inputTokens + cacheReadTokens) * 100.
     */
    val cacheHitPercentFloat: Float
        get() = if (totalPromptTokens > 0 && cacheReadTokens > 0) {
            ((cacheReadTokens.toDouble() / totalPromptTokens) * 100.0).toFloat().coerceIn(0f, 100f)
        } else 0f

    /**
     * Formatted cache hit percent string (e.g. "49.6%", "99.0%", "100%").
     */
    val cacheHitPercentText: String
        get() = when {
            cacheHitPercentFloat <= 0f -> ""
            cacheHitPercentFloat >= 99.95f -> "100%"
            else -> String.format(java.util.Locale.US, "%.1f%%", cacheHitPercentFloat)
        }

    /**
     * Cache hit ratio percentage integer (0 - 100).
     */
    val cacheHitPercent: Int
        get() = kotlin.math.round(cacheHitPercentFloat).toInt().coerceIn(0, 100)

    val hasUsage: Boolean get() = inputTokens > 0 || outputTokens > 0 || cacheReadTokens > 0
}

/**
 * Assistant textual reply in Markdown.
 */
data class AssistantResponse(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val usage: MessageUsage? = null,
    val isError: Boolean = false,
    val isAborted: Boolean = false,
) : GuiMessageItem

/**
 * Prominent error card displayed when model invocation, gateway, or agent encounters an error
 * (e.g. 429 rate limit, gateway failure, network timeout).
 */
data class ErrorMessageBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val error: String,
    val details: String? = null,
) : GuiMessageItem

/**
 * System state / information badge.
 */
data class SystemStatusMessage(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val text: String,
) : GuiMessageItem

/**
 * An individual selectable option presented to the user.
 */
data class InteractiveOption(
    val key: String,
    val label: String,
    val description: String? = null,
)

/**
 * Interactive question / choices card, matching pi-web's ExtensionDialog / select / confirm concept.
 *
 * Two sources:
 * - RPC sessions: built from pi's `extension_ui_request` events. [requestId] is
 *   set and answers go back over the wire as `extension_ui_response`.
 * - PTY sessions (legacy transcript parser): [requestId] is null and a tap
 *   sends the option key as terminal input.
 *
 * [method] selects the render shape: "select" shows option buttons, "confirm"
 * shows 是/否, "input"/"editor" show an inline free-text field ([placeholder]).
 */
data class QuestionOptionBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val question: String,
    val options: List<InteractiveOption>,
    val selectedKey: String? = null,
    val selectedKeys: Set<String> = emptySet(),
    val isAnswered: Boolean = false,
    val requestId: String? = null,
    val method: String = "select",
    val placeholder: String? = null,
    val isMultiSelect: Boolean = method in listOf("multi_select", "multiselect"),
    val allowCustomInput: Boolean = false,
    val customInput: String? = null,
) : GuiMessageItem

