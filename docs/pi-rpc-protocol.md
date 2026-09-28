# pi RPC 轨协议速查（PC Agent ↔ 安卓图形视图）

> 2026-09-12 上线。图形视图（kind = `rpc` 的会话）不再逆向解析终端文本，
> 而是消费 PC Agent 桥接的 `pi --mode rpc` 结构化事件流。
> 事件/命令形状对齐 pi 0.85.x 的 `modes/rpc/rpc-types.d.ts`。

## 数据流

```
[安卓图形视图] ←agent_event(JSON 文本帧)─ [中继(透传)] ─[agent_event]─ [PC Agent]
       │                                                       │ spawn + JSONL 桥
       └─agent_command(JSON 文本帧)─→ [中继] ─→ [PC Agent] ─→ pi --mode rpc 子进程
                                                               stdin/stdout 严格 JSONL
```

## 新增控制信令（包在既有 relay JSON 文本帧通道里，中继零改动）

| t | 方向 | 载荷 |
|---|---|---|
| `agent_event` | Agent → App | `{sid, event}` — pi 会话事件原样转发 |
| `agent_command` | App → Agent | `{sid, command}` — 手机下发的 pi RPC 命令 |

`sessions` / `created` 新增 `kind` 字段：`"pty"` | `"rpc"`。

## attach 流程（rpc 会话）

1. App 发 `attach{sid, lastSeq:0}`（lastSeq 对 rpc 会话无意义）。
2. Agent 先取 `get_state` + `get_entries`（此时不转发实时事件，保证顺序），
   包装成首条 `agent_event`：`{type:"snapshot", state, entries(尾部200条), truncated, error?}`。
3. 之后实时事件开始转发（detached 期间的事件直接丢弃，重连靠重新 snapshot）。

## 事件词汇表（event.type）

- `snapshot` — attach 快照
- `agent_start` / `agent_end` / `agent_settled` — 运行周期（`agent_settled` 才是真正的"跑完"）
- `message_start` / `message_end` — 完整消息（role: user / assistant / toolResult）
- `message_update` — 流式 delta，`assistantMessageEvent.type`:
  `text_start/text_delta/text_end`、`thinking_*`、`toolcall_start/toolcall_delta/toolcall_end`，
  均 带 `contentIndex` 定位消息内块下标；toolcall_* 额外带 `id`/`toolName`
- `tool_execution_start/update/end` — 工具执行状态与部分输出（`toolCallId` 关联卡片）
- `extension_ui_request` — 扩展交互：`select`/`confirm`/`input`/`editor`（等待应答）、
  `notify`/`setStatus`/`setWidget`/`setTitle`/`set_editor_text`（火后不管）
- `response` — 手机所发命令的应答（`success` / `error`）
- `extension_error`

## 手机 → pi 命令（经 `agent_command`）

常用：`prompt{message}`（空闲时）、`steer{message}`（运行中插话）、`abort`、
`get_entries{since?}`、`get_commands`、`get_session_stats`、`get_tree`、
`get_last_assistant_text`、`extension_ui_response`。

**斜杠指令**：pi 的 TUI 内建命令（/model /tree 等）在 RPC 通道不存在，发出去就是普通
文本。可用的指令只有 `get_commands` 列出的部分（扩展命令 / 提示模板 / 技能），
以 `prompt{message:"/name ..."}` 触发；TUI 内建项由手机端映射到对应 RPC 命令
（/model→模型面板、/new→new_session、/resume→会话选择器、/status→get_session_stats、
/copy→get_last_assistant_text、/tree→get_tree、/quit→kill）。

**会话切换（/resume）**：pi RPC 没有"列出会话"的命令，pi-web 也是自己扫文件。Agent 新增
控制消息 `{t:"list_pi_sessions", sid}`（agent 自己扫 `~/.pi/agent/sessions/**`，按当前
项目 cwd 过滤，取最近 40 个），以 `agent_event{type:"pi_sessions", sessions:[{file,id,
name,preview,timestamp,messageCount}]}` 应答。选中后发 `switch_session{sessionPath}`，
成功（`data.cancelled=false`）后手机重新 attach，新快照即为目标会话。

**extension_ui_response 应答形状**（与 pi 的 `rpc-types.d.ts` 一致）：

- select / input / editor → `{type:"extension_ui_response", id, value:"<选项文本或输入>"}`
- confirm → `{type:"extension_ui_response", id, confirmed:true|false}`
- 取消 → `{type:"extension_ui_response", id, cancelled:true}`

## 安卓端实现索引

- `ui/gui/AgentEventModels.kt` — 事件 JSON → 类型化模型
- `ui/gui/GuiAgentState.kt` — 事件流 → 消息卡片（纯 Kotlin，含 11 项 JVM 单测）
- `ui/gui/GuiChatScreen.kt` — `GuiChatContent` 按 `sessionKind` 分流（rpc / pty 兜底）
- `ui/gui/GuiCards.kt` — `QuestionOptionCard` 按 method 渲染（select/confirm/内联输入）

## PC 端实现索引

- `agent/lib/pi-rpc.js` — 子进程 + 严格 JSONL（注意：不能用 readline，pi 的协议注释明确
  readline 会按 Unicode 分隔符错误拆帧）；stdin 关闭 = pi 自杀，桥接时保持管道常开
- `agent/lib/rpc-session.js` — 会话对象；`kind:"rpc"`
- `agent/lib/session-manager.js` — `preset.type === "rpc"` 分支；`agent_command` 路由；attach 快照
- 配置：`config.json` 顶层 `piDir` 指向 pi-node 运行时目录（服务里显式写绝对路径，
  LocalSystem 的 LOCALAPPDATA 与桌面用户不同）+ 预设 `{id:"pi-gui", type:"rpc"}`

## 已验证

`agent/test/rpc-session.js`（SessionManager 级）、`tools/pi-rpc-probe.js`（协议冒烟）、
`tools/e2e-rpc.js`（经生产中继对 LocalSystem 服务全链路，2026-09-12 通过）。
