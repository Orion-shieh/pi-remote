# Pi Remote Terminal —— 项目交接文档

> 写给接手的人（或新开的 AI 会话）。目标是不看历史对话也能立刻上手。
>
> 最后更新：2026-09-11

---

## 1. 这是什么

在安卓手机上远程控制家里电脑的终端（PowerShell），主要用途是**在外面也能用 pi agent**。

```
[Android App]  ──wss──>  [中继 Relay]  <──wss──  [PC Agent]
  Kotlin+Compose           阿里云 ECS             Windows 服务
  自绘终端                  Debian 12             node-pty
                                                  └─ ConPTY → pwsh → pi
```

**核心设计**：PC Agent 主动向中继建立**出站**连接，所以电脑在 NAT 后面也无需端口转发。

---

## 2. 当前状态总览

| 部分 | 状态 | 说明 |
|---|---|---|
| 中继服务器 | ✅ 运行中 | systemd 守护，~25 天 uptime，443/8443 双端口 |
| PC Agent | ✅ 运行中 | Windows 服务 `PiRemoteAgent`，开机自启 |
| 安卓 App | ✅ 可用 | 已能在真机完整跑通：连接→建会话→输入→渲染 |
| 端到端 | ✅ 打通 | 见 §7 验证记录 |
| 未解决问题 | ⚠️ 1 个 | pi 长对话呼出键盘会跳（见 §9） |

**线上状态自查**：

```bash
curl -sk https://8.138.112.73/healthz
# {"ok":true,"uptime":...,"agents":["home-pc"],"clientAgentId":...,"sessions":N}
```

```powershell
Get-Service PiRemoteAgent
Get-Content 'D:\Program\Pi_Agent\logs\agent.log' -Tail 20
```

---

## 3. 代码结构

**根目录 `D:\Program\Pi_Agent`**（⚠️ **不是 git 仓库**，没有任何版本控制）

```
Pi_Agent/
├── PLAN.md                    ★ 主文档：决策记录、协议、里程碑、所有踩过的坑
├── HANDOFF.md                 ← 本文件
├── .secrets/relay.json        ★ 全部凭据（已 gitignore）
├── docs/
│   ├── android-build.md       Android 构建/调试/模拟器运行手册
│   └── terminal-requirements.md  从真实 pi 输出反推的终端需求
├── relay/                     中继服务（部署到服务器）
├── agent/                     PC 端 Agent（装成 Windows 服务）
├── android/                   Android Studio 工程
├── tools/                     开发期验证脚本
└── logs/                      agent 日志（nssm 轮转）
```

### 3.1 `relay/` —— 中继（Node.js，615 行）

部署在服务器 `/opt/pi-relay`，配置在 `/etc/pi-relay/`。

| 文件 | 职责 |
|---|---|
| `server.js` | 双端口 wss 监听、路由表、心跳、健康检查 |
| `auth.js` | HMAC-SHA256 握手校验 + nonce 防重放 |
| `protocol.js` | 二进制帧格式常量（**三端共享的真相来源**） |
| `pi-relay.service` | systemd unit（非 root 运行，`CAP_NET_BIND_SERVICE`） |
| `deploy.sh` | 一键部署：装 Node → 生成自签证书 → 装 systemd → 启动 |
| `test/smoke.js` | 本地端到端冒烟测试（10 项） |

**关键状态**：

```js
agents: Map<agentId, ws>          // Agent 重连则覆盖旧连接
sessionOwner: Map<sid, agentId>   // 会话归属，用于跨会话路由
client: ws | null                 // 单设备独占，新连接踢旧的
```

### 3.2 `agent/` —— PC Agent（Node.js，1365 行）

| 文件 | 职责 |
|---|---|
| `server.js` | 入口：加载配置、解析 shell、装配 |
| `lib/relay-client.js` | WebSocket 客户端：**证书指纹绑定**、HMAC 握手、指数退避重连、端点轮换 |
| `lib/session.js` | 单个 PTY 会话：node-pty 封装、环形缓冲、init 输入、杀进程树 |
| `lib/session-manager.js` | 会话表、预设、控制消息分发、退出会话保留与清理 |
| `lib/ringbuffer.js` | 有界回放缓冲（5000 行 / 2MB 双阈值） |
| `lib/shell.js` | 动态解析 pwsh 路径（MSI → WindowsApps 扫描 → 注册表 → 5.1 兜底） |
| `lib/protocol.js` | 一行 shim，转发到 `relay/protocol.js` |
| `config.json` | 实际配置（**含 token，已 gitignore**） |
| `config.example.json` | 配置模板 |
| `install-service.cmd` / `.ps1` | 自提权安装为 Windows 服务 |
| `uninstall-service.cmd` | 卸载 |
| `test/session-limit.js` | 会话上限回归测试 |
| `test/kill-tree.js` | 进程树回收测试 |
| `test/pi-check.js` | 启动 pi 并捕获原始字节流，产出 `pi-capture.bin` |

### 3.3 `android/` —— 三个 Gradle 模块

```
android/
├── terminal-emulator/   纯 JVM（无 Android 依赖）→ 145 项测试不需设备
├── terminal-view/       Android Library：TerminalView 自绘 + 主题
└── app/                 Compose UI + OkHttp 网络层 + 前台服务
```

#### `terminal-emulator/` —— **Termux 移植**（11 个 Java 文件）

⚠️ **GPLv3**（许可证在模块内 `LICENSE-GPLv3.md`）。链接它的整个 App 都受 GPLv3 约束。

来源：https://github.com/termux/termux-app 的 `terminal-emulator` 模块。

移植时做的剥离（为了让模块保持纯 JVM）：

| 原依赖 | 替代 |
|---|---|
| `android.util.Log` | stderr 回退（`Logger`） |
| `android.graphics.Color` | 位运算（`TerminalColors`） |
| `android.util.Base64` | `java.util.Base64` |
| `android.view.KeyEvent.KEYCODE_*` | `KeyEventCodes`（从 SDK 源码抽取的常量） |

未移植 `TerminalSession` / `JNI` / 原生构建（PTY 在 PC 端）。`TerminalSessionClient` 相应裁剪。

**为什么不自己写**：早期自研版本有两个结构性缺陷，打补丁修不好——
① 没有 reflow（宽度变化时折行文本被截断）② resize 无条件重置滚动区域。

#### `terminal-view/`

| 文件 | 职责 |
|---|---|
| `TerminalView.kt` | 自绘 View：网格布局、run 合并绘制、光标、滚动（像素级 + 惯性 + 滚动条）、IME 接入、按键编码 |
| `TerminalTheme.kt` | 259 色调色板（0-255 ANSI + FG/BG/光标三个特殊槽位） |

#### `app/`

| 文件 | 职责 |
|---|---|
| `PiRemoteApp.kt` | Application，持有 `SessionRepository` 单例 |
| `MainActivity.kt` | `enableEdgeToEdge()` + 启动前台服务 |
| `data/Protocol.kt` | 二进制帧编解码（ByteBuffer） |
| `data/ControlMessage.kt` | JSON 控制消息的类型化封装 + 请求构造器 |
| `data/RelayClient.kt` | OkHttp WebSocket + **指纹绑定 TrustManager** + 握手 + 重连 |
| `data/SessionRepository.kt` | 单例：连接状态、会话表、事件通道、全部命令入口 |
| `data/SessionHistory.kt` | 会话历史本地持久化（运行中 / 已中断） |
| `data/SettingsStore.kt` | SharedPreferences（端点、token、指纹、设备 ID） |
| `service/TerminalService.kt` | 前台服务，保活连接 + 通知显示状态 |
| `ui/App.kt` | 三态导航 + `BackHandler` |
| `ui/Screens.kt` | 设置页 + 会话列表页（运行中 / 已中断分区） |
| `ui/TerminalScreen.kt` | 终端页 + 底部快捷键栏 |
| `ui/Theme.kt` | Compose 主题（恒为深色） |

---

## 4. 通信协议

### 4.1 传输

单个 WebSocket。**文本帧 = JSON 控制信令**，**二进制帧 = 终端字节流**。

### 4.2 二进制帧（22 字节固定头）

```
offset  size  field
0       1     type     0x01=stdout 0x02=stdin 0x03=replay_done
1       1     flags    保留
2       4     seq      uint32 BE，每 chunk 递增（非每字节）
6       16    sid      会话 UUID
22      ...   payload  原始终端字节流
```

`seq` 用于断线续传定位。App 记录收到过的最大 seq，重连时上报。

### 4.3 控制消息

**Agent → App**：`hello_ok` `settings` `sessions` `created` `exit` `agent_online` `agent_offline` `replay_gap` `error`

**App → Agent**：`hello` `list` `create` `attach` `detach` `kill` `resize`

**中继 → Agent（中继自己发的）**：`client_online` `client_offline`

### 4.4 鉴权

- 静态密钥对：`agentToken` / `deviceToken`
- 握手带 `HMAC-SHA256(token, "${role}|${id}|${ts}|${nonce}")`
- 中继校验时间窗 ±30s + nonce 防重放
- 失败即断连（close code 4403），不做任何提示

### 4.5 断线续传（核心功能）

```
1. Agent 为每个会话维护环形缓冲，每 chunk 分配递增 seq
2. App 断开 → Agent 仅 detach，PTY 进程继续跑
3. App 重连 → attach{sid, lastSeq}
4. Agent 回放 (lastSeq, current] 的所有 chunk
5. 发 replay_done → 切实时流
6. 若 lastSeq 太旧（已被裁剪）→ 先发 replay_gap，App 清屏
```

回放与实时流的顺序安全由**同步执行**保证：assemble 快照和切换实时在同一 tick 内完成，事件无法插入。见 `session-manager.js` 的 `attach()`。

---

## 5. 部署与运维

### 5.1 服务器（中继）

```
主机        8.138.112.73   （华南3 / 广州，Debian 12，包年包月 IP 固定）
监听        wss://8.138.112.73:443/relay    （主）
            wss://8.138.112.73:8443/relay   （备）
健康检查    https://8.138.112.73/healthz
代码        /opt/pi-relay     配置 /etc/pi-relay/
服务        pi-relay.service  （enabled，Restart=always，非 root 运行）
TLS         TLS 1.3，自签证书，有效期至 2036-09-07
```

**阿里云安全组**已放行 443 与 8443（入方向）。服务器本机**无** iptables/ufw。

**为什么双端口**：主用 443（出站最友好），8443 作为规避端口级干扰的备选；客户端配置里两个都放，按顺序探测。

**为什么不备案**：无域名，走 IP + 自签证书 + **指纹绑定**。这也是为什么证书指纹填错会直接连不上——那是唯一信任锚。

改代码后重新部署：

```bash
# 本地
cd D:/Program/Pi_Agent
rm -rf .stage && mkdir -p .stage
cp relay/server.js relay/auth.js relay/protocol.js relay/package.json \
   relay/config.example.json relay/pi-relay.service relay/deploy.sh .stage/
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' PIPASS='<服务器密码>' \
   python tools/upload.py .stage /opt/pi-relay
PIPASS='<服务器密码>' python tools/remote.py 'systemctl restart pi-relay && sleep 3 && systemctl is-active pi-relay'
```

> ⚠️ Git Bash 会把 `/opt/pi-relay` 这类参数**自动转成 Windows 路径**，必须加 `MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'`。

### 5.2 PC Agent

```
服务名      PiRemoteAgent
运行身份    LocalSystem（ORION$）
启动类型    自动
日志        D:\Program\Pi_Agent\logs\agent.log（nssm 1MB 轮转）
```

**为什么 LocalSystem**：不需要存 Windows 密码，开机即启。代价是继承 SYSTEM 环境，已在 Agent 里补偿：

| 问题 | 补偿 |
|---|---|
| `Get-AppxPackage` 是按用户的，SYSTEM 查不到 pwsh | `shell.js` 直接扫 `C:\Program Files\WindowsApps\Microsoft.PowerShell_*`，与账号无关 |
| SYSTEM 的 `USERPROFILE` 指向 systemprofile，pi 找不到配置 | 配置里 `shellEnv` + `pathPrepend` 显式注入用户 profile 变量 |

**已知限制**：pi 若调 `git push`，DPAPI 加密的凭据在 LocalSystem 下解不开。要 push 需改用 Orion 账号：
`nssm set PiRemoteAgent ObjectName .\Orion <密码>`

重装服务：双击 `agent/install-service.cmd`（会弹 UAC）。

### 5.3 凭据位置

**全部在 `D:\Program\Pi_Agent\.secrets\relay.json`**：

```json
{
  "host": "8.138.112.73",
  "endpoints": ["wss://8.138.112.73:443/relay", "wss://8.138.112.73:8443/relay"],
  "agentId": "home-pc",
  "agentToken": "...",        // PC Agent 用
  "deviceToken": "...",       // Android App 用
  "pinnedSha256": "86:CB:...", // 证书指纹，两端都要
  "certNotAfter": "2036-09-07"
}
```

App 首次使用需在设置页填三项：**中继地址、设备 Token、证书指纹**。

---

## 6. 构建、测试、运行

### 6.1 Android

环境已固化，**别改**（国内网络约束）：

| 项 | 值 |
|---|---|
| JDK | `C:\Program Files\Android\Android Studio\jbr`（OpenJDK 21） |
| SDK | `C:\Users\Orion\AppData\Local\Android\Sdk` |
| Gradle | 8.9（wrapper 指向**腾讯云镜像**，官方源在国内不可达） |
| 仓库 | `settings.gradle.kts` 里**阿里云镜像排在 `google()` 之前** |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| compileSdk / minSdk | 35 / 26 |

```bash
cd D:/Program/Pi_Agent/android
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

./gradlew :terminal-emulator:test      # 145 项，纯 JVM，秒级，不需要设备
./gradlew :app:assembleDebug           # 产出 app/build/outputs/apk/debug/
./gradlew clean :app:assembleDebug     # 彻底重建
```

详细调试手册（模拟器启动、预置凭据、如何验证渲染）见 **`docs/android-build.md`**。

> `TerminalView` 是自绘 View，**不进无障碍树**，所以 `uiautomator dump` 看不到终端内容。验证渲染只能靠截图做像素分析。

### 6.2 PC Agent

```bash
cd D:/Program/Pi_Agent
node tools/make-agent-config.js                    # 从 .secrets 生成 agent/config.json
node agent/server.js                               # 前台跑（调试用）
node agent/test/session-limit.js                   # 会话上限回归
node agent/test/kill-tree.js                       # 进程树回收
node agent/test/pi-check.js 20                     # 捕获 pi 输出 → pi-capture.bin
```

> nssm 管着的服务会占住 relay 的 agent 槽位。**调试时先停服务**，否则两个 agent 会互相踢：
> `Stop-Service PiRemoteAgent`（需提权）

### 6.3 跨端验证脚本（`tools/`）

| 脚本 | 用途 |
|---|---|
| `e2e.js` | 中继端到端验收（8 项）：跨端口路由、鉴权、二进制帧完整性 |
| `m1-test.js` | **完整集成测试（16 项）**：建会话、输入、resize、回放、pi TUI 渲染 |
| `detach-leak-test.js` | detach 期零字节泄漏，25 轮循环 |
| `probe.js` | 单连接握手探测 |
| `remote.py` | 通过 SSH 在服务器执行命令（密码走 `PIPASS` 环境变量） |
| `upload.py` | SFTP 上传（远程目录用 `mkdir -p` 创建） |
| `make-agent-config.js` | 合并 `.secrets` + 模板 → `agent/config.json` |

```bash
# 对着已运行的服务跑集成测试（不新起 agent）
PI_AGENT_EXTERNAL=1 node tools/m1-test.js
```

---

## 7. 验证记录（已实测通过）

- **中继**（`tools/e2e.js`，8 项）：agent 连 443 / client 连 8443 跨端口路由；错误 token 与 nonce 重放拒绝（4403）；控制信令与二进制帧双向转发，sid/seq/payload 逐字节保留
- **Agent**（`tools/m1-test.js`，16 项）：会话创建、实时输出、stdin 路由、ConPTY resize、detach 期零字节泄漏、重连回放无丢数据、kill 终止、**pi TUI 远程渲染**
- **终端模拟器**（`android/terminal-emulator`，145 项）：Termux 原测试套件全部通过
- **Android 真机**（MEIZU 21 Note / API 36）：TLS 指纹绑定握手 → 创建 pi 会话 → attach 回放 → 软键盘输入 → **屏幕上渲染出可读的 "hello" 字形**
- **服务韧性**：中继 SIGKILL 后 3 秒自拉；PC 服务 LocalSystem 下完整驱动 pi

---

## 8. 踩过的坑（务必先看，避免重复）

这些是**实际发生过**的问题，不是理论风险。详细分析在 `PLAN.md` §0.1/§0.2。

### 8.1 终端模拟器

| 问题 | 本质 |
|---|---|
| **`CSI > u` 被当成恢复光标** | `ESC[>7u`（Kitty 键盘 push）落到无前缀分支，`u` 被读作 DECRC → 光标瞬回 (0,0)。**pi 启动就发这个**，导致整个 TUI 布局错位且不报错 |
| 索引色与真彩色无法区分 | 都打包进一个 Int，调色板索引 5 和真彩色 `0x000005` 数值相同 |
| **Kitty 键盘协议查询不能响应** | 响应 `CSI ? u` 会让 pi 切到扩展按键编码，方向键和 Ctrl 组合键全失效 |
| 从不响应 DSR | 有些程序会等设备状态报告等到超时 |
| **字符错位（光标与文字对不上）** | 格子宽度用 `ceil(measureText("M"))` 而文字按字体自然 advance 绘制，每字符差 ~0.7px，60 列后累计到一个多字符宽 |
| 粗体导致错位 | 独立 BOLD typeface 的 advance 与常规体不同 |

### 8.2 Windows / PTY

| 问题 | 本质 |
|---|---|
| **`pty.kill()` 依赖控制台** | 无控制台时 `AttachConsole failed`，且只杀 shell 不杀孙进程 → 改用 `taskkill /PID x /T /F` |
| taskkill 非零退出码被误判 | 杀深层进程树常报部分失败，导致每次都白走一遍 `pty.kill()` |
| 固定延迟发初始命令不可靠 | pwsh 提示符 ~1.1s 才出现，700ms 发出的 `pi` 会被丢弃 → 改为等输出静默 |
| MSIX 版 pwsh 的路径含版本号 | 升级后路径变，写死会坏 → 启动时动态解析 |

### 8.3 安卓 / Compose

| 问题 | 本质 |
|---|---|
| **双击 `connect()` 使状态卡死** | 前台服务与 Compose 各调一次，第二次把状态重置为 Connecting，而 `start()` 已运行直接返回 |
| **回放数据被丢弃** | `MutableSharedFlow` 无订阅者时丢事件；attach 回放常早于 Compose 开始订阅 → 换 `Channel(UNLIMITED)` |
| **键盘遮住底栏与终端** | `targetSdk 35` 在 Android 15+ 强制 edge-to-edge，`adjustResize` 失效；未调 `enableEdgeToEdge()` 时 Compose 收不到 IME inset |
| **输入法英文打不进去（搜狗，中文正常）** | 只重写 `commitText`。Gboard 每字母走 `commitText`；搜狗把英文当**组合文本**走 `setComposingText`，全程不触发 `commitText` |
| **返回键失效** | `KeyHandler` 把 `KEYCODE_BACK` 编码成 ESC，被按键处理吃掉，传不到 Compose 的 `BackHandler` |
| **惯性方向反了** | `OverScroller.fling` 范围参数放错位置（放到了 `minX/maxX`），Y 范围变成 `[0,0]`，滚动器把视口夹回底部 |
| **回看全是当前屏幕的重复** | scrollback 按**引用**存行对象，紧接着 `scrollUp` 用 `copyFrom` 原地改写同一批对象 |
| 状态栏图标看不见 | `enableEdgeToEdge()` 未指定 bar style，跟随系统亮暗；深色背景下配深色图标 |
| **一打开终端就闪退** | 为实现平滑滚动在窗口上方多画一行，但 `externalToInternalRow` 只接受 `[-activeTranscriptRows, mRows)`，没有历史时 `-1` 抛异常 |

### 8.4 中继 / 集成

| 问题 | 本质 |
|---|---|
| **终止会话后名额不释放** | `create()` 用 `sessions.size` 判上限，而退出会话为「重连能看最后输出」保留 10 分钟，一直占名额 |
| 状态广播时机错 | Agent 连上就广播 settings/sessions，那时 client 还不存在，消息被丢 → 中继加 `client_online` |
| **Git Bash 路径转换** | `/opt/pi-relay`、`/sdcard/x` 等参数被自动转成 Windows 路径，在服务器/设备上创建垃圾目录 → 所有 adb/ssh/sftp 调用都要加 `MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` |

---

## 9. 未决问题

### 9.1 ⚠️ pi 长对话呼出键盘时画面跳转（**未解决，改动已回退**）

**现象**：只有在 **pi agent + 文本超出屏幕**时，呼出输入法会让画面先跳到文本中间、再快速滑下来。其它程序（PowerShell）只是闪一下。

**当前状态**：**已回退到 Termux 移植刚完成时的状态**（即 120ms 尺寸防抖仍在）。问题会复现。

**已尝试且失败的两条路**：

1. 「立即 resize 本地 + 只防抖网络通知」→ 未解决，反而增加闪烁
2. 「本地与网络通知都立即」→ 未解决，闪烁更明显

**下一步线索**（尚未验证）：

- pi 是唯一会**按终端尺寸整体重绘**的程序，这解释了为什么只有它明显
- pi 运行时终端处于 **alt screen**（备用屏）。`TerminalBuffer.resize` 里有 `mActiveTranscriptRows = altScreen ? 0 : ...` 的分支——**备用屏下行为不同，这条分支两次修复都没碰过**
- 建议先加日志把每帧的 `(cols, rows, 视图高度, altScreen)` 打出来，确认是尺寸抖动、alt screen 分支、还是别的机制

**相关代码**：`TerminalView.applyViewSize()` / `commitSize()` / `onDraw()`，`TerminalBuffer.resize()`。

### 9.2 其它待办

- **没有版本控制**。项目至今不是 git 仓库，强烈建议 `git init` 并提交（注意 `.gitignore` 已备好）
- **实际使用强度不足**。真机只做过功能验证，没长时间跑过；息屏/切网/弱网下的稳定性未验证
- **中继单点**。服务器挂掉全断（systemd 会自拉）。电脑上有 Tailscale，可作为应急兜底通道，未集成
- **`m1-test.js` 的偶发**。detach 断言出现过 1 次未复现失败（约 1/15），已加帧级诊断但未复现
- **scrollback 上限 5000 行**。日志里见到过 `gap=true`（回放缓冲被裁剪），如果实际使用常超，需调大或做成可配置
- **未做**：蓝牙键盘验证、长按选区复制、浅色主题、多设备同时观看同一会话

### 9.3 遗留的技术债

- `TerminalView` 里 `sendSequence` / `sendKeyCode` / `emitCodePoint` 三条发送路径，逻辑有重叠，可以合并
- `SessionRepository` 已涨到 ~350 行，职责偏多（连接、会话表、历史、命令），可拆分

---

## 10. 环境与网络约束

**本机**：Windows 11，内网 `192.168.31.62`，网关 `192.168.31.1`
- Node `v22.23.2`（在 `C:\Users\Orion\AppData\Local\pi-node\current\node.exe`）
- pi 全局安装：同一目录，preset 里通过 `pathPrepend` 找到
- pwsh `7.6.6`（**MSIX/Store 版**，真实路径在 `C:\Program Files\WindowsApps\...`）
- Tailscale 已装并运行（`100.75.96.84`），本方案未使用
- WSL2 已装（Ubuntu）

**国内网络约束**（已固化到工程，改动会破坏构建）：
- `services.gradle.org` 不可达 → wrapper 指向腾讯云镜像
- `dl.google.com` 不稳定 → Gradle 仓库优先走阿里云镜像
- Gradle 缓存里有 8.7 / 8.9 / 9.5.0 三个发行版，AGP 8.5.2 / 8.7.3 / 9.3.0

**服务器**：Debian 12 bookworm x86_64，1.6G 内存 / 40G 磁盘，纯净系统（无 Docker）

---

## 11. 快速上手清单

新会话接手时建议按这个顺序：

1. 读 `PLAN.md`（主文档，636 行，含全部决策依据）
2. 读本文件 §8（踩过的坑）——**能省掉几小时的重复排查**
3. 确认线上状态：`curl -sk https://8.138.112.73/healthz` + `Get-Service PiRemoteAgent`
4. 改安卓前先跑一次 `./gradlew :terminal-emulator:test` 确认基线是绿的（145 项）
5. 改完用 `PI_AGENT_EXTERNAL=1 node tools/m1-test.js` 做端到端回归
6. 涉及服务器操作记得加 `MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'`

**如果要继续查 §9.1 那个问题**：先在 `TerminalView.onDraw()` 和 `applyViewSize()` 里加日志，把尺寸变化序列和 alt screen 状态打出来，用真机复现一次，再决定方向——前两次都是凭推理改的，都没成。
