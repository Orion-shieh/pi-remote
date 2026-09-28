# Pi Agent 原理、架构与执行流程

> 版本参考：`@earendil-works/pi-coding-agent` 0.87.x
> 来源：Pi 官方文档（`how-pi-works.md` / `sdk.md` / `extensions.md` / `sessions.md` 等）整理。

## 1. 一句话概括

Pi 是一个运行在终端里的**可扩展 AI 编码代理**。它把一个目标（goal）+ 一个工作目录（cwd）交给大模型，由模型通过**工具调用循环（agent loop）**去读文件、跑命令、改内容，并把整个过程作为一个可分支、可压缩的**会话（session）**持久化下来。

它本身不"思考"，而是做四件事的协调器：

1. 组装模型请求（context / system prompt / tools）
2. 调用模型 provider 并流式接收响应
3. 执行模型请求的工具调用，把结果写回会话
4. 在需要时继续下一轮（turn），或结束本次 run

---

## 2. 基本原理

### 2.1 最小内核：LLM + 工具循环

Pi 的核心是一个由模型驱动的循环：

```
用户消息 → 组装请求 → 模型流式响应（文本 + 工具调用）
        → 执行工具 → 记录结果 → 是否还需要下一轮？
             ├─ 是 → 再发起一次模型请求（新的 turn）
             └─ 否 → 本次 run 结束
```

- **turn（轮次）**：一次模型响应 + 其触发的全部工具调用与结果。
- **run（运行）**：从一次输入开始，直到没有工具结果或排队消息需要继续为止的一整段自动工作。
- 模型能"多步工作"的本质，就是工具结果被追加回上下文后再次请求模型。

### 2.2 会话是一棵树

Pi 把一次对话存成**树结构**，每个条目带 `id` 和 `parentId`：

- 每个节点是一条消息 / 事件 / 工具结果 / 模型切换 / 压缩记录。
- 从根到某个条目的路径叫一个 **branch（分支）**。
- 以"当前条目（leaf）"结尾的分支是**活动分支**，它为下一次模型请求提供历史。

因此回退到早期节点不会删除已走过的分支——那只是切换了 leaf。这也是 `/tree`、`/fork`、`/clone` 的基础。

### 2.3 上下文是"重建"出来的，不是保存的

模型请求的上下文不是文件里存的原始记录，而是 Pi 每次按活动分支**重建**出来的：

```
下一次模型请求上下文
= 系统提示词（base 指令 + 发现的 context 文件 + 技能描述）
+ 活动分支转换出的 user / assistant / tool-result 消息
+ 工具定义
+ 模型设置（模型、思考等级等）
```

其中：

- **压缩（compaction）** 会插入一条 summary 条目，在后续请求中"替代"较老的消息，但**原始条目仍保留在树里**。
- **分支摘要（branch summary）** 可在离开某分支时生成摘要，挂到进入的分支上。
- 完整技能（skill）指令是**按需加载**的，平时只暴露描述。

### 2.4 分层包结构

Pi 由一组可独立复用的 npm 包组成（扩展与技能可直接依赖）：

| 包 | 职责 |
|---|---|
| `@earendil-works/pi-ai` | 面向 provider 的消息、内容块、模型抽象 |
| `@earendil-works/pi-agent-core` | 底层 agent 运行时与消息循环 |
| `@earendil-works/pi-coding-agent` | 编码代理：工具、会话、资源、CLI、SDK |
| `@earendil-works/pi-tui` | 终端 UI 组件 |
| `typebox` | 工具参数 Schema |

---

## 3. 核心架构

### 3.1 组件总览

```
┌─────────────────────────────────────────────────────────────┐
│                         接口层 Interfaces                      │
│  Interactive(TUI) │ Print │ JSON │ RPC │ TypeScript SDK       │
└───────────────┬─────────────────────────────────────────────┘
                │ 都复用同一套 agent + session 机制
┌───────────────▼─────────────────────────────────────────────┐
│                        AgentSession                          │
│  一次对话的所有权：模型、工具、排队消息、压缩状态、扩展运行时     │
└───┬───────────┬───────────┬───────────────┬─────────────────┘
    │           │           │               │
┌───▼───┐  ┌────▼────┐ ┌────▼─────┐  ┌──────▼──────────┐
│ Model │  │ Session │ │ Resource │  │  Tool Registry   │
│Runtime│  │ Manager │ │  Loader  │  │ (built-in/custom)│
└───────┘  └─────────┘ └──────────┘  └──────────────────┘
  模型访问    会话树/leaf   扩展·技能·   read/bash/edit/write…
  模型选择    压缩/分支     提示词·主题
                          ·context文件
```

### 3.2 AgentSession（会话对象）

由 `createAgentSession()` 创建，是 SDK 与内部运行的核心载体：

- **状态读取**：`session.messages`、`session.model`、`session.thinkingLevel`、`session.systemPrompt`、`session.getActiveToolNames()`。
- **输入**：`prompt()` 处理扩展命令、展开提示词模板后进入 agent；`steer()`、`followUp()`、`abort()`、`waitForIdle()`。
- **事件订阅**：`session.subscribe(cb)`，用于流式输出与生命周期观察。
- **生命周期**：`session.dispose()` 中止工作、失效扩展上下文、断开 agent、移除监听。

流式运行中再次 `prompt()` 必须显式声明是**steer（插入当前轮）** 还是 **follow-up（等当前 run 后处理）**，否则拒绝而非猜测：

- **steering**：在当前 assistant turn 及其工具调用之后进入。
- **follow-up**：在当前 run 完成所有待办之后进入。

### 3.3 SessionManager（会话存储）

- 拥有持久化（JSONL 文件）或内存中的**条目树**，并跟踪活动 leaf。
- **是"最终模型上下文"的权威来源**：重建上下文与压缩由它负责。
- `SessionManager.inMemory()` 用于不想落盘的主机。
- 分支只改 leaf，不删异分支。
- 相关运行时 `AgentSessionRuntime` 提供 `newSession()` / `switchSession()` / `fork()` / `importFromJsonl()`；替换会话后旧订阅需重新绑定。

持久化格式：JSONL，每条树条目有 `id` 并引用 `parent`；`session entries` 用 ISO 8601 时间戳，`AgentMessage` 用 Unix 毫秒时间戳。

### 3.4 ModelRuntime / 资源加载 / 设置

- **ModelProvider**：`ModelRuntime` 负责模型访问与选择，`streamSimple()` 可做 provider 无关的嵌套模型调用。凭证存于 `auth.json`。
- **ResourceLoader（`DefaultResourceLoader`）**：发现扩展、技能、提示词模板、主题、context 文件。
- **SettingsManager**：合并用户级（`~/.pi/agent/settings.json`）与项目级（`.pi/settings.json`）设置。

### 3.5 工具系统

- 每个工具定义：`name` + 面向模型的 `description` + TypeBox 参数 Schema + `execute()`。
- 结果必须含面向模型的 `content` 和用于渲染/状态重建的 `details`。
- `execute()` 抛异常 → 失败的工具结果；返回对象不会自动算错误。
- 同一 assistant 消息里的多个工具调用**可并行执行**；共享可变状态时应串行。
- 文件修改类工具应使用 `withFileMutationQueue()` 包住完整 read-modify-write。
- 大结果要截断，并告知模型去哪里读完整输出。
- 工具可通过 `pi.setActiveTools()` 动态激活；工具集变化会在下一次请求前追加到 transcript。

### 3.6 扩展机制（Extensions）

扩展是**加载进 Pi 进程内**的 TypeScript 模块，导出默认工厂 `(pi: ExtensionAPI) => void`，可注册：

| 能力 | API |
|---|---|
| 生命周期事件 | `pi.on()` |
| 模型可调用的工具 | `pi.registerTool()` |
| `/` 命令 | `pi.registerCommand()` |
| 快捷键 / CLI flag | `pi.registerShortcut()` / `pi.registerFlag()` |
| 发送消息 | `pi.sendUserMessage()` / `pi.sendMessage()` |
| 持久化非上下文数据 | `pi.appendEntry()` |
| 添加模型 provider | `pi.registerProvider()` |
| 终端渲染 | renderer + `ctx.ui` |
| 扩展间通信 | `pi.events` |

关键约束：

- 工厂内**不要**启动进程/套接字/定时器（有些调用只加载扩展不启动会话）；长生命周期资源放在 `session_start`。
- 清理要幂等，放在 `session_shutdown`。
- 扩展运行在 Pi 进程内，**拥有进程的 OS 权限**，只加载可信来源。

### 3.7 信任与权限（Trust & Permissions）

- Pi 先解析**项目信任（project trust）**，再加载项目设置与资源（`sessionDir` 例外，会在信任解析前读取）。
- context 文件的发现**不需要**项目信任。
- 工具与扩展都使用 Pi 进程的 OS 权限，**不做沙箱隔离**——因此不要对不可信仓库/文件/扩展/无人值守自动化掉以轻心。

---

## 4. 执行流程

### 4.1 一次请求的完整时序

```
输入(编辑器/模板展开)
   │
   ▼
before_agent_start  ── 可修改 prompt / systemPromptOptions / 工具集
   │
   ▼
[组装请求]  system prompt + 活动分支 + 工具定义 + 模型设置
   │
   ▼
调用 provider，流式返回 assistant（text / thinking / toolCall）
   │  message_update(text_delta…) → 实时输出
   ▼
message_end（权威完成消息，terminal stopReason）
   │
   ▼
逐个/并行执行工具调用
   │  tool_call → 可改参数或阻断
   │  tool_result → 可组合修改结果
   ▼
记录工具结果 → 完成一个 turn
   │
   ▼
turn_end ── 可追加 custom/context_edit/compaction 并 continue:true
   │
   ├─ 还有工具结果 / 排队消息 → 再次请求模型（新 turn）──┐
   │                                                   │
   └───────────────────────────────────────────────────┘
   │
   ▼
agent_end（一次低层 agent run 结束；可能仍有自动重试/排队）
   │
   ▼
agent_before_settle ── 最后可行动边界：可追加条目并请求一次续跑
   │
   ▼
agent_settled（最终，仅通知：Pi 不会再自动继续）
```

### 4.2 消息与内容块

会话消息使用统一的 `AgentMessage`：

- **角色**：`system` / `user` / `assistant` / `toolResult`，以及编码代理扩展的 `bashExecution` / `custom` / `branchSummary` / `compactionSummary`。
- **内容块**：`text`、`image`、`thinking`、`toolCall`。
- `assistant` 消息总是带 `usage`；工具做嵌套模型调用时，工具结果可带 `usage` 计入会话统计。
- 首条 system 消息声明初始 prompt 与工具集；后续 system 消息可**追加指令、替换/删除命名 section、增删工具**，按顺序回放即得当前状态；`replace: true` 建立全新基线。
- 流式中的 `assistant` 消息 `stopReason: "pending"`；`message_end` 里是终止态，且 `pending` 不会写入 JSONL。

### 4.3 生命周期事件速查

- **run 前**：`input`、`before_agent_start`（可改系统提示词/工具）。
- **模型层**：`context`（转换会话消息，不含 prompt/工具系统消息）、`context_with_system`（可拥有完整 transcript，index 0 需保留 system）、provider 事件。
- **消息层**：`message_end`（可替换最终消息，保留 role）。
- **工具层**：`tool_call`（可改输入/阻断）、`tool_result`（依次组合）、`user_bash`（直接 shell 命令）。
- **边界**：`turn_end`、`agent_before_settle`（唯一可请求续跑的行动边界，注意无条件续跑会死循环）、`agent_settled`（只通知）。
- **其他**：`cache_warming_decision`、`session_start`、`session_shutdown`、`project_trust`（仅个人与显式命令行扩展可参与）。

事件处理按扩展加载/注册顺序执行；`pi.on()` 返回退订函数；某些事件是通知、某些会转换数据或取消操作——以各事件的声明返回类型为准。

### 4.4 上下文压缩触发

- footer 显示当前上下文用量；接近模型上限时，Pi 通常**自动压缩**较老历史，保留近期消息。
- `/compact` 手动压缩，可携带指令指定需保留的主题/决策。
- 压缩失败（provider 不可用或拒绝摘要请求）可稍后重试；**关闭自动压缩不影响手动命令**。
- 压缩只"插入摘要条目"，**不删除**原始会话条目。

---

## 5. 接口模式

| 模式 | 行为 |
|---|---|
| Interactive (TUI) | 在终端渲染会话与 agent 事件，完整交互 UI |
| Print | 跑一个 prompt，写出最终响应（脚本化一次性任务） |
| JSON | 以 JSONL 写出 agent 事件（结构化消费单次运行） |
| RPC | stdin 收 JSONL 命令，stdout 写响应与事件（控制独立 Pi 进程） |
| SDK | 在 Node/Bun 进程内直接创建并控制 `AgentSession` |

所有接口复用同一套 agent 与 session 机制。

### 5.1 SDK 最小示例

```typescript
import { createAgentSession } from "@earendil-works/pi-coding-agent";

const { session } = await createAgentSession();

try {
  session.subscribe((event) => {
    if (event.type === "message_update" && event.assistantMessageEvent.type === "text_delta") {
      process.stdout.write(event.assistantMessageEvent.delta);
    }
  });

  await session.prompt("What files are in the current directory?");
  console.log(session.getLastAssistantText());
} finally {
  session.dispose(); // 释放并清理
}
```

> `prompt()` 在 run 结束时 resolve（含自动重试）。`agent_end` 表示一次低层 run 结束，但可能还有自动恢复/排队工作；需要"Pi 不会再自动继续"时用 `agent_settled`。

### 5.2 显式配置边界

不传参时工厂自动创建 `ModelRuntime`、文件型 `SettingsManager`、持久化 `SessionManager`、`DefaultResourceLoader` 和默认工具。每个边界都可显式替换：

- `modelRuntime` / `model` / `thinkingLevel` / `scopedModels`：模型访问与选择
- `settingsManager`：合并设置或内存设置
- `sessionManager`：持久化或内存历史
- `resourceLoader`：扩展/技能/模板/主题/context 文件
- `tools` / `noTools` / `excludeTools` / `customTools`：活动工具集

---

## 6. 配置与资源位置

**Agent 目录**（默认 `~/.pi/agent`，可用 `PI_CODING_AGENT_DIR` 覆盖）：

| 路径 | 职责 |
|---|---|
| `settings.json` | 用户级设置、资源路径、包声明 |
| `keybindings.json` | 键位绑定 |
| `models.json` | 兼容端点、模型与覆盖 |
| `auth.json` | API key 与 OAuth 凭证 |
| `AGENTS.md` / `CLAUDE.md` | 跨工作目录的用户指令 |
| `SYSTEM.md` / `APPEND_SYSTEM.md` | 替换 / 追加系统提示词 |
| `extensions/` `skills/` `prompts/` `themes/` | 用户扩展、技能、提示词模板、主题 |

**项目 `.pi/`**：`settings.json`、`SYSTEM.md`、`APPEND_SYSTEM.md`、`extensions/`、`skills/`、`prompts/`、`themes/`。其中 `SYSTEM.md` / `APPEND_SYSTEM.md` 以受信任项目文件优先，同名文件不合并。

**Context 文件**（`AGENTS.md` / `CLAUDE.md`）从 agent 目录、工作目录及父目录加载，适用于其目录及其子目录；`AGENTS.override.md` 仅在同目录替换，不抑制其它目录的 context 文件。

**Pi 包（Packages）** 通过 npm / git / 本地路径把扩展、技能、提示词、主题作为一个整体分发：`pi install`、`pi list`、`pi remove`、`pi update --extensions`，也可用 `-e` 临时加载。

---

## 7. 记忆要点

1. **内核是工具循环**：模型响应 → 执行工具 → 结果回灌 → 需要就再来一轮。
2. **会话是树**：活动分支 = 下一次模型请求的历史；分支切换只改 leaf。
3. **上下文是重建的**：系统提示词 + 活动分支 + 工具定义，压缩只插入摘要不删原文。
4. **一切接口共用同一 agent/session**：TUI / Print / JSON / RPC / SDK。
5. **扩展在进程内运行、拥有 OS 权限**，项目信任只控制加载哪些资源，不提供沙箱。

## 参考

- `docs/how-pi-works.md`、`docs/sdk.md`、`docs/extensions.md`、`docs/sessions.md`
- `docs/configuration.md`、`docs/message-types.md`、`docs/packages.md`、`docs/rpc.md`、`docs/security.md`
