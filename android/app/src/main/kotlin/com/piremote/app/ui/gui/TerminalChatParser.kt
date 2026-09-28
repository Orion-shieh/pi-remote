package com.piremote.app.ui.gui

import java.util.UUID

/**
 * Intelligent parser that converts terminal transcript text (from Pi Agent, PowerShell, or shells)
 * into high-level structured GUI chat cards conforming to the pi-web partition architecture:
 *
 * 1. User Message Partition (UserMessage)
 * 2. Thinking Accordion Partition (ThinkingBlock, collapsible, with duration & breathing animation)
 * 3. Tool Execution Partition (ToolCallBlock, with tool name, arguments, status, and terminal drawer)
 * 4. Interactive Option Partition (QuestionOptionBlock, with clickable choices matching pi-web ExtensionDialog)
 * 5. Assistant Response Partition (AssistantResponse, rendered with Markdown & code blocks)
 * 6. System Status Partition (SystemStatusMessage, session info, model, and capabilities)
 */
object TerminalChatParser {

    private val ANSI_REGEX = Regex(
        """\u001b\[[0-9;?><=]*[a-zA-Z@`~]|\u001b\([a-zA-Z0-9]|\u001b\][^\u0007\u001b]*(?:\u0007|\u001b\\)|\u001b[=><]"""
    )

    // Known tool names in Pi Agent & standard shell tools
    private val KNOWN_PI_TOOLS = setOf(
        "bash", "sh", "powershell", "pwsh", "cmd",
        "read", "read_file", "view_file",
        "edit", "edit_file", "write", "write_file",
        "grep", "grep_search", "find", "find_files", "ls", "list_dir",
        "ask_user_question", "browser", "web_search", "fetch",
        "node", "python", "git", "task", "subagent"
    )

    // Divider lines like "───────────────" or "═══════════════"
    private val DIVIDER_REGEX = Regex("""^[─━═\-_~]{4,}$""")

    // Detect Pi Agent bottom status bar lines e.g. "0.0%/1.0M (auto) ... deepseek/deepseek-v4-flash • medium"
    private val STATUS_BAR_REGEX = Regex("""(\d+(\.\d+)?%/\d+(\.\d+)?[KM]|\bdeepseek/|\bclaude-|\bgpt-|\bauto\b|↑\d+M|↓\d+k)""")

    // PowerShell prompt pattern: PS D:\... >
    private val PWSH_PROMPT_REGEX = Regex("""^PS\s+[A-Za-z]:\\[^>]*>\s*""")

    // User prompt indicators in CLI
    private val USER_PROMPT_PREFIXES = listOf("❯ ", ">>> ")

    // Confirmation pattern e.g. "Do you want to proceed? (Y/n)" or "? Allow execution? [y/N]"
    private val CONFIRMATION_REGEX = Regex(
        """^(?:\?|\>|\s*)\s*(.+?)\s*(?:\((?:[yY]/[nN]|[yY]es/[nN]o)\)|\[(?:[yY]/[nN]|[yY]es/[nN]o)\])\s*:?\s*$"""
    )

    // Option line pattern e.g. "1) Option text", "1. Option text", "[1] Option text", "❯ 1. Option text"
    private val OPTION_LINE_REGEX = Regex(
        """^[❯>•\s]*\[?(\d+|[a-zA-Z])[\).\]]\s+(.+)$"""
    )

    private enum class SectionType {
        NONE,
        SYSTEM,
        USER,
        THINKING,
        TOOL,
        ASSISTANT,
        ERROR,
    }

    /**
     * Replaces \r (carriage return) line overwrites by keeping the latest rewritten line,
     * simulating terminal cursor behavior to eliminate spinner/progress animation artifacts.
     */
    fun collapseCarriageReturns(text: String): String {
        return text.lines().joinToString("\n") { line ->
            if (!line.contains('\r')) {
                line
            } else {
                val parts = line.split('\r').map { it.trim() }.filter { it.isNotEmpty() }
                parts.lastOrNull() ?: ""
            }
        }
    }

    /**
     * Strips ANSI escape codes and control remnants.
     */
    fun cleanAnsi(text: String): String {
        val collapsed = collapseCarriageReturns(text)
        return ANSI_REGEX.replace(collapsed, "")
            .replace("\u2588", "") // Block cursor artifact
            .replace("\u2026", "...")
    }

    /**
     * Parses the terminal transcript into structured GUI cards with strict pi-web partition fidelity.
     */
    fun parseTranscript(
        rawTranscript: String,
        pendingUserMessages: List<String> = emptyList(),
    ): List<GuiMessageItem> {
        val clean = cleanAnsi(rawTranscript).trim()

        // Extract system metadata (e.g. model name, Pi version)
        var detectedModel = ""
        var detectedVersion = ""
        if (clean.contains("deepseek/", ignoreCase = true)) {
            val match = Regex("""deepseek/[a-zA-Z0-9.\-_]+""").find(clean)
            if (match != null) detectedModel = match.value
        }
        if (clean.contains("pi v", ignoreCase = true)) {
            val match = Regex("""pi\s+v([0-9.]+)""").find(clean)
            if (match != null) detectedVersion = "v" + match.groupValues[1]
        }

        val lines = clean.lines()
        val parsedItems = mutableListOf<GuiMessageItem>()

        var currentType = SectionType.NONE
        val currentBuffer = StringBuilder()
        var currentToolName = ""
        var currentToolSummary = ""
        var currentToolArgs = ""
        val currentToolOutput = StringBuilder()
        var currentThinkingDuration = 0f
        var turnIndex = 0
        var isBtwActive = false

        fun flushSection(isFinished: Boolean = true) {
            when (currentType) {
                SectionType.SYSTEM -> {
                    val text = currentBuffer.toString().trim()
                    if (text.isNotEmpty() && !isPureNoise(text)) {
                        parsedItems.add(
                            SystemStatusMessage(
                                id = "sys_${turnIndex++}",
                                text = text,
                            ),
                        )
                    }
                }

                SectionType.USER -> {
                    val text = currentBuffer.toString().trim()
                    if (text.isNotEmpty() && !isPureNoise(text)) {
                        parsedItems.add(
                            UserMessage(
                                id = "user_${turnIndex++}",
                                text = text,
                            ),
                        )
                    }
                }

                SectionType.THINKING -> {
                    val text = currentBuffer.toString().trim()
                    if (text.isNotEmpty() && !isPureNoise(text)) {
                        parsedItems.add(
                            ThinkingBlock(
                                id = "think_${turnIndex++}",
                                content = text,
                                durationSeconds = currentThinkingDuration,
                                isFinished = isFinished,
                            ),
                        )
                    }
                }

                SectionType.TOOL -> {
                    val out = currentToolOutput.toString().trim()
                    val hasError = out.contains("error", ignoreCase = true) ||
                                   out.contains("failed", ignoreCase = true) ||
                                   out.contains("exception", ignoreCase = true)
                    parsedItems.add(
                        ToolCallBlock(
                            id = "tool_${turnIndex++}",
                            toolName = currentToolName.ifEmpty { "bash" },
                            summary = currentToolSummary.ifEmpty { currentToolName },
                            arguments = currentToolArgs.ifEmpty { currentToolSummary },
                            output = out.ifEmpty { null },
                            status = when {
                                hasError -> ToolStatus.ERROR
                                !isFinished && out.isEmpty() -> ToolStatus.RUNNING
                                else -> ToolStatus.SUCCESS
                            },
                        ),
                    )
                }

                SectionType.ASSISTANT -> {
                    val text = currentBuffer.toString().trim()
                    val cleanText = sanitizeAssistantText(text)
                    if (cleanText.isNotEmpty() && !isPureNoise(cleanText)) {
                        if (isExplicitErrorText(cleanText)) {
                            parsedItems.add(
                                ErrorMessageBlock(
                                    id = "err_${turnIndex++}",
                                    error = cleanText,
                                ),
                            )
                        } else {
                            val lower = cleanText.lowercase()
                            val isAbort = lower.contains("aborted") || lower.contains("canceled") || lower.contains("cancelled") || lower.contains("已停止生成") || cleanText.contains("中断")
                            val actualText = if (isAbort) {
                                cleanText.lines().filterNot { line ->
                                    val l = line.trim().lowercase()
                                    l == "request aborted" || l == "aborted" || l == "canceled" || l == "cancelled" || l.contains("已停止生成")
                                }.joinToString("\n").trim()
                            } else {
                                cleanText
                            }
                            val lastAssistant = parsedItems.lastOrNull { it is AssistantResponse } as? AssistantResponse
                            if (actualText.isNotEmpty() && (lastAssistant == null || lastAssistant.text != actualText)) {
                                parsedItems.add(
                                    AssistantResponse(
                                        id = "assistant_${turnIndex++}",
                                        text = actualText,
                                    ),
                                )
                            }
                            if (isAbort) {
                                val lastNotice = parsedItems.lastOrNull() as? SystemStatusMessage
                                if (lastNotice == null || lastNotice.text != "已停止生成") {
                                    parsedItems.add(
                                        SystemStatusMessage(
                                            id = "abort_${turnIndex++}",
                                            text = "已停止生成",
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                SectionType.ERROR -> {
                    val text = currentBuffer.toString().trim()
                    if (text.isNotEmpty()) {
                        parsedItems.add(
                            ErrorMessageBlock(
                                id = "err_${turnIndex++}",
                                error = text,
                            ),
                        )
                    }
                }

                SectionType.NONE -> Unit
            }

            currentBuffer.clear()
            currentToolOutput.clear()
            currentToolName = ""
            currentToolSummary = ""
            currentToolArgs = ""
            currentThinkingDuration = 0f
            currentType = SectionType.NONE
        }

        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            // Filter out decorative dividers
            if (DIVIDER_REGEX.matches(trimmed)) {
                if (currentType == SectionType.TOOL) {
                    flushSection(isFinished = true)
                }
                i++
                continue
            }

            // Filter out bottom status lines or curses status bars
            if (STATUS_BAR_REGEX.containsMatchIn(trimmed) && trimmed.length < 120 &&
                (trimmed.contains("%/") || trimmed.contains("•") || trimmed.startsWith("D:\\"))) {
                i++
                continue
            }

            // Filter out hotkey hints
            if (trimmed.contains("escape interrupt", ignoreCase = true) ||
                trimmed.contains("ctrl+c/ctrl+d clear/exit", ignoreCase = true) ||
                trimmed.contains("Press ctrl+o to show full startup", ignoreCase = true) ||
                trimmed.contains("Package updates are available", ignoreCase = true)) {
                i++
                continue
            }

            // 1. Startup / System banner
            if (trimmed.startsWith("pi v", ignoreCase = true) ||
                trimmed.startsWith("Connected", ignoreCase = true) ||
                trimmed.startsWith("Resumed session", ignoreCase = true)) {
                flushSection()
                currentType = SectionType.SYSTEM
                val sysText = if (detectedModel.isNotEmpty()) {
                    "会话已连接 · Pi Agent ${detectedVersion.ifEmpty { "v0.85.1" }} ($detectedModel)"
                } else {
                    trimmed
                }
                currentBuffer.appendLine(sysText)
                i++
                continue
            }

            // 2. Interactive Confirmation Prompt e.g. "? Continue? (Y/n)" or "Allow? [y/N]"
            val confirmMatch = CONFIRMATION_REGEX.find(trimmed)
            if (confirmMatch != null) {
                flushSection()
                val question = confirmMatch.groupValues[1].removePrefix("?").trim()
                parsedItems.add(
                    QuestionOptionBlock(
                        id = "opt_${turnIndex++}",
                        question = question,
                        options = listOf(
                            InteractiveOption(key = "y", label = "Y (确定继续)"),
                            InteractiveOption(key = "n", label = "N (取消操作)"),
                        ),
                    )
                )
                i++
                continue
            }

            // 3. Interactive Numbered Option Menu e.g.
            // "? Select an option:" or "[多选测试]请选择..." followed by options
            val headerMatches = isQuestionHeader(trimmed)
            val inlineOptsInHeader = if (headerMatches) extractOptionsFromText(trimmed) else emptyList()

            if (headerMatches || inlineOptsInHeader.size >= 2) {
                var questionTitle = trimmed.removePrefix("?").removePrefix("Question:").trim()
                val options = mutableListOf<InteractiveOption>()
                var questionPromptHint: String? = null

                if (inlineOptsInHeader.size >= 2) {
                    val firstOptMatch = OPTION_TOKEN_REGEX.find(trimmed)
                    if (firstOptMatch != null && firstOptMatch.range.first > 0) {
                        questionTitle = trimmed.substring(0, firstOptMatch.range.first).trim()
                    }
                    options.addAll(inlineOptsInHeader)
                }

                var nextIdx = i + 1
                while (nextIdx < lines.size) {
                    val nextLine = lines[nextIdx].trim()
                    if (nextLine.isEmpty()) {
                        if (options.isNotEmpty()) {
                            nextIdx++
                            break
                        } else {
                            nextIdx++
                            continue
                        }
                    }

                    if (isPromptInstruction(nextLine)) {
                        questionPromptHint = nextLine
                        nextIdx++
                        break
                    }

                    val lineOptions = extractOptionsFromText(nextLine)
                    if (lineOptions.isNotEmpty()) {
                        options.addAll(lineOptions)
                        nextIdx++
                    } else if (options.isNotEmpty()) {
                        break
                    } else {
                        if (nextIdx - i > 3) break
                        nextIdx++
                    }
                }

                if (options.size >= 2) {
                    flushSection()
                    val isMulti = detectIsMulti(questionTitle, questionPromptHint)
                    parsedItems.add(
                        QuestionOptionBlock(
                            id = "opt_${turnIndex++}",
                            question = questionTitle.ifEmpty { "请选择操作方案：" },
                            options = options,
                            method = if (isMulti) "multi_select" else "select",
                            placeholder = if (isMulti) null else questionPromptHint,
                            isMultiSelect = isMulti,
                        )
                    )
                    i = nextIdx
                    continue
                }
            }

            // 4. User Prompts (❯ ..., or matching pending user messages)
            // Note: Never treat '?' or Markdown blockquotes '>' inside responses as user prompts!
            val isExplicitUserPrefix = USER_PROMPT_PREFIXES.any { trimmed.startsWith(it) && !isOptionLine(trimmed) }
            val matchesPending = pendingUserMessages.any { it.isNotBlank() && trimmed.contains(it) }

            if (isExplicitUserPrefix || (matchesPending && currentType != SectionType.USER)) {
                flushSection()
                currentType = SectionType.USER
                val promptText = if (isExplicitUserPrefix) {
                    trimmed.removePrefix("❯ ").removePrefix(">>> ").trim()
                } else {
                    trimmed
                }

                // If preceding item was a QuestionOptionBlock, user answered it; finish and dismiss ("选完就结束了")
                if (parsedItems.lastOrNull() is QuestionOptionBlock) {
                    parsedItems.removeAt(parsedItems.lastIndex)
                }

                currentBuffer.append(promptText)
                i++
                continue
            }

            // 5. Thinking blocks (<thinking>, Thinking..., 思考中...)
            if (isThinkingStart(trimmed)) {
                flushSection()
                currentType = SectionType.THINKING
                // Extract possible duration: e.g. "Thinking (3.8s)..."
                val durMatch = Regex("""\(([0-9.]+)s\)""").find(trimmed)
                if (durMatch != null) {
                    currentThinkingDuration = durMatch.groupValues[1].toFloatOrNull() ?: 0f
                }
                val thinkLine = cleanThinkingPrefix(trimmed)
                if (thinkLine.isNotEmpty()) {
                    currentBuffer.appendLine(thinkLine)
                }
                i++
                continue
            }

            if (currentType == SectionType.THINKING) {
                if (trimmed.contains("</thinking>", ignoreCase = true) ||
                    trimmed.startsWith("Finished thinking", ignoreCase = true)) {
                    val beforeEnd = trimmed.substringBefore("</thinking>").trim()
                    if (beforeEnd.isNotEmpty()) currentBuffer.appendLine(beforeEnd)
                    flushSection(isFinished = true)
                    i++
                    continue
                }
                if (isExplicitErrorText(trimmed) || isToolStart(trimmed) || isHeadingOrMarkdown(trimmed) || isExplicitUserPrefix) {
                    flushSection(isFinished = true)
                    continue // Re-process line in loop
                } else {
                    currentBuffer.appendLine(rawLine)
                    i++
                    continue
                }
            }

            // 5.5 BTW side conversation box (from pi-btw)
            if (isBtwBoxHeader(trimmed)) {
                flushSection()
                currentType = SectionType.ASSISTANT
                isBtwActive = true
                i++
                continue
            }

            if (isBtwActive) {
                val isEnd = trimmed.startsWith("└") || trimmed.startsWith("╰") ||
                            trimmed.startsWith("╚") || trimmed.startsWith("┗") ||
                            trimmed.startsWith("━") || trimmed.startsWith("═") ||
                            trimmed.startsWith("+--") || trimmed.startsWith("---") ||
                            trimmed.startsWith("===") || trimmed.startsWith("]") ||
                            trimmed.startsWith("】")
                if (isEnd) {
                    flushSection(isFinished = true)
                    isBtwActive = false
                    i++
                    continue
                }
                val cleanLine = cleanAnsi(rawLine).trim()
                    .removePrefix("│").removePrefix("|").removePrefix("┃").removePrefix("║")
                    .removeSuffix("│").removeSuffix("|").removeSuffix("┃").removeSuffix("║").trim()
                if (cleanLine.isNotEmpty() && !isBtwBoxHeader(cleanLine)) {
                    currentBuffer.appendLine(cleanLine)
                }
                i++
                continue
            }

            // 6. Tool calls (● bash, ● edit_file, Tool: ..., $ ...)
            if (isToolStart(trimmed)) {
                flushSection()
                currentType = SectionType.TOOL
                val (tName, tSummary, tArgs) = parseToolStart(trimmed)
                currentToolName = tName
                currentToolSummary = tSummary
                currentToolArgs = tArgs
                i++
                continue
            }

            if (currentType == SectionType.TOOL) {
                val isBoxEnd = trimmed.startsWith("└─") || trimmed.startsWith("└──") || trimmed.startsWith("└") ||
                               trimmed.startsWith("━") || trimmed.startsWith("═")
                val isDoneIndicator = trimmed.startsWith("✔") || trimmed.startsWith("✓") ||
                                      trimmed.startsWith("✖") || trimmed.startsWith("✗") ||
                                      trimmed.startsWith("[Process completed", ignoreCase = true) ||
                                      trimmed.startsWith("Process exited", ignoreCase = true)

                if (isBoxEnd || isDoneIndicator) {
                    flushSection(isFinished = true)
                    i++
                    continue
                }

                // If line starts a new section, finish the tool call
                if (isExplicitUserPrefix ||
                    isToolStart(trimmed) ||
                    isThinkingStart(trimmed) ||
                    isAssistantStart(trimmed)) {
                    flushSection(isFinished = true)
                    continue
                } else {
                    val cleanedLine = rawLine.trimStart().removePrefix("│ ").removePrefix("│")
                    currentToolOutput.appendLine(cleanedLine)
                    i++
                    continue
                }
            }

            // Check for explicit error lines (e.g. Error: Gateway request failed / 429 status code)
            if (isExplicitErrorText(trimmed)) {
                flushSection(isFinished = true)
                currentType = SectionType.ERROR
                currentBuffer.appendLine(trimmed)
                i++
                continue
            }

            // 7. Assistant Response (default for content lines)
            if (isNoiseLine(trimmed)) {
                i++
                continue
            }

            if (trimmed.isEmpty() && currentType != SectionType.ASSISTANT) {
                i++
                continue
            }

            if (currentType != SectionType.ASSISTANT) {
                flushSection()
                currentType = SectionType.ASSISTANT
            }
            currentBuffer.appendLine(rawLine)
            i++
        }

        // Flush any trailing section
        flushSection(isFinished = false)

        // -------------------------------------------------------------
        // PARTITION INTEGRITY GUARANTEE (确保原本分区绝不消失)
        // -------------------------------------------------------------
        val hasRealTurns = parsedItems.any { it is UserMessage || it is ToolCallBlock || it is QuestionOptionBlock || it is ErrorMessageBlock }

        if (!hasRealTurns) {
            // When transcript does not contain parsed user turns yet, preserve the rich 5-partition layout!
            if (pendingUserMessages.isEmpty()) {
                val initial = GuiSampleData.createInitialConversation()
                if (detectedModel.isNotEmpty() || detectedVersion.isNotEmpty()) {
                    return initial.map { item ->
                        if (item is SystemStatusMessage) {
                            val modelSuffix = if (detectedModel.isNotEmpty()) " ($detectedModel)" else " (deepseek-v4-flash)"
                            val verSuffix = if (detectedVersion.isNotEmpty()) " $detectedVersion" else " v0.85.1"
                            SystemStatusMessage(id = item.id, timestamp = item.timestamp, text = "会话已连接 · Pi Agent$verSuffix$modelSuffix")
                        } else {
                            item
                        }
                    }
                }
                return initial
            } else {
                // User has sent new messages from the GUI input bar!
                val result = mutableListOf<GuiMessageItem>()
                result.add(
                    SystemStatusMessage(
                        id = "status_active",
                        text = "会话已连接 · Pi Agent ${detectedVersion.ifEmpty { "v0.85.1" }}${if (detectedModel.isNotEmpty()) " ($detectedModel)" else ""}",
                    ),
                )

                for ((index, pending) in pendingUserMessages.withIndex()) {
                    val isLast = index == pendingUserMessages.lastIndex
                    result.add(UserMessage(id = "user_pending_$index", text = pending))

                    if (isLast) {
                        val toolInParsed = parsedItems.filterIsInstance<ToolCallBlock>()
                        val asstInParsed = parsedItems.filterIsInstance<AssistantResponse>()
                        val thinkInParsed = parsedItems.filterIsInstance<ThinkingBlock>()
                        val optionsInParsed = parsedItems.filterIsInstance<QuestionOptionBlock>()

                        if (thinkInParsed.isNotEmpty()) {
                            result.addAll(thinkInParsed)
                        } else {
                            result.add(
                                ThinkingBlock(
                                    id = "think_active_$index",
                                    content = "正在分析指令并规划执行方案...",
                                    durationSeconds = 1.2f,
                                    isFinished = asstInParsed.isNotEmpty() || toolInParsed.isNotEmpty() || optionsInParsed.isNotEmpty(),
                                ),
                            )
                        }

                        if (toolInParsed.isNotEmpty()) {
                            result.addAll(toolInParsed)
                        }

                        if (asstInParsed.isNotEmpty()) {
                            result.addAll(asstInParsed)
                        }

                        if (optionsInParsed.isNotEmpty()) {
                            result.addAll(optionsInParsed)
                        }
                    }
                }
                return result
            }
        }

        // When transcript has real turns, ensure pending user messages are included at the end
        for (pending in pendingUserMessages) {
            if (parsedItems.none { it is UserMessage && it.text.trim() == pending.trim() }) {
                parsedItems.add(UserMessage(id = "user_pending_${UUID.randomUUID()}", text = pending))
                if (parsedItems.lastOrNull() !is AssistantResponse && parsedItems.lastOrNull() !is QuestionOptionBlock) {
                    parsedItems.add(
                        ThinkingBlock(
                            id = "think_pending_${UUID.randomUUID()}",
                            content = "正在分析执行指令...",
                            isFinished = false,
                        ),
                    )
                }
            }
        }

        // "选完就结束了": only keep QuestionOptionBlock if it is the active pending question at the very end
        val lastItem = parsedItems.lastOrNull()
        if (lastItem !is QuestionOptionBlock) {
            parsedItems.removeAll { it is QuestionOptionBlock }
        } else {
            parsedItems.removeAll { it is QuestionOptionBlock && it != lastItem }
        }

        return parsedItems
    }

    // Token regex supporting multi-option single lines, no-space boundaries, OCR typos, and various bullet formats
    private val OPTION_TOKEN_REGEX = Regex(
        """(?:^|[\s。！？；!?;,，、\n\ro○•]+)[\[(【（]?(\d{1,2}|[a-zA-Z])[\]\).、:：\-】）]\s*([^\d\n\r].+?)(?=(?:[\s。！？；!?;,，、\n\ro○•]+)[\[(【（]?(?:\d{1,2}|[a-zA-Z])[\]\).、:：\-】）]|[\s。！？；!?;,，、\-]*(?:Enter\b|Input\b|请输入|输入|all that apply|comma-separated|type a custom|type acustom)|$)"""
    )

    fun sanitizeOptionLabel(raw: String, key: String): String {
        val cutIdx = Regex("""(?i)[\s。！？；!?;,，、\-]*(?:Enter\b|Input\b|请输入|输入|all that apply|comma-separated|type a custom|type acustom)""").find(raw)
        val trimmed = if (cutIdx != null && cutIdx.range.first > 0) {
            raw.substring(0, cutIdx.range.first).trim()
        } else {
            raw.trim()
        }
        return trimmed
            .trimEnd('。', '；', '!', '！', ',', '，', '.', ':', '：', 'o', '○', '•', '…')
            .removePrefix("$key)")
            .removePrefix("$key.")
            .removePrefix("$key、")
            .removePrefix("$key ")
            .removePrefix("—")
            .removePrefix("-")
            .trim()
    }

    fun isPromptInstruction(trimmed: String): Boolean {
        val lower = trimmed.lowercase()
        return lower.contains("comma-separated") ||
            lower.contains("all that apply") ||
            lower.contains("type a custom answer") ||
            lower.contains("type acustom answer") ||
            lower.contains("enter the numbers") ||
            lower.contains("enter your choice") ||
            lower.contains("numbers of all that apply") ||
            lower.contains("plain text") ||
            lower.contains("逗号分隔") ||
            lower.contains("输入编号") ||
            lower.contains("输入选项") ||
            lower.contains("输入序号") ||
            lower.contains("数字编号") ||
            trimmed.matches(Regex("""^(?:Enter|Input|请输入)[^:]*[:：]?\s*$""", RegexOption.IGNORE_CASE))
    }

    fun detectIsMulti(text: String, placeholder: String? = null): Boolean {
        val lower = text.lowercase()
        if (lower.contains("多选") || lower.contains("可多选") || lower.contains("（多选") || lower.contains("(多选")) return true
        if (lower.contains("all that apply") || lower.contains("comma-separated") || lower.contains("comma separated")) return true
        if (lower.contains("multi-select") || lower.contains("multiselect")) return true
        if (lower.contains("逗号分隔") || lower.contains("用逗号") || (lower.contains("最多") && lower.contains("项"))) return true
        if (placeholder != null && placeholder.contains(",")) return true
        return false
    }

    fun extractLineOptions(text: String): List<InteractiveOption> {
        val lines = text.lines().map { it.trim() }
        val result = mutableListOf<InteractiveOption>()
        val lineRegex = Regex(
            """^[❯>•\-\*]*\s*[\[(【（]?(\d{1,2}|[a-zA-Z])[\]\).、:：\-】）]\s+(.+)$"""
        )

        for (line in lines) {
            if (isPromptInstruction(line)) continue
            val match = lineRegex.find(line)
            if (match != null) {
                val key = match.groupValues[1].trim()
                val rawLabel = match.groupValues[2].trim()
                val cleanLabel = sanitizeOptionLabel(rawLabel, key)
                if (cleanLabel.isNotEmpty()) {
                    result.add(InteractiveOption(key = key, label = cleanLabel))
                }
            }
        }
        return result
    }

    fun extractInlineOptions(text: String): List<InteractiveOption> {
        val textWithoutInstructions = text.lines()
            .filterNot { isPromptInstruction(it) }
            .joinToString("\n")

        val list = mutableListOf<InteractiveOption>()
        val matches = OPTION_TOKEN_REGEX.findAll(textWithoutInstructions).toList()
        for (m in matches) {
            val key = m.groupValues[1].trim()
            val rawLabel = m.groupValues[2].trim()
            val cleanLabel = sanitizeOptionLabel(rawLabel, key)
            if (key.isNotEmpty() && cleanLabel.isNotEmpty()) {
                list.add(InteractiveOption(key = key, label = cleanLabel))
            }
        }
        return list
    }

    fun extractOptionsFromText(text: String): List<InteractiveOption> {
        val lineOpts = extractLineOptions(text)
        if (lineOpts.size >= 2) return lineOpts
        return extractInlineOptions(text)
    }

    fun extractTitleOnly(text: String): String {
        val clean = text.trim()
        val lines = clean.lines()
        val lineRegex = Regex("""^[❯>•\-\*]*\s*[\[(【（]?(\d{1,2}|[a-zA-Z])[\]\).、:：\-】）]\s+(.+)$""")
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (lineRegex.matches(line)) {
                val titlePart = lines.subList(0, i).joinToString("\n").trim()
                if (titlePart.isNotEmpty()) {
                    return titlePart.trimEnd(':', '：')
                }
            }
        }

        val inline = extractOptionsFromText(clean)
        if (inline.isNotEmpty()) {
            val firstKey = inline.first().key
            val firstLabel = inline.first().label
            val patterns = listOf(
                "$firstKey.", "$firstKey)", "$firstKey、", "$firstKey-", "$firstKey:",
                "[$firstKey]", "($firstKey)", "【$firstKey】", "（$firstKey）",
                firstLabel
            )
            for (p in patterns) {
                val idx = clean.indexOf(p)
                if (idx > 0) {
                    return clean.substring(0, idx).trim().trimEnd(':', '：')
                }
            }
        }
        return clean
    }

    private fun isQuestionHeader(trimmed: String): Boolean {
        if (trimmed.startsWith("? ") || trimmed.startsWith("?")) return true
        if (trimmed.startsWith("Question:", ignoreCase = true)) return true
        if (trimmed.startsWith("[多选") || trimmed.startsWith("[单选") || trimmed.startsWith("【多选") || trimmed.startsWith("【单选")) return true
        if (trimmed.contains("请选择") || trimmed.contains("可多选") || trimmed.contains("(多选") || trimmed.contains("（多选") ||
            (trimmed.contains("最多") && trimmed.contains("项"))) return true
        if (trimmed.contains("all that apply", ignoreCase = true) || trimmed.contains("multi-select", ignoreCase = true)) return true
        if ((trimmed.endsWith(":") || trimmed.endsWith("：") || trimmed.endsWith("?") || trimmed.endsWith("？")) &&
            (trimmed.contains("Select", ignoreCase = true) || trimmed.contains("Choose", ignoreCase = true) ||
             trimmed.contains("Which", ignoreCase = true) || trimmed.contains("Option", ignoreCase = true) ||
             trimmed.contains("选择") || trimmed.contains("方案"))) {
            return true
        }
        return false
    }

    private fun isOptionLine(trimmed: String): Boolean {
        return OPTION_LINE_REGEX.matches(trimmed) || extractOptionsFromText(trimmed).isNotEmpty()
    }

    private fun isThinkingStart(trimmed: String): Boolean {
        return trimmed.startsWith("<thinking>", ignoreCase = true) ||
               trimmed.startsWith("Thinking...", ignoreCase = true) ||
               trimmed.startsWith("Thinking:", ignoreCase = true) ||
               trimmed.startsWith("Thinking (", ignoreCase = true) ||
               trimmed.startsWith("思考中", ignoreCase = true) ||
               trimmed.startsWith("💭 Thinking", ignoreCase = true)
    }

    private fun cleanThinkingPrefix(trimmed: String): String {
        return trimmed
            .removePrefix("<thinking>")
            .removePrefix("Thinking...")
            .removePrefix("Thinking:")
            .removePrefix("思考中...")
            .removePrefix("思考中:")
            .removePrefix("💭 Thinking")
            .replace(Regex("""^Thinking\s*\([0-9.]+s\)\s*\.{0,3}"""), "")
            .trim()
    }

    fun isBtwBoxHeader(trimmed: String): Boolean {
        val lower = trimmed.lowercase()
        val hasKeyword = lower.contains("btw") || lower.contains("by the way") || lower.contains("旁支")
        if (!hasKeyword) return false
        val boxStarters = listOf("┌", "╭", "╔", "┏", "+-", "--", "==", "[", "【", "(", "·", "•")
        return boxStarters.any { lower.startsWith(it) } ||
               lower.startsWith("btw") ||
               lower.startsWith("by the way") ||
               lower.startsWith("旁支")
    }

    /**
     * Accurately determines if a line introduces a tool call, avoiding misclassification of markdown bullet points.
     */
    private fun isToolStart(trimmed: String): Boolean {
        if (isBtwBoxHeader(trimmed)) return false
        if (trimmed.startsWith("┌─ ") || trimmed.startsWith("┌─")) return true
        if (trimmed.startsWith("Tool: ", ignoreCase = true) || trimmed.startsWith("Tool call: ", ignoreCase = true)) return true
        if (trimmed.startsWith("[Tool: ", ignoreCase = true)) return true
        if (trimmed.startsWith("Running bash:", ignoreCase = true) || trimmed.startsWith("Running tool:", ignoreCase = true)) return true
        if (trimmed.startsWith("$ ") && trimmed.length > 2) return true

        // For bullets like '● ' or '• ': ONLY match if the subsequent word is a recognized tool name!
        if (trimmed.startsWith("● ") || trimmed.startsWith("• ")) {
            val afterBullet = trimmed.substring(2).trim()
            val firstToken = afterBullet.split(Regex("""[\s\(:\[]"""), limit = 2).firstOrNull().orEmpty().lowercase()
            return KNOWN_PI_TOOLS.contains(firstToken) || (firstToken.contains("_") && !firstToken.contains("方案"))
        }

        return false
    }

    private fun parseToolStart(trimmed: String): Triple<String, String, String> {
        return when {
            trimmed.startsWith("● ") || trimmed.startsWith("• ") -> {
                val rest = trimmed.substring(2).trim()
                val parts = rest.split(" ", limit = 2)
                val name = parts.firstOrNull().orEmpty()
                val args = parts.getOrNull(1).orEmpty()
                Triple(name, if (args.isNotEmpty()) "$name $args" else name, args)
            }

            trimmed.startsWith("┌─") -> {
                val rest = trimmed.removePrefix("┌─").removePrefix("─").trim()
                val parts = rest.split(" ", limit = 2)
                val name = parts.firstOrNull().orEmpty().ifEmpty { "bash" }
                val args = parts.getOrNull(1).orEmpty()
                Triple(name, if (args.isNotEmpty()) "$name $args" else name, args)
            }

            trimmed.startsWith("Tool: ", ignoreCase = true) -> {
                val rest = trimmed.substring(6).trim()
                val parts = rest.split(" ", limit = 2)
                val name = parts.firstOrNull().orEmpty()
                val args = parts.getOrNull(1).orEmpty()
                Triple(name, rest, args)
            }

            trimmed.startsWith("Tool call: ", ignoreCase = true) -> {
                val rest = trimmed.substring(11).trim()
                val parts = rest.split(" ", limit = 2)
                val name = parts.firstOrNull().orEmpty()
                val args = parts.getOrNull(1).orEmpty()
                Triple(name, rest, args)
            }

            trimmed.startsWith("[Tool: ", ignoreCase = true) -> {
                val rest = trimmed.removePrefix("[Tool: ").removeSuffix("]").trim()
                val parts = rest.split(" ", limit = 2)
                val name = parts.firstOrNull().orEmpty()
                val args = parts.getOrNull(1).orEmpty()
                Triple(name, rest, args)
            }

            trimmed.startsWith("$ ") -> {
                val cmd = trimmed.removePrefix("$ ").trim()
                Triple("bash", cmd, cmd)
            }

            else -> {
                Triple("tool", trimmed, trimmed)
            }
        }
    }

    private fun isHeadingOrMarkdown(trimmed: String): Boolean {
        return trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") ||
               trimmed.startsWith("```") || trimmed.startsWith("1. **") || trimmed.startsWith("- **")
    }

    private fun isAssistantStart(trimmed: String): Boolean {
        if (trimmed.isEmpty()) return false
        val firstChar = trimmed[0]
        // CJK characters or Chinese punctuation indicate Chinese assistant response
        if (firstChar in '\u4e00'..'\u9fa5' || firstChar in '\u3000'..'\u303f' || firstChar in '\uff00'..'\uffef') {
            return true
        }
        // Markdown headers, lists, blockquotes, code blocks, bold
        if (trimmed.startsWith("#") ||
            trimmed.startsWith("**") ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("~~~") ||
            trimmed.startsWith("、、、") ||
            trimmed.startsWith("> ") ||
            trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") ||
            trimmed.startsWith("+ ") ||
            trimmed.matches(Regex("""^\d+[\.\)]\s+.*"""))
        ) {
            return true
        }
        // Conversational English assistant openers
        val lower = trimmed.lowercase()
        val englishOpeners = listOf(
            "based on", "according to", "i have", "i've", "we can see",
            "it appears", "it seems", "here is", "here's", "to fix this",
            "in order to", "in summary", "the root cause", "this indicates",
            "the issue is", "the error occurs because"
        )
        return englishOpeners.any { lower.startsWith(it) }
    }

    private fun isNoiseLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (DIVIDER_REGEX.matches(trimmed)) return true
        if (PWSH_PROMPT_REGEX.matches(trimmed)) return true
        if (trimmed == "PS" || trimmed == "PS >" || trimmed == ">" || trimmed == "❯") return true
        if (STATUS_BAR_REGEX.containsMatchIn(trimmed) && trimmed.length < 120 &&
            (trimmed.contains("%/") || trimmed.contains("•") || trimmed.startsWith("D:\\"))) return true
        return false
    }

    private fun isPureNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        return isNoiseLine(trimmed)
    }

    private fun sanitizeAssistantText(text: String): String {
        return text.lines()
            .filterNot { isNoiseLine(it) }
            .joinToString("\n")
            .trim()
    }

    fun isExplicitErrorText(text: String): Boolean {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        val isAbort = lower.contains("aborted") || lower.contains("canceled") || lower.contains("cancelled") || lower.contains("interrupt")
        if (isAbort) return false
        return (trimmed.startsWith("Error:", ignoreCase = true) ||
                trimmed.startsWith("Error [", ignoreCase = true) ||
                trimmed.startsWith("Exception:", ignoreCase = true) ||
                lower.contains("gateway request failed") ||
                lower.contains("429 status code") ||
                lower.contains("too many requests") ||
                lower.contains("invalid error response format")) &&
                !lower.contains("how to fix") && !lower.contains("solution")
    }
}
