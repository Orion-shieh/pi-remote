package com.piremote.app.ui.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the RPC event → GUI card reducer. Typed [AgentEvent]s are
 * constructed directly, so no Android or org.json machinery is involved.
 */
class GuiAgentStateTest {

    private fun userMessage(text: String) = AgentMessage(
        role = "user",
        toolCallId = null,
        isError = false,
        content = listOf(AgentBlock.Text(text)),
    )

    private fun assistantMessage(
        blocks: List<AgentBlock>,
        isError: Boolean = false,
    ) = AgentMessage(role = "assistant", toolCallId = null, isError = isError, content = blocks)

    private fun toolResultMessage(toolCallId: String, text: String, isError: Boolean = false) = AgentMessage(
        role = "toolResult",
        toolCallId = toolCallId,
        isError = isError,
        content = listOf(AgentBlock.Text(text)),
    )

    // ------------------------------------------------------------- snapshot

    @Test
    fun `snapshot renders entries as cards`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = false),
                entries = listOf(
                    AgentEntry("e1", "model_change", null, "模型切换 · google/gemini"),
                    AgentEntry("e2", "message", userMessage("帮我看看状态"), null),
                    AgentEntry(
                        "e3",
                        "message",
                        assistantMessage(
                            listOf(
                                AgentBlock.Thinking("先查一下"),
                                AgentBlock.ToolCall("tc1", "bash", null, """{"command":"git status"}"""),
                                AgentBlock.Text("完成了"),
                            ),
                        ),
                        null,
                    ),
                ),
                truncated = false,
                error = null,
            ),
        )

        val items = state.items
        assertEquals(5, items.size)
        assertTrue(items[0] is SystemStatusMessage)
        assertTrue(items[1] is UserMessage)
        assertEquals("帮我看看状态", (items[1] as UserMessage).text)
        assertTrue(items[2] is ThinkingBlock)
        assertTrue((items[2] as ThinkingBlock).isFinished)
        val tool = items[3] as ToolCallBlock
        assertEquals("bash", tool.toolName)
        assertEquals(ToolStatus.SUCCESS, tool.status)
        assertEquals("""{"command":"git status"}""", tool.arguments)
        assertTrue(items[4] is AssistantResponse)
        assertEquals("完成了", (items[4] as AssistantResponse).text)
    }

    @Test
    fun `snapshot timestamps come from the session entries not from now`() {
        val state = GuiAgentState()
        val then = 1_700_000_000_000L
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = false),
                entries = listOf(
                    AgentEntry("e1", "message", userMessage("你好"), null, timestampMs = then),
                    AgentEntry(
                        "e2",
                        "message",
                        assistantMessage(listOf(AgentBlock.Text("完成了"))),
                        null,
                        timestampMs = then + 5000,
                    ),
                ),
                truncated = false,
                error = null,
            ),
        )
        assertEquals(then, (state.items[0] as UserMessage).timestamp)
        assertEquals(then + 5000, (state.items[1] as AssistantResponse).timestamp)
    }

    @Test
    fun `batched snapshot accumulates entries across frames and renders once`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = false),
                entries = listOf(AgentEntry("e1", "message", userMessage("第一条"), null, timestampMs = 0)),
                truncated = false,
                error = null,
                name = null,
                batchIndex = 0,
                batchTotal = 2,
            ),
        )
        state.onEvent(
            AgentEvent.SnapshotBatch(
                entries = listOf(
                    AgentEntry("e2", "message", userMessage("第二条"), null, timestampMs = 0),
                    AgentEntry(
                        "e3",
                        "message",
                        assistantMessage(listOf(AgentBlock.Text("回复"))),
                        null,
                        timestampMs = 0,
                    ),
                ),
                batchIndex = 1,
                batchTotal = 2,
            ),
        )
        assertEquals(2, state.items.filterIsInstance<UserMessage>().size)
        assertEquals(1, state.items.filterIsInstance<AssistantResponse>().size)
    }

    @Test
    fun `snapshot records streaming state and truncation notice`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = true),
                entries = emptyList(),
                truncated = true,
                error = null,
            ),
        )
        assertTrue(state.agentRunning)
        // Pending "thinking" placeholder shows while the run is active.
        val pending = state.items.filterIsInstance<ThinkingBlock>()
        assertTrue(pending.any { !it.isFinished })
        assertTrue(state.items.any { it is SystemStatusMessage })
    }

    @Test
    fun `snapshot state exposes current model and thinking level`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(
                    modelProvider = "cmdgo",
                    modelId = "deepseek/deepseek-v4.1-flash",
                    modelName = "DeepSeek V4.1 Flash (Command Code)",
                    thinkingLevel = "medium",
                    isStreaming = false,
                ),
                entries = emptyList(),
                truncated = false,
                error = null,
            ),
        )
        // pi model ids often contain a slash themselves; the key must stay
        // provider/id while the label prefers the human-readable name.
        assertEquals("cmdgo/deepseek/deepseek-v4.1-flash", state.currentModelKey)
        assertEquals("DeepSeek V4.1 Flash (Command Code)", state.currentModelLabel)
        assertEquals("medium", state.currentThinking)
    }

    @Test
    fun `model catalogue and set_model responses update the picker state`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.CommandResponse(
                command = "get_available_models",
                success = true,
                error = null,
                state = null,
                models = listOf(
                    PiModelOption("deepseek", "deepseek-v4-flash", "DeepSeek V4 Flash"),
                    PiModelOption("amd", "DeepSeek-V4-Flash-Vision-Exp", null),
                ),
                thinkingLevels = emptyList(),
            ),
        )
        assertEquals(2, state.availableModels.size)
        assertEquals("DeepSeek V4 Flash", state.availableModels[0].label)
        assertEquals("DeepSeek-V4-Flash-Vision-Exp", state.availableModels[1].label)

        state.onEvent(
            AgentEvent.CommandResponse(
                command = "set_model",
                success = true,
                error = null,
                state = SessionStateInfo("amd", "DeepSeek-V4-Flash-Vision-Exp", null, null, false),
                models = emptyList(),
                thinkingLevels = emptyList(),
            ),
        )
        assertEquals("amd/DeepSeek-V4-Flash-Vision-Exp", state.currentModelKey)
        assertEquals("DeepSeek-V4-Flash-Vision-Exp", state.currentModelLabel)
    }

    @Test
    fun `failed commands surface an error notice`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.CommandResponse(
                command = "steer",
                success = false,
                error = "not streaming",
                state = null,
                models = emptyList(),
                thinkingLevels = emptyList(),
            ),
        )
        val notices = state.items.filterIsInstance<SystemStatusMessage>()
        assertTrue(notices.any { it.text.contains("steer") && it.text.contains("not streaming") })
    }

    @Test
    fun `get_commands fills the slash palette and stats render as a notice`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.CommandResponse(
                command = "get_commands",
                success = true,
                commands = listOf(
                    PiSlashCommand("mcp", "Show MCP server status", "extension"),
                    PiSlashCommand("skill:agent-reach", "research helper", "skill"),
                ),
            ),
        )
        assertEquals(2, state.slashCommands.size)
        assertEquals("mcp", state.slashCommands[0].name)

        state.onEvent(
            AgentEvent.CommandResponse(
                command = "get_session_stats",
                success = true,
                statsText = "会话统计 · 消息 6（用户 2 / 助手 2 / 工具 2）",
            ),
        )
        assertTrue(
            state.items.filterIsInstance<SystemStatusMessage>()
                .any { it.text.contains("会话统计") },
        )
    }

    @Test
    fun `thinking levels catalogue is recorded`() {
        val state = GuiAgentState()
        assertFalse(state.thinkingLevelsLoaded)
        state.onEvent(
            AgentEvent.CommandResponse(
                command = "get_available_thinking_levels",
                success = true,
                error = null,
                state = null,
                models = emptyList(),
                thinkingLevels = listOf("off", "medium", "high"),
            ),
        )
        assertEquals(listOf("off", "medium", "high"), state.thinkingLevels)
        assertTrue(state.thinkingLevelsLoaded)
    }

    @Test
    fun `empty thinking levels updates state and sets loaded`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.CommandResponse(
                command = "get_available_thinking_levels",
                success = true,
                thinkingLevels = emptyList(),
            ),
        )
        assertTrue(state.thinkingLevels.isEmpty())
        assertTrue(state.thinkingLevelsLoaded)
    }

    @Test
    fun `set_thinking_level response updates currentThinking`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.CommandResponse(
                command = "set_thinking_level",
                success = true,
                state = SessionStateInfo(
                    modelProvider = null,
                    modelId = null,
                    modelName = null,
                    thinkingLevel = "medium",
                    isStreaming = false,
                ),
            ),
        )
        assertEquals("medium", state.currentThinking)
        state.updateThinking("high")
        assertEquals("high", state.currentThinking)
    }

    // ------------------------------------------------------------ streaming

    @Test
    fun `text and thinking deltas accumulate into streaming cards`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageStart(assistantMessage(emptyList())))

        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("thinking_start", 0, null, null, null, null, null),
            ),
        )
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("thinking_delta", 0, "第一步", null, null, null, null),
            ),
        )
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("text_start", 1, null, null, null, null, null),
            ),
        )
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("text_delta", 1, "你好", null, null, null, null),
            ),
        )
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("text_delta", 1, "世界", null, null, null, null),
            ),
        )

        val items = state.items
        val thinking = items.filterIsInstance<ThinkingBlock>().first()
        assertEquals("第一步", thinking.content)
        assertFalse(thinking.isFinished)
        val response = items.filterIsInstance<AssistantResponse>().first()
        assertEquals("你好世界", response.text)
    }

    @Test
    fun `toolcall deltas build the tool card and execution events update it`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageStart(assistantMessage(emptyList())))

        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("toolcall_start", 0, null, null, "tc9", "bash", null),
            ),
        )
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("toolcall_delta", 0, """{"command":"ls"}""", null, "tc9", "bash", null),
            ),
        )

        val running = state.items.filterIsInstance<ToolCallBlock>().single()
        assertEquals(ToolStatus.RUNNING, running.status)
        assertEquals("bash", running.toolName)
        assertEquals("""{"command":"ls"}""", running.arguments)

        state.onEvent(AgentEvent.ToolExecutionStart("tc9", "bash"))
        state.onEvent(AgentEvent.ToolExecutionUpdate("tc9", null, "file-a\nfile-b\n", false))
        state.onEvent(AgentEvent.ToolExecutionEnd("tc9"))

        val finished = state.items.filterIsInstance<ToolCallBlock>().single()
        assertEquals(ToolStatus.SUCCESS, finished.status)
        assertEquals("file-a\nfile-b", finished.output)
    }

    @Test
    fun `message_end finalizes cards and replaces the streaming section`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageStart(assistantMessage(emptyList())))
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("text_start", 0, null, null, null, null, null),
            ),
        )
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("text_delta", 0, "部分", null, null, null, null),
            ),
        )

        state.onEvent(
            AgentEvent.MessageEnd(
                assistantMessage(
                    listOf(
                        AgentBlock.Text("这是完整回复"),
                    ),
                ),
            ),
        )
        state.onEvent(AgentEvent.AgentSettled)

        assertFalse(state.agentRunning)
        val items = state.items
        assertTrue(items.none { it.id.startsWith("a-streaming") })
        val response = items.filterIsInstance<AssistantResponse>().single()
        assertEquals("这是完整回复", response.text)
        assertTrue(response.id.startsWith("a-m"))
    }

    // --------------------------------------------------------- tool results

    @Test
    fun `final tool result replaces partial output and error status wins`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.ToolExecutionStart("tc1", "bash"))
        state.onEvent(AgentEvent.ToolExecutionUpdate("tc1", null, "partial...", false))
        state.onEvent(AgentEvent.MessageEnd(toolResultMessage("tc1", "完整输出", isError = true)))

        // A card only exists once the assistant toolCall block arrives; the
        // tool run state must survive that handoff.
        state.onEvent(AgentEvent.MessageStart(assistantMessage(emptyList())))
        state.onEvent(
            AgentEvent.MessageUpdate(
                AssistantDelta("toolcall_start", 0, null, null, "tc1", "bash", null),
            ),
        )

        val tool = state.items.filterIsInstance<ToolCallBlock>().single()
        assertEquals(ToolStatus.ERROR, tool.status)
        assertEquals("完整输出", tool.output)
    }

    @Test
    fun `snapshot tool result pairs output with the tool call card`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = false),
                entries = listOf(
                    AgentEntry("e1", "message", userMessage("hi"), null),
                    AgentEntry(
                        "e2",
                        "message",
                        assistantMessage(listOf(AgentBlock.ToolCall("tc1", "bash", null, "{}"))),
                        null,
                    ),
                    AgentEntry("e3", "message", toolResultMessage("tc1", "cmd output"), null),
                ),
                truncated = false,
                error = null,
            ),
        )

        val tool = state.items.filterIsInstance<ToolCallBlock>().single()
        assertEquals("cmd output", tool.output)
        assertEquals(ToolStatus.SUCCESS, tool.status)
    }

    // ---------------------------------------------------------------- dialogs

    @Test
    fun `select dialog renders options and resolve records the answer`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.ExtensionUiRequest(
                requestId = "req-1",
                method = "select",
                title = "选个方案",
                message = null,
                options = listOf("方案 A", "方案 B"),
                placeholder = null,
                statusKey = null,
                statusText = null,
                widgetLines = emptyList(),
            ),
        )

        val dialog = state.items.filterIsInstance<QuestionOptionBlock>().single()
        assertEquals("req-1", dialog.requestId)
        assertEquals(2, dialog.options.size)
        assertEquals("方案 A", dialog.options[0].label)
        assertFalse(dialog.isAnswered)

        state.resolveDialog("req-1", "方案 B")
        val answered = state.items.filterIsInstance<QuestionOptionBlock>().single()
        assertTrue(answered.isAnswered)
        assertEquals("方案 B", answered.selectedKey)
    }

    @Test
    fun `confirm dialog gets yes and no options with stable ids`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.ExtensionUiRequest(
                requestId = "req-2",
                method = "confirm",
                title = "继续吗",
                message = "将执行 git push",
                options = emptyList(),
                placeholder = null,
                statusKey = null,
                statusText = null,
                widgetLines = emptyList(),
            ),
        )

        val dialog = state.items.filterIsInstance<QuestionOptionBlock>().single()
        assertEquals(listOf("yes", "no"), dialog.options.map { it.key })
    }

    // --------------------------------------------------------- user messages

    @Test
    fun `optimistic user bubble is replaced by the echoed message`() {
        val state = GuiAgentState()
        state.addUserMessage("跑个测试")

        assertTrue(state.items.any { it is UserMessage && it.text == "跑个测试" })

        state.onEvent(AgentEvent.MessageEnd(userMessage("跑个测试")))
        val users = state.items.filterIsInstance<UserMessage>()
        assertEquals(1, users.size)
        assertTrue(users[0].id.startsWith("u"))
    }

    @Test
    fun `optimistic BTW user bubble is replaced by the echoed message`() {
        val state = GuiAgentState()
        state.addUserMessage("/btw 这个变量什么意思", sendType = "btw")

        assertTrue(state.items.any { it is UserMessage && it.text == "/btw 这个变量什么意思" && it.sendType == "btw" })

        // Echoed from pi with prompt instruction
        state.onEvent(AgentEvent.MessageEnd(userMessage("【旁支提问/BTW】这个变量什么意思\n(说明：这是旁支提问，请直接简要回答该问题，无需调用工具修改项目代码。)")))
        val users = state.items.filterIsInstance<UserMessage>()
        assertEquals(1, users.size)
        assertTrue(users[0].id.startsWith("u"))
        assertEquals("/btw 这个变量什么意思", users[0].text)
        assertEquals("btw", users[0].sendType)
    }

    @Test
    fun `snapshot decodes BTW message and sets sendType`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = false),
                entries = listOf(
                    AgentEntry("e1", "message", userMessage("【旁支提问/BTW】这个变量什么意思\n(说明：这是旁支提问，请直接简要回答该问题，无需调用工具修改项目代码。)"), null),
                ),
                truncated = false,
                error = null,
            ),
        )

        val users = state.items.filterIsInstance<UserMessage>()
        assertEquals(1, users.size)
        assertEquals("这个变量什么意思", users[0].text)
        assertEquals("btw", users[0].sendType)
    }

    @Test
    fun `TerminalChatParser parses rounded corner and bracketed BTW boxes`() {
        val transcript = """
            ╭─ BTW ──────────────────────────╮
            │ 这是旁支提问的回复内容         │
            ╰────────────────────────────────╯
        """.trimIndent()
        val items = TerminalChatParser.parseTranscript(transcript)
        val assistants = items.filterIsInstance<AssistantResponse>()
        assertEquals(1, assistants.size)
        assertEquals("这是旁支提问的回复内容", assistants[0].text.trim())
    }

    // ----------------------------------------------------------------- status

    @Test
    fun `setStatus lines build the status strip`() {
        val state = GuiAgentState()
        state.onEvent(dialogRequest("setStatus", statusKey = "k1", statusText = "索引中"))
        state.onEvent(dialogRequest("setStatus", statusKey = "k2", statusText = "就绪"))
        assertEquals("索引中 · 就绪", state.statusLine)

        state.onEvent(dialogRequest("setStatus", statusKey = "k1", statusText = null))
        assertEquals("就绪", state.statusLine)
    }

    // ----------------------------------------------------------- token usage

    @Test
    fun `snapshot preserves message usage in AssistantResponse`() {
        val state = GuiAgentState()
        val usage = MessageUsage(inputTokens = 1250, outputTokens = 380, cacheReadTokens = 512, totalTokens = 1630)
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(null, null, null, null, isStreaming = false),
                entries = listOf(
                    AgentEntry("e1", "message", userMessage("你好"), null),
                    AgentEntry(
                        "e2",
                        "message",
                        AgentMessage(
                            role = "assistant",
                            toolCallId = null,
                            isError = false,
                            content = listOf(AgentBlock.Text("你好！有什么我可以帮你的？")),
                            usage = usage,
                        ),
                        null,
                    ),
                ),
                truncated = false,
                error = null,
            ),
        )

        val responses = state.items.filterIsInstance<AssistantResponse>()
        assertEquals(1, responses.size)
        assertEquals(usage, responses[0].usage)
        assertEquals(1250, responses[0].usage?.inputTokens)
        assertEquals(380, responses[0].usage?.outputTokens)
        assertEquals(512, responses[0].usage?.cacheReadTokens)
    }

    @Test
    fun `message_end delivers token usage into AssistantResponse`() {
        val state = GuiAgentState()
        val usage = MessageUsage(inputTokens = 800, outputTokens = 150, cacheReadTokens = 200, totalTokens = 950)
        val assistant = AgentMessage(
            role = "assistant",
            toolCallId = null,
            isError = false,
            content = listOf(AgentBlock.Text("处理完毕")),
            usage = usage,
        )

        state.onEvent(AgentEvent.MessageEnd(assistant))
        val responses = state.items.filterIsInstance<AssistantResponse>()
        assertEquals(1, responses.size)
        assertEquals("处理完毕", responses[0].text)
        assertEquals(usage, responses[0].usage)
    }

    @Test
    fun `parseUsage extracts deepseek and gemini cache hit tokens`() {
        val deepseekJson = org.json.JSONObject("""
            {
                "prompt_tokens": 1200,
                "completion_tokens": 300,
                "prompt_cache_hit_tokens": 850,
                "prompt_cache_miss_tokens": 350
            }
        """.trimIndent())
        val deepseekUsage = AgentEventJson.parseUsage(deepseekJson)
        assertEquals(1200, deepseekUsage?.inputTokens)
        assertEquals(300, deepseekUsage?.outputTokens)
        assertEquals(850, deepseekUsage?.cacheReadTokens)

        val geminiJson = org.json.JSONObject("""
            {
                "inputTokens": 2000,
                "outputTokens": 500,
                "cachedContentTokenCount": 1600
            }
        """.trimIndent())
        val geminiUsage = AgentEventJson.parseUsage(geminiJson)
        assertEquals(2000, geminiUsage?.inputTokens)
        assertEquals(500, geminiUsage?.outputTokens)
        assertEquals(1600, geminiUsage?.cacheReadTokens)

        // Anthropic style: inputTokens represents uncached, cacheReadTokens represents cached prefix
        val anthropicUsage = MessageUsage(inputTokens = 256, outputTokens = 37, cacheReadTokens = 25600)
        assertEquals(25856, anthropicUsage.totalPromptTokens)
        assertEquals(25856, anthropicUsage.effectiveInputTokens)
        assertEquals(99, anthropicUsage.cacheHitPercent)
        assertEquals("99.0%", anthropicUsage.cacheHitPercentText)

        // Real turn usage scenario (e.g. terminal ↑18k ↓367 R18k CH49.6%)
        val realTurnUsage = MessageUsage(inputTokens = 17950, outputTokens = 367, cacheReadTokens = 17680)
        assertEquals(35630, realTurnUsage.totalPromptTokens)
        assertEquals("49.6%", realTurnUsage.cacheHitPercentText)
    }

    // ------------------------------------------------------------- error handling

    @Test
    fun `error event produces ErrorMessageBlock and stops running state`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        assertTrue(state.agentRunning)

        state.onEvent(AgentEvent.Error("Error: 429 status code (no body)", code = "429"))
        assertFalse(state.agentRunning)

        val errors = state.items.filterIsInstance<ErrorMessageBlock>()
        assertEquals(1, errors.size)
        assertEquals("Error: 429 status code (no body)", errors[0].error)
        assertEquals("429", errors[0].details)
    }

    @Test
    fun `parseMessage extracts error text when content is empty and has error`() {
        val errorJson = org.json.JSONObject("""
            {
                "role": "assistant",
                "isError": true,
                "error": "Error: Invalid error response format: Gateway request failed"
            }
        """.trimIndent())
        val msg = AgentEventJson.parseMessage(errorJson)
        assertTrue(msg.isError)
        assertEquals(1, msg.content.size)
        assertTrue(msg.content[0] is AgentBlock.Text)
        assertEquals("Error: Invalid error response format: Gateway request failed", (msg.content[0] as AgentBlock.Text).text)
    }

    @Test
    fun `error and stream_error json events are parsed as AgentEvent Error`() {
        val json1 = org.json.JSONObject("""
            {
                "type": "error",
                "error": "Error: 429 status code (no body)"
            }
        """.trimIndent())
        val event1 = AgentEventJson.parse(json1)
        assertTrue(event1 is AgentEvent.Error)
        assertEquals("Error: 429 status code (no body)", (event1 as AgentEvent.Error).message)

        val json2 = org.json.JSONObject("""
            {
                "type": "agent_error",
                "message": "Gateway timeout"
            }
        """.trimIndent())
        val event2 = AgentEventJson.parse(json2)
        assertTrue(event2 is AgentEvent.Error)
        assertEquals("Gateway timeout", (event2 as AgentEvent.Error).message)
    }

    @Test
    fun `parseMessage extracts error when only stopReason and errorMessage are present`() {
        val errorJson = org.json.JSONObject("""
            {
                "role": "assistant",
                "content": [],
                "stopReason": "error",
                "errorMessage": "Error: 429 status code (no body)"
            }
        """.trimIndent())
        val msg = AgentEventJson.parseMessage(errorJson)
        assertTrue(msg.isError)
        assertEquals(1, msg.content.size)
        assertTrue(msg.content[0] is AgentBlock.Text)
        assertEquals("Error: 429 status code (no body)", (msg.content[0] as AgentBlock.Text).text)
    }

    @Test
    fun `onMessageEnd with isError generates ErrorMessageBlock and stops agent`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        assertTrue(state.agentRunning)

        val errorJson = org.json.JSONObject("""
            {
                "role": "assistant",
                "content": [],
                "stopReason": "error",
                "errorMessage": "Error: Invalid error response format: Gateway request failed"
            }
        """.trimIndent())
        val msg = AgentEventJson.parseMessage(errorJson)
        state.onEvent(AgentEvent.MessageEnd(msg))

        assertFalse(state.agentRunning)
        val errorBlocks = state.items.filterIsInstance<ErrorMessageBlock>()
        assertEquals(1, errorBlocks.size)
        assertEquals("Error: Invalid error response format: Gateway request failed", errorBlocks[0].error)
    }

    @Test
    fun `TerminalChatParser parses Gateway and 429 errors into ErrorMessageBlock`() {
        val transcript = """
            Thinking...
            Error: 429 status code (no body)
        """.trimIndent()
        val items = TerminalChatParser.parseTranscript(transcript)
        val errors = items.filterIsInstance<ErrorMessageBlock>()
        assertEquals(1, errors.size)
        assertTrue(errors[0].error.contains("429 status code"))
    }

    @Test
    fun `aborted and canceled messages do not produce ErrorMessageBlock`() {
        val abortJson = org.json.JSONObject("""
            {
                "role": "assistant",
                "content": [],
                "stopReason": "aborted",
                "errorMessage": "Request aborted"
            }
        """.trimIndent())
        val msg = AgentEventJson.parseMessage(abortJson)
        assertFalse(msg.isError)

        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageEnd(msg))
        assertFalse(state.agentRunning)
        val errorBlocks = state.items.filterIsInstance<ErrorMessageBlock>()
        assertEquals(0, errorBlocks.size)

        val statusMessages = state.items.filterIsInstance<SystemStatusMessage>()
        assertEquals(1, statusMessages.size)
        assertEquals("已停止生成", statusMessages[0].text)

        val assistantBlocks = state.items.filterIsInstance<AssistantResponse>()
        assertEquals(0, assistantBlocks.size)
    }

    @Test
    fun `onAbort immediately stops agent running and creates system notice without empty box`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageStart(AgentMessage(role = "assistant", toolCallId = null, isError = false, content = emptyList())))
        state.onEvent(AgentEvent.Delta(AssistantDelta(type = "text_delta", contentIndex = 0, deltaText = "一部分回答")))
        assertTrue(state.agentRunning)

        state.onAbort()
        assertFalse(state.agentRunning)

        val assistantBlocks = state.items.filterIsInstance<AssistantResponse>()
        assertEquals(1, assistantBlocks.size)
        assertEquals("一部分回答", assistantBlocks[0].text)

        val statusMessages = state.items.filterIsInstance<SystemStatusMessage>()
        assertEquals(1, statusMessages.size)
        assertEquals("已停止生成", statusMessages[0].text)
    }

    @Test
    fun `onAbort with no text generates only system status notice without any assistant box`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageStart(AgentMessage(role = "assistant", toolCallId = null, isError = false, content = emptyList())))
        assertTrue(state.agentRunning)

        state.onAbort()
        assertFalse(state.agentRunning)

        val assistantBlocks = state.items.filterIsInstance<AssistantResponse>()
        assertEquals(0, assistantBlocks.size)

        val statusMessages = state.items.filterIsInstance<SystemStatusMessage>()
        assertEquals(1, statusMessages.size)
        assertEquals("已停止生成", statusMessages[0].text)
    }

    @Test
    fun `onAbort followed by MessageEnd does not produce duplicate assistant response`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(AgentEvent.MessageStart(AgentMessage(role = "assistant", toolCallId = null, isError = false, content = emptyList())))
        state.onEvent(AgentEvent.Delta(AssistantDelta(type = "text_delta", contentIndex = 0, deltaText = "一部分回答")))
        assertTrue(state.agentRunning)

        // 1. User clicks stop
        state.onAbort()
        assertFalse(state.agentRunning)

        // 2. Server sends MessageEnd for the aborted turn with the same content
        val endMsg = AgentMessage(
            role = "assistant",
            toolCallId = null,
            isError = false,
            content = listOf(AgentBlock.Text("一部分回答")),
            isAborted = true,
        )
        state.onEvent(AgentEvent.MessageEnd(endMsg))

        // Verify there is EXACTLY ONE AssistantResponse and ONE SystemStatusMessage
        val assistantBlocks = state.items.filterIsInstance<AssistantResponse>()
        assertEquals(1, assistantBlocks.size)
        assertEquals("一部分回答", assistantBlocks[0].text)

        val statusMessages = state.items.filterIsInstance<SystemStatusMessage>()
        assertEquals(1, statusMessages.size)
        assertEquals("已停止生成", statusMessages[0].text)
    }

    @Test
    fun `user message retains model and provider when added`() {
        val state = GuiAgentState()
        state.onEvent(
            AgentEvent.Snapshot(
                state = SessionStateInfo(
                    model = ModelInfo("deepseek-chat", "DeepSeek Chat", null),
                    thinkingLevel = null,
                    tools = emptyList(),
                    isStreaming = false,
                )
            )
        )
        assertEquals("DeepSeek Chat", state.currentModelLabel)

        state.addUserMessage("Hello world", model = state.currentModelLabel, provider = "deepseek")
        val userMessages = state.items.filterIsInstance<UserMessage>()
        assertEquals(1, userMessages.size)
        assertEquals("Hello world", userMessages[0].text)
        assertEquals("DeepSeek Chat", userMessages[0].model)
        assertEquals("deepseek", userMessages[0].provider)
    }

    @Test
    fun `user message retains sendType when specified`() {
        val state = GuiAgentState()
        state.addUserMessage("引导指令", model = "deepseek-chat", provider = "deepseek", sendType = "steer")
        state.addUserMessage("跟进排队", model = "deepseek-chat", provider = "deepseek", sendType = "follow_up")
        state.addUserMessage("普通发送", model = "deepseek-chat", provider = "deepseek")

        val userMessages = state.items.filterIsInstance<UserMessage>()
        assertEquals(3, userMessages.size)
        assertEquals("引导指令", userMessages[0].text)
        assertEquals("steer", userMessages[0].sendType)

        assertEquals("跟进排队", userMessages[1].text)
        assertEquals("follow_up", userMessages[1].sendType)

        assertEquals("普通发送", userMessages[2].text)
        assertNull(userMessages[2].sendType)
    }

    @Test
    fun `steering message appears after streaming content at the bottom`() {
        val state = GuiAgentState()
        state.onEvent(AgentEvent.AgentStart)
        state.onEvent(
            AgentEvent.MessageStart(
                AgentMessage(
                    role = "assistant",
                    toolCallId = null,
                    isError = false,
                    content = listOf(AgentBlock.Text("正在生成代码...")),
                )
            )
        )

        // User sends a steer message while streaming
        state.addUserMessage("使用深色模式", sendType = "steer")

        val items = state.items
        assertTrue(items.isNotEmpty())

        // The assistant streaming response should come first
        val firstBlock = items.first()
        assertTrue(firstBlock is AssistantResponse)
        assertEquals("正在生成代码...", (firstBlock as AssistantResponse).text)

        // The steering message MUST be at the bottom, after the active execution
        val lastBlock = items.last()
        assertTrue(lastBlock is UserMessage)
        val steerMsg = lastBlock as UserMessage
        assertEquals("使用深色模式", steerMsg.text)
        assertEquals("steer", steerMsg.sendType)

        // Turn 1 completes
        state.onEvent(
            AgentEvent.MessageEnd(
                AgentMessage(
                    role = "assistant",
                    toolCallId = null,
                    isError = false,
                    content = listOf(AgentBlock.Text("正在生成代码...")),
                )
            )
        )

        // Turn 2 begins responding to the steer
        state.onEvent(
            AgentEvent.MessageStart(
                AgentMessage(
                    role = "assistant",
                    toolCallId = null,
                    isError = false,
                    content = listOf(AgentBlock.Text("已为您切换为深色模式")),
                )
            )
        )

        val itemsAfterTurn2Start = state.items
        assertEquals(3, itemsAfterTurn2Start.size)
        // 1. Turn 1 assistant message
        assertTrue(itemsAfterTurn2Start[0] is AssistantResponse)
        assertEquals("正在生成代码...", (itemsAfterTurn2Start[0] as AssistantResponse).text)
        // 2. Steer message between Turn 1 and Turn 2
        assertTrue(itemsAfterTurn2Start[1] is UserMessage)
        assertEquals("使用深色模式", (itemsAfterTurn2Start[1] as UserMessage).text)
        assertEquals("steer", (itemsAfterTurn2Start[1] as UserMessage).sendType)
        // 3. Turn 2 assistant message responding to steer
        assertTrue(itemsAfterTurn2Start[2] is AssistantResponse)
        assertEquals("已为您切换为深色模式", (itemsAfterTurn2Start[2] as AssistantResponse).text)
    }

    private fun dialogRequest(
        method: String,
        statusKey: String? = null,
        statusText: String? = null,
    ) = AgentEvent.ExtensionUiRequest(
        requestId = "r-$method-$statusKey",
        method = method,
        title = "",
        message = null,
        options = emptyList(),
        placeholder = null,
        statusKey = statusKey,
        statusText = statusText,
        widgetLines = emptyList(),
    )
}
