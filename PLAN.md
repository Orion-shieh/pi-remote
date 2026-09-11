# Pi Remote Terminal —— 项目规划

在手机上远程控制家里电脑的 PowerShell / pi agent 会话。

> 本文档为规划基线。所有已决策项均标注为 **【已定】**，待定项标注为 **【待定】**。

## 0. 实施进度

| 阶段 | 状态 | 说明 |
|---|---|---|
| **M0** 服务器基建 | ✅ 完成 | Node 22.23.2、自签证书、systemd 服务、安全组放行均就绪 |
| **M1** PC Agent 本地 | ✅ 完成 | PTY 创建、输入、resize、回放、断线续传全部实测通过；`pi` TUI 可远程驱动 |
| **M2** 中继上线 + 远端接入 | ✅ 完成 | 公网可达，agent/client 握手与双向转发全部实测通过 |
| **M3** App + 终端模拟器 | ✅ 完成 | 三个模块均构建通过；已在模拟器上验证完整的端到端输入输出闭环 |
| **M4** 保活 / 断线续传 | ✅ 完成 | detach/attach + 环形回放已验证；Android 端按 `lastSeq` 续传；重连退避已实现 |
| **M5** 服务化 + 打磨 | ✅ 完成 | LocalSystem 服务已安装并终检通过（`PI_AGENT_EXTERNAL=1 node tools/m1-test.js` 16/16） |
| **M6** 真机打磨 | ⬜ 待做 | 蓝牙键盘、长按选区、深色/浅色主题、息屏长时间稳定性尚未验证 |

**已部署的中继**

```
主机        8.138.112.73   (华南3 / 广州, Debian 12, 包年包月 IP 固定)
监听        wss://8.138.112.73:443/relay   (主)
            wss://8.138.112.73:8443/relay  (备)
健康检查    https://8.138.112.73/healthz
证书指纹    86:CB:38:44:92:95:32:82:5E:B0:75:BA:A0:92:28:78:5B:DA:5A:05:06:50:6C:EE:A2:31:92:8D:6F:BE:D8:CD
证书有效期  至 2036-09-07
TLS         1.3
服务单元    pi-relay.service (enabled, Restart=always, 非 root 运行)
代码位置    服务器 /opt/pi-relay  配置 /etc/pi-relay/
```

**凭据存放**：`D:/Program/Pi_Agent/.secrets/relay.json`（已加入 `.gitignore`，不要提交）

**已实测验证**

- 中继（`tools/e2e.js`）：agent 连 443 / client 连 8443 跨端口路由；握手签名校验；错误 token 与 nonce 重放拒绝（4403）；控制信令与二进制帧双向转发，sid/seq/payload 逐字节保留；agent 上下线通知
- Agent（`tools/m1-test.js`，16 项）：会话创建、实时输出、stdin 路由、ConPTY resize、detach 期零字节泄漏、重连回放无丢数据、seq 0 全量回放、kill 终止、**pi TUI 远程渲染**
- 进程树回收（`agent/test/kill-tree.js`）：无控制台环境下 kill 后孙进程不残留
- 终端序列捕获（`agent/test/pi-check.js`）：pi 实际发出的 31 种 CSI 序列已归档
- 终端模拟器（`android/terminal-emulator`，38 项 JUnit）：真实 pi 捕获的回归断言、宽字符/代理对、UTF-8 跨分片、颜色编码互斥、`CSI > u` 误解析防护
- **Android 端到端（模拟器 API 36）**：TLS 证书绑定握手 → 创建 `pi` 会话 → attach 回放 → 软键盘输入 → pi 回显渲染成可读字形

---

## 0.1 M1 阶段发现的坑（已修）

| 问题 | 现象 | 处理 |
|---|---|---|
| 固定延迟发初始命令不可靠 | pwsh 提示符在 ~1.1s 才出现，700ms 发出的 `pi` 被丢弃 | 改为等输出静默后再发，带上限兜底（`agent/lib/session.js`） |
| `pty.kill()` 依赖控制台 | 无控制台时 `AttachConsole failed`，且只杀 shell 不杀孙进程 | 改用 `taskkill /PID x /T /F` |
| taskkill 非零退出码被误判 | 杀深层进程树时常因部分子进程报错，导致每次都回退到 `pty.kill()` | 只在 5s 超时未退出时才回退 |
| 状态广播时机错 | agent 连上中继时就广播 `settings`/`sessions`，那时 client 还不存在，消息被丢 | 中继在 client 接入时下发 `client_online`，agent 重发状态 |
| 本地调试路径转换 | Git Bash 把 `/opt/pi-relay` 转成 `D:/C_need/Git/opt/pi-relay`，在服务器上建出垃圾目录 | 调用 Python 时加 `MSYS_NO_PATHCONV=1` |
| 配置读取掩盖权限错误 | `fs.existsSync` 遇 EACCES 返回 false，被当成“配置缺失” | 改为区分 ENOENT / SyntaxError / 其他并抛出真实原因 |

**已知待观察项**：`tools/m1-test.js` 的 detach 断言出现过 1 次未复现的失败（约 1/15）。agent 的 detach 语义已由 `tools/detach-leak-test.js` 连续 25 轮验证无泄漏，断言已改为更强的“字节数完全不变”并保留帧级诊断，再犯时可定位。

### 0.2 M3 阶段发现的坑（已修）

| 问题 | 现象 | 处理 |
|---|---|---|
| `CSI > u` 被当成恢复光标 | `ESC[>7u`（Kitty 键盘 push）落到无前缀分支，`u` 被读作 DECRC，光标瞬回 (0,0)，pi 启动即触发，整个 TUI 布局错位且不报错 | 有前缀（除 `?`）一律不进入普通 CSI 分支 |
| 索引色与真彩色无法区分 | 都打包进一个 Int，调色板索引 5 与真彩色 `0x000005` 数值相同 | 索引色编码为 `-(index+2)`，并加单测锁死 |
| 双击 `connect()` 使状态卡死 | 前台服务与 Compose 各调一次，第二次把状态重置为 Connecting，而 `start()` 已运行直接返回 | `RelayClient.start()` 返回是否真正发起连接，`connect()` 据此决定是否改状态 |
| 回放数据被丢弃 | `MutableSharedFlow` 无订阅者时丢事件；attach 回放常早于 Compose 开始订阅 → 终端空白 | 换成 `Channel(UNLIMITED)` + `receiveAsFlow()` |
| 键盘遮住底栏与终端 | `targetSdk 35` 在 Android 15+ 强制 edge-to-edge，`adjustResize` 失效；未调 `enableEdgeToEdge()` 时 Compose 收不到 IME inset | `enableEdgeToEdge()` + `imePadding()`/`statusBarsPadding()`/`navigationBarsPadding()` |
| **向上回看全是当前屏幕的重复** | `addToScrollback` 按**引用**存行对象，紧接着 `scrollUp` 用 `copyFrom` 原地改写同一批对象，历史被自己覆盖 | 入 scrollback 时深拷贝（`TerminalRow.copy()`），并加单测断言内容而非仅断言行数 |
| 真机上呼不出输入法 | 视图已聚焦时 `requestFocus()` 是空操作；且在 `onTouchEvent` 内直接调 `showSoftInput` 部分输入法会忽略 | 封装 `showKeyboard()`：未聚焦先聚焦，`post` 到触摸分派之后再调，失败则 `restartInput` |
| 返回键直接退出应用 | 没有 `BackHandler`，任何界面按返回都 finish Activity | 加 `BackHandler` 按层级返回（终端页→会话列表，设置页→上一页） |
| 移除后刷新又出现 | `移除` 只删本地记录，而 agent 还会把退出会话上报 10 分钟，下次刷新就合并回来了 | 持久化一份“已移除 sid”清单，合并时过滤 |
| 终端下滑播不动 / 没有惯性 | 滚动按整数行跳、只跟手指，且无 fling | 改成像素级偏移（`OverScroller` + `VelocityTracker`），并修复回看时新输出会把视野顶跑 |
| 状态栏图标看不见 | `enableEdgeToEdge()` 未指定 bar style，跟随系统亮暗；深色背景下配深色图标 | 显式 `SystemBarStyle.dark(Color.TRANSPARENT)` |
| **输入法弹出时文字跳一下 / 上面的对话看不到** | 输入法动画期间 `onSizeChanged` 每帧触发，实测 200ms 内发 **7 次** resize；每次收缩都把行挤进 scrollback、重置滚动位置并命令 PC 重绘 | 尺寸等停止变化（120ms）再提交（`RESIZE_SETTLE_MS`）；实测降到 1 次 |
| 反复开关键盘会侵蚀屏幕 | 收缩把行推进 scrollback、展开只在底部加空行，不对称；每开关一次就永久少一屏内容 | `TerminalBuffer.resize` 返回位移量，展开时先把 scrollback 末尾的行拉回顶部，并把光标偏移同步给 emulator；加往返测试 |
| 切会话时残留上个会话的画面 | 视图在会话切换时复用 | `remember(sessionId)` 重建视图（在组合期间而非 `LaunchedEffect` 里，避开回放已入队的竞态） |
| **终止会话后名额不释放，刷不出新会话** | `create()` 用 `this.sessions.size` 判上限，而退出会话为了「重连能看最后输出」会保留 10 分钟，于是它们一直占着名额；攒够 8 个就报上限，要等自动清理才能再建 | 只统计 `running` 的会话；错误消息带上 `当前/上限`；加 `agent/test/session-limit.js` 回归测试（已验证用旧代码会复现） |
| **一打开终端就闪退** | 为实现平滑滚动在窗口上方多画一行，但 `externalToInternalRow` 只接受 `[-mActiveTranscriptRows, mRows)`；没有历史时 `-1` 直接抛 `IllegalArgumentException` | 按合法外部行范围迭代（`maxOf(topRow-1, -transcriptRows)` 到 `minOf(topRow+rows-1, mRows-1)`），不再假设那一行存在 |
| **搜狗输入法英文打不进去（中文正常），Gboard 正常** | 只重写了 `commitText`。Gboard 在 `TYPE_NULL` 下每个字母都走 `commitText`；而搜狗把英文当成**组合文本**，走 `setComposingText` → 一直不触发 `commitText`，文本全丢 | 改成 Termux 的做法：`BaseInputConnection(this, true)`，并在 `commitText` 与 `finishComposingText` 里**都读取并清空 editable**，两条路径归一 |
| **返回键又失效** | `KeyHandler` 把 `KEYCODE_BACK` 编码成 ESC，`handleTerminalKey` 就把它吃掉了，永远传不到 Compose 的 `BackHandler`。Termux 整个 Activity 就是终端所以故意如此，宿主式集成不适用 | `handleTerminalKey` 开头直接对 `KEYCODE_BACK` 返回 false，让它冒泡给宿主 |
| **终端字符错位（光标与文字对不上）** | 格子宽度用了 `ceil(measureText("M"))`，而文字按字体自然 advance 绘制；每字符差 ~0.7px，60 列后累计到一个多字符宽。另外粗体用独立 typeface，advance 与常规体不同 | 格子宽改为**不取整**的 `measureText("X")`；绘制时测量 run 实际宽度并用 `canvas.scale` 补偿；粗体改用 `setFakeBoldText` 保持宽度一致 |
| **长文本时呼出键盘会先跳到文本中间再滑回来** | ⚠️ **已尝试修复但已回退**。根因是 120ms 尺寸防抖期间模拟器与视图行数不一致（模拟器 45 行 / 视图 24 行），而绘制从外部行 0（屏幕顶部）开始。曾改为「立即 resize + 只防抖网络通知」，再改为「全部立即」，两次都未解决且带来闪烁 | **当前状态：保留 120ms 防抖**（即回退至 Termux 移植完成后的状态）。待重新调查 |
| **上一条只修了一半，pi 长对话仍然跳** | ⚠️ **已回退**（同上） | 回退至全量防抖 |
| Git Bash 路径转换（再次） | `adb push` 的 `/sdcard/...` 被转成 `D:/C_need/Git/sdcard/...` | 所有 adb 调用统一加 `MSYS_NO_PATHCONV=1` |

### 0.3 会话历史（本地持久化）

PC agent 只保留退出会话很短时间，重启后全部丢失，所以“之前用过什么”只能由手机记住。

`SessionHistory` 把会话表存在 SharedPreferences（`pi-remote-history`），字段：`sid / name / preset / cwd / lastSeenAt / alive`。

合并规则（`SessionRepository.publishSessions`）：

- agent 上报且在运行 → `alive = true`
- agent 上报但 `running = false`，或者从列表里消失 → `alive = false`，**保留不删**
- 应用重启后读回的历史一律置 `alive = false`，等 agent 列表到达后再重新标活
- 已中断的会话最多保留 40 条，按 `lastSeenAt` 倒序

主页因此分成两个区：**运行中的会话** 与 **已中断的会话**。后者提供三个动作：

- **查看** —— 尝试 attach。agent 已回收该会话时会回 `no_session`，界面弹提示并退回列表（不留下一个空终端）
- **重开** —— 用原 `preset` + 原 `cwd` 新建一个会话（`create` 消息新增可选 `cwd` 字段）
- **移除** —— 仅本地删除，不联网

**构建环境约束（已固化到工程）**：

- `services.gradle.org` 与 `dl.google.com` 在国内不可靠 → wrapper 指向腾讯云镜像，仓库优先走阿里云镜像
- Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / JDK 21（Android Studio JBR）/ compileSdk 35
- 详尽的构建与调试命令见 [docs/android-build.md](./docs/android-build.md)

---

## 1. 决策摘要

| 维度 | 决策 | 备注 |
|---|---|---|
| 网络接入 | 云服务器中转（自研 WebSocket 中继） | PC 主动出站，无需端口转发 |
| 中继实现 | Node.js 22 + `ws`，systemd 管理 | |
| 服务器 | 阿里云 ECS / Debian 12 bookworm / x86_64 / 中国大陆地域 | 1.6G RAM，40G 磁盘 |
| 加密 | TLS 传输加密 + token 鉴权（**不做端到端加密**） | 中继可见明文，服务器须加固 |
| 证书 | 自签证书 + 两端内置 SHA-256 指纹绑定 | 无域名 |
| 端口 | `443` 主用，`8443` 备用，客户端自动探测 | 备案限制只作用于 80/443 的域名，纯 IP 不受影响 |
| 客户端 | Kotlin + Jetpack Compose，**自研终端模拟器** | 不使用 WebView |
| PC Agent | Node.js + `node-pty`(ConPTY) | 复用 PC 上已有的 Node v22.23.2 |
| PC 服务化 | `nssm` 注册为 Windows 服务 | 开机自启 + 崩溃自拉 + 日志轮转 |
| 会话保活 | 常驻守护进程，内存保活 + 环形缓冲回放 | PC Agent 重启后会话不恢复 |
| 会话模型 | 会话菜单 + 快捷指令；**单设备独占** | 同会话新连接踢掉旧连接 |
| 安卓交付 | 仅生成 Android Studio 工程源码，由本机构建 | 不做签名 APK / 不上架 |

### 已勘察的 PC 环境

- Node `v22.23.2` / npm `10.9.8` / Python `3.13.5` / Git `2.45.1`
- OpenSSH 客户端 `9.7p1`
- pi 全局安装：`C:\Users\Orion\AppData\Local\pi-node\current\pi`
- Tailscale 已装并运行（`100.75.96.84`）—— 本方案不依赖，可作为应急兜底通道
- 内网 `192.168.31.62`，网关 `192.168.31.1`
- WSL2 已装（Ubuntu）
- `pwsh` 7.6.6 存在，为 **MSIX/Store 包**，真实路径 `C:\Program Files\WindowsApps\Microsoft.PowerShell_7.6.6.0_x64__8wekyb3d8bbwe\pwsh.exe`（实测可直接调用，ACL 对 Users/SYSTEM 均放行）
- Windows PowerShell 5.1 在标准路径 `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe`

### 已勘察的服务器环境

- Debian 12 (bookworm)，x86_64，纯净系统
- 仅监听 `22` 与 systemd-resolved（`53`/`5355`），**80/443 空闲**
- `nginx` / `docker` / `node` / `npm` / `certbot` / `socat` **均未安装**
- 磁盘 40G（用 8%），内存 1.6G（可用 1.28G）

---

## 2. 总体架构

```
┌──────────────────────────────┐
│  Android App                 │
│  Kotlin + Compose            │
│  ├ :terminal-emulator  VT解析 │
│  ├ :terminal-view      Canvas │
│  ├ :app  网络/前台服务/UI      │
│  └ 会话菜单 + 快捷指令栏        │
└──────────────┬───────────────┘
               │ wss://<IP>:443/relay   (出站)
               ▼
┌──────────────────────────────┐
│  Relay ★ 单点                 │
│  Debian 12 + Node 22 + ws     │
│  ├ TLS 终结 (自签证书)         │
│  ├ token 鉴权 + 心跳           │
│  ├ agents:   agentId → ws     │
│  ├ sessions: sid     → agentId│
│  └ 纯字节转发，不解析终端内容    │
└──────────────┬───────────────┘
               │ wss (PC 主动出站)
               ▼
┌──────────────────────────────┐
│  PC Agent (Windows 服务)      │
│  Node.js + node-pty → ConPTY  │
│  ├ 会话管理器 + 预设           │
│  ├ 环形缓冲 (5000行 / 2MB)     │
│  ├ 断线指数退避重连             │
│  └ nssm 托管 / 日志轮转         │
└──────────────────────────────┘
```

**关键性质**：PC Agent 主动向中继建立出站连接 → 电脑在 NAT 后也无需任何端口映射。

---

## 3. 通信协议

### 3.1 传输

单个 WebSocket 连接，两条逻辑通道：

- **文本帧** → JSON 控制信令
- **二进制帧** → 终端字节流

### 3.2 二进制帧格式

```
offset  size  field
0       1     type     0x01=stdout 0x02=stdin 0x03=replay_done
1       1     flags    保留
2       4     seq      uint32 BE，每个 chunk 递增（非每字节）
6       16    sid      会话 UUID
22      ...   payload  原始终端字节流
```

`seq` 用于断线续传定位：App 记录收到的最大 `seq`，重连时上报。

### 3.3 控制信令

**Agent → Relay → App**

| `t` | 载荷 |
|---|---|
| `hello` | `role:"agent"`, `agentId`, `token` |
| `sessions` | `list:[{sid,name,preset,cwd,running,pid,createdAt}]` |
| `created` | `sid`, `name` |
| `exit` | `sid`, `code` |
| `ping` | — |

**App → Relay → Agent**

| `t` | 载荷 |
|---|---|
| `hello` | `role:"client"`, `token` |
| `list` | — |
| `create` | `preset`, `cwd?` |
| `attach` | `sid`, `lastSeq` |
| `detach` | `sid`（保活，不断进程） |
| `kill` | `sid`（终结进程） |
| `resize` | `sid`, `cols`, `rows` |
| `ping` | — |

键输入走二进制帧 `type=0x02`，避免 JSON 转义开销。

### 3.4 鉴权

- 静态密钥对：`agentToken` / `deviceToken`
- 握手：`hello` 中带 `token`
- 加固：token 不裸传，改为 `HMAC-SHA256(token, nonce + timestamp)`，中继校验时间窗 ±30s，防重放
- 失败即断连，不做任何提示（避免泄露服务存在）

### 3.5 断线续传（MVP 核心）

```
1. Agent 为每个会话维护环形缓冲，每 chunk 分配递增 seq
2. App 断开 → Agent 仅 detach，pty 进程继续跑，输出继续入缓冲
3. App 重连 → attach{sid, lastSeq}
4. Agent 回放 (lastSeq, current] 区间的所有 chunk
5. Agent 发 replay_done → 切换为实时流
```

**竞态风险点**：第 4→5 步之间若产生新输出，必须在回放期间挂起实时推送并排队，否则会乱序。这是全项目最容易出 bug 的地方，需要单测覆盖。

### 3.6 心跳与超时

- `ws` 层 ping/pong，间隔 30s
- 90s 无响应判定死连接并清理路由表
- PC Agent 侧断线采用指数退避重连（1s → 2s → 4s → … → 上限 30s）

---

## 4. Relay（中继服务器）

### 4.1 部署形态
- **不装 Docker**（1.6G 内存没必要），裸机部署
- Node.js 22 通过 NodeSource 源安装
- `systemd` unit：`relay.service`，`Restart=always`
- 监听 `443`（主）与 `8443`（备），两个端口同一进程内两个 listener

> **为什么是两个端口**：`443` 对家庭宽带、公司网络、4G/5G 的出站友好度最高；`8443` 用于规避潜在的端口级干扰。App 与 Agent 的配置里都放候选地址列表，启动时按顺序探测，连上即锁定。

### 4.2 证书方案（无域名 + 自签）

```bash
# 生成自签证书（10 年有效）
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout /etc/relay/key.pem -out /etc/relay/cert.pem \
  -subj "/CN=pi-relay" \
  -addext "subjectAltName=IP:<服务器公网IP>"

openssl x509 -in /etc/relay/cert.pem -noout -fingerprint -sha256
```

- 最后一条输出的 **SHA-256 指纹**写入 App 与 PC Agent 配置
- 两端都**不做系统 CA 校验**，只校验指纹（certificate pinning）
- 证书轮换 → 需同步更新两端配置并重新构建（这是无域名的代价，已接受）

### 4.3 路由与状态

```
agents   : Map<agentId, WebSocket>       // Agent 重连则覆盖旧连接
sessions : Map<sid, agentId>             // 会话归属，Agent 重连时重建
client   : WebSocket | null              // 单设备独占，新连接踢旧的
```

### 4.4 加固清单

> ⚠️ **待办**：root 密码已在聊天中泄露，务必尽快 `passwd` 改密并改用密钥登录、关闭 `PasswordAuthentication`。

- `sshd`：禁 root 直接登录、改非标端口或限制来源、只允许密钥
- `fail2ban` 已启用
- 防火墙：仅放行 `22` / `443` / `8443`
- **阿里云安全组需在控制台单独放行 443 / 8443**（服务器防火墙是另一层，两层都要开）
- 进程降权：`relay.service` 用非 root 用户运行
- 单 IP 连接数限制、消息速率限流、日志滚动（`logrotate`）

---

## 5. PC Agent

### 5.1 技术要点

| 项 | 方案 |
|---|---|
| PTY | `node-pty`（Win10+ 走 ConPTY，原生支持） |
| 默认 shell | `pwsh.exe`（见 §5.4，需换成 MSI 安装） |
| resize | `pty.resize(cols, rows)` 必须透传，否则 pi 的 TUI 排版错乱 |
| 会话保活 | pty 由 Agent 持有；App 断开只 detach |
| 输出缓冲 | 环形缓冲，双阈值：5000 行 **或** 2MB，先到先裁剪 |
| 重连 | 指数退避连接中继 |
| 保活进程 | `nssm` 注册 Windows 服务 |
| 日志 | nssm 重定向 + 轮转 |

### 5.2 会话预设（config.json 可配）

```jsonc
{
  "relay": {
    "endpoints": ["wss://<IP>:443/relay", "wss://<IP>:8443/relay"],
    "agentId": "home-pc",
    "agentToken": "***",
    "pinnedSha256": "AB:CD:..."
  },
  "buffers": { "maxLines": 5000, "maxBytes": 2097152 },
  "presets": [
    { "id": "pi", "name": "pi agent",
      "shell": "auto",
      "cwd": "D:/Program/Pi_Agent",
      "args": ["-NoLogo","-NoProfile","-Command","pi"] },
    { "id": "ps", "name": "PowerShell 7",
      "shell": "auto",
      "cwd": "C:/Users/Orion",
      "args": ["-NoLogo"] }
  ]
}
```

`"shell": "auto"` 由 Agent 动态解析，见 §5.4。

**【待定】** `pi` 预设的 `cwd`（默认工作目录）—— 先用 `D:/Program/Pi_Agent`，可随时改。

### 5.4 Shell 解析（已勘察实测）

`pwsh` 7.6.6 是 **MSIX/Store 包**，但经实测**可以在服务上下文中使用**：

- 按全路径 `"C:\Program Files\WindowsApps\Microsoft.PowerShell_7.6.6.0_x64__8wekyb3d8bbwe\pwsh.exe"` 直接调用，正常返回 `7.6.6`，退出码 0
- 目录 ACL 包含 `BUILTIN\Users:(RX)` 与 `NT AUTHORITY\SYSTEM:(F)`，两种服务账号都能执行
- 它是 full-trust 打包应用，非 AppContainer，不受沙箱限制

**所以不需要换 MSI 版**（且 winget 把 MSIX/MSI 视为同一个包 ID，直接 install 会被“已安装”分支拦截，换装反而麻烦）。

**但有两个坑必须处理：**

1. **不能用应用执行别名**：`C:\Users\Orion\AppData\Local\Microsoft\WindowsApps\pwsh.exe` 是用户级重解析点，依赖包注册与别名开关，服务里不可靠。**必须用带版本号的全路径**。
2. **路径含版本号**（`_7.6.6.0_`）—— PowerShell 升级后路径会变，写死会坏。

**处理方式**：Agent 启动时按优先级动态解析并缓存结果

```
1. C:\Program Files\PowerShell\7\pwsh.exe          (若有 MSI 版)
2. powershell -c "(Get-AppxPackage Microsoft.PowerShell).InstallLocation" + "\pwsh.exe"
3. C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe   (找底保底，必然存在)
```

解析结果写入日志，便于排查。

**为什么优先 PS 7 而不用 5.1**：5.1 默认使用系统 ANSI 代码页，UTF-8 输出（pi 的 TUI 里有中文与制表符）容易乱码；PS 7 默认 UTF-8。

### 5.3 服务化

> **【已定】** 以 `LocalSystem` 身份运行（不需存密码，开机即启，不依赖登录）。

安装脚本已就绪，双击即可（会自提权）：

```
agent/install-service.cmd          # 安装并启动
agent/uninstall-service.cmd        # 停止并移除
```

脚本做的事：装 nssm（winget）→ 注册服务 → 配 AppDirectory/日志轮转/开机自启 → 以 LocalSystem 运行 → 启动 → 自检并打印日志尾部。

关键参数：

```cmd
nssm install PiRemoteAgent <node.exe> D:\Program\Pi_Agent\agent\server.js
nssm set PiRemoteAgent AppDirectory    D:\Program\Pi_Agent\agent
nssm set PiRemoteAgent AppStdout       D:\Program\Pi_Agent\logs\agent.log
nssm set PiRemoteAgent AppStderr       D:\Program\Pi_Agent\logs\agent.err.log
nssm set PiRemoteAgent AppRotateBytes  1048576
nssm set PiRemoteAgent Start           SERVICE_AUTO_START
nssm set PiRemoteAgent ObjectName      LocalSystem
```

### 5.4 服务运行账号：LocalSystem 的代价与补偿

以 `LocalSystem` 运行意味着进程继承的是 **SYSTEM 的环境**，而不是桌面用户的。这会造成两个具体问题，都已在代码里补偿：

| 问题 | 补偿 |
|---|---|
| `Get-AppxPackage` 是按用户的，SYSTEM 查不到 Orion 装的 pwsh，会掉到 PowerShell 5.1（编码损坏） | `resolveShell()` 新增直接扫描 `C:\Program Files\WindowsApps\Microsoft.PowerShell_*` 并按版本取最高，与当前账号无关 |
| SYSTEM 的 PATH 里没有 `pi`，且 `USERPROFILE` 指向 SYSTEM 的目录，pi 找不到自己的配置与认证 | 配置新增 `shellEnv` + `pathPrepend`，agent 在 spawn 时注入 `USERPROFILE`/`APPDATA`/`LOCALAPPDATA`/`TEMP` 并前置 `pi` 所在的 PATH |

`buildEnv()` 会归一化 PATH 键名，避免环境块里同时出现 `Path` 和 `PATH` 两份。

**已知未验证的风险**：pi 若调用 `git push`，DPAPI 保护的凭据在 LocalSystem 下无法解密。日常只跑 pi 编辑/构建不受影响，需要 push 时再改用 Orion 账号（`nssm set PiRemoteAgent ObjectName .\Orion <密码>`）。

**ConPTY 在 Session 0 的可行性**：M1 阶段已用管道 stdio（同样没有控制台）完成 16 项测试，包括 pi TUI 渲染与进程树回收，因此该风险基本排除；服务化后再跑一次 `PI_AGENT_EXTERNAL=1 node tools/m1-test.js` 做终检。

---

## 6. 终端渲染与交互设计

### 6.0 已移植 Termux 的终端模拟器

`android/terminal-emulator` 是 Termux terminal-emulator 的移植（**GPLv3**，许可证见模块内 `LICENSE-GPLv3.md`）。

动机：早期自研版本有两个结构性缺陷，靠打补丁修不好：

1. **没有 reflow** —— 宽度变化时折行的长文本被直接截断，而不是重新折行
2. **resize 无条件重置滚动区域** —— 程序用 `CSI r` 设过区域（TUI 状态栏、vim 分屏）后尺寸一变就丢

移植内容：`TerminalEmulator / TerminalBuffer / TerminalRow / TerminalColors / TextStyle / WcWidth / ByteQueue / KeyHandler` + **完整测试套件（145 项）**。

移植时剥离了 Android 依赖，让模块保持纯 JVM、测试不需要模拟器：

| 原依赖 | 替代 |
|---|---|
| `android.util.Log` | stderr 回退（`Logger`） |
| `android.graphics.Color` | 位运算（`TerminalColors`） |
| `android.util.Base64` | `java.util.Base64`（`TerminalEmulator` OSC 52） |
| `android.view.KeyEvent.KEYCODE_*` | `KeyEventCodes`（从 SDK 源码抽取的常量） |

未移植 `TerminalSession` / `JNI` / 原生构建：PTY 在 PC 端，通过中继访问。
`TerminalSessionClient` 相应裁剪到 `TerminalEmulator` 实际调用的方法。

**注意**：GPLv3 具有传染性，链接该模块的整个 App 都受其约束。

### 6.1 滚动坐标约定（最容易搞混的地方）

```
scrollPx = 0        贴底，显示最新输出
scrollPx 变大        回看更早的内容
手指下滑              scrollPx 增大 → 内容跟着手指走
fling() 的 velocityY  手指下滑为正，**直接传入，不需要取反**
```

很多滚动组件是「从顶部算起」的，那种要传 `-velocityY`。这里是从底部算起的，所以不取反。

`OverScroller.fling` 的签名是
`fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)`：
偏移在 Y 轴上，**范围必须放在第 7、8 个参数**。放到 `minX/maxX` 会让 Y 范围变成 `[0,0]`，
滚动器直接把视口夹回底部——表现为「往上滑，惯性却往下去」。

### 6.2 尺寸变更必须等稳定

软键盘是滑入的，`onSizeChanged` 会在动画期间逐帧触发。直接响应会：

1. 每帧都把行推进 scrollback
2. 每帧重置滚动位置
3. 每帧给 PC 发 resize → 对方 PTY 重设尺寸 → 程序整体重绘

三者叠加就是使用者感知到的「文字跳一下」。现在先等 120ms 不再变化再提交，
渲染直接用当前视图高度取最后 N 行，所以动画期间画面依然正确，只是缓冲区不重建。

### 6.3 滚动条

右边缘一条 4dp 的圆角滑块，始终存在（空闲 alpha 110 / 使用中 200），
触摸热区宽 28dp 所以好抓。滑块高度按 `可见行/总行` 比例，位置由 `scrollPx/range` 决定。

---

## 7. Android App 模块

### 7.1 模块划分

| 模块 | 类型 | 职责 |
|---|---|---|
| `:terminal-emulator` | 纯 Kotlin，无 Android 依赖 | VT 解析状态机 + 字符网格 + 滚动缓冲。**可纯 JVM 单测** |
| `:terminal-view` | Android Library | 自定义 `View` + `Canvas` 渲染、光标、选区、滚动手势 |
| `:app` | Application | Compose UI、网络层、前台服务、会话菜单、快捷指令 |

> **为什么终端视图用传统 View 而不是 Compose Canvas**：等宽字符网格用 `Paint.drawText` 批量绘制性能远好于 Compose 逐段布局，且 IME / 按键 / 长按选区的控制更直接。用 `AndroidView` 与 Compose 互操作。

### 7.2 终端模拟器必须支持（pi 的 TUI 依赖项）

**必做**

- CSI 序列：光标定位/移动、清屏、擦除、插入删除行、滚动区域（`ESC[r`）
- SGR：基础 8/16 色、`38;5;n` 256 色、`38;2;r;g;b` truecolor、粗体/下划线/反显
- **Alternate screen buffer**（`CSI ?1049h/l`、`?47`、`?1047`）—— pi 这类 TUI 全靠它，不支持必然花屏
- 光标显隐（`?25`）、自动换行（`?7`）、应用光标键（`?1`）、原点模式（`?6`）
- **Bracketed paste**（`?2004`）
- OSC：设置窗口标题（`ESC]0;`/`ESC]2;`）
- 中日韩全角双宽字符（wcwidth）、emoji
- 滚动回看缓冲

**后置**

- 鼠标上报（`?1000/1002/1006`）—— 手机上意义有限
- 超链接、剪贴板 OSC 52

**参考实现**：Termux 的 `terminal-emulator` / `terminal-view` 模块设计可借鉴。
⚠️ **许可注意**：Termux 相关模块为 **GPLv3**。自用无碍；若未来要上架或闭源分发，需自行重写或整体开源。

### 7.3 输入方案（TUI 最大痛点）

- 底部常驻**补全键栏**：`Esc` `Ctrl` `Alt` `Tab` `↑` `↓` `←` `→` `Enter` `Ctrl+C`
- `Ctrl` 作为修饰键：按下后再按字母 → 发送 `0x01`–`0x1A`
- 粘贴：多行内容走 bracketed paste 包裹，避免被逐行执行
- IME：用 `InputConnection` 的 raw key 通道获取 `Esc`/`Tab` 等被输入法吞掉的键
- 支持外接蓝牙键盘（标准按键事件全量透传）

### 7.4 连接与保活

- **前台服务**（`FOREGROUND_SERVICE_TYPE_DATA_SYNC`）+ 常驻通知
- 引导用户把 App 加入**电池优化白名单**，否则 Doze 会掐断连接
- 网络切换（WiFi ↔ 蜂窝）时自动重连 + `attach` 续传
- 息屏/切后台不主动断线；断线后按退避策略重连
- 连接状态在通知栏可见（已连接 / 重连中 / 已断开）

### 7.5 构建配置

- `minSdk 26`（Android 8.0）、`targetSdk 35`
- Kotlin 2.x + Compose BOM + OkHttp（WebSocket）
- 仅生成工程源码，使用 Android Studio 本地构建

---

## 8. 目录规划

```
D:/Program/Pi_Agent/
├── PLAN.md
├── PROTOCOL.md              # 协议细则（从本文件抽出）
├── relay/                   # 中继服务（部署到 Debian）
│   ├── server.js
│   ├── auth.js
│   ├── router.js
│   ├── package.json
│   ├── relay.service
│   └── deploy.md
├── agent/                   # PC 端 Agent
│   ├── server.js
│   ├── pty-manager.js
│   ├── session.js
│   ├── ringbuffer.js
│   ├── protocol.js
│   ├── config.example.json
│   └── install-service.cmd
├── shared/                  # 协议常量（三端共享的真相来源）
│   └── protocol.md
└── android/                 # Android Studio 工程
    ├── settings.gradle.kts
    ├── app/
    ├── terminal-emulator/
    └── terminal-view/
```

---

## 9. 里程碑

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| **M0** | 服务器基建 | Node 22 装上；自签证书生成并拿到指纹；systemd unit 就位；安全组放行 443/8443 |
| **M1** | PC Agent 本地打通 | 本机 `wscat` 连 `127.0.0.1`，能创建会话、操作 PowerShell、resize 正常 |
| **M2** | 中继上线 + 远端接入 | 从外网（手机关 WiFi 用流量）能连到家里电脑并操作 |
| **M3** | App 骨架 + 终端模拟器 | 能完整显示并操作 `pi`，TUI 不花屏、颜色正确、中文不错位 |
| **M4** | 保活 / 断线续传 | 切网络 + 息屏 5 分钟后重连，回放连续无乱序无丢字 |
| **M5** | 服务化 + 体验打磨 | 开机即用；快捷指令；补全键栏；息屏长时间稳定 |

> **M3 是最大的一块**。建议 M0–M2 阶段先用一个临时 WebView/`wscat` 验证端到端链路，链路稳了再投入自研终端模拟器，避免同时 debug 协议和渲染两个变量。

---

## 10. 风险清单

| 风险 | 影响 | 应对 |
|---|---|---|
| 终端模拟器工作量被低估 | M3 严重延期 | 先做最小可用子集（alt-screen + SGR + 光标），跑通 pi 后再补全 |
| 回放与实时流竞态 | 输出乱序/丢字 | 回放期挂起实时推送并排队；单测覆盖 |
| Android Doze 杀前台服务 | 保活失效 | 电池优化白名单 + 通知常驻 + 重连退避 |
| 中继是单点 | 服务器挂 → 全断 | systemd 自拉；PC 侧无限重连；**已有 Tailscale 可作应急兜底通道** |
| 自签证书轮换 | 需重新构建 App | 证书有效期设 10 年，尽量避开 |
| 阿里云安全组未放行 | 外网连不上，排查耗时 | M0 阶段就配好并验证 |
| ConPTY resize 未透传 | pi 的 TUI 排版错乱 | M1 阶段就验证 resize |
| 大陆 ↔ 服务器链路质量 | 晚高峰卡顿 | 双端口候选；必要时改用 Tailscale 直连模式 |
| MSIX 版 pwsh 无法在服务中启动 | Agent 起不来 | **已实测排除**：全路径可直接执行，ACL 放行；改用动态路径解析（§5.4） |
| PowerShell 升级导致 MSIX 路径变化 | Agent 找不到 shell | 启动时动态解析 + 保底回退到 5.1（§5.4） |
| ConPTY 在 Session 0 行为异常 | 服务化后无终端 | M1 阶段实测；退路是改用任务计划程序触发 |
| 服务用 LocalSystem 导致 pi 找不到配置 | pi 无法启动 | 服务以 Orion 身份运行（§5.5） |

---

## 11. 待定项

1. **PC 端 pi 的默认工作目录** —— 暂定 `D:/Program/Pi_Agent`，可随时改配置
2. ~~PC 上是否装了 `pwsh`~~ —— **已确认**：有 7.6.6（MSIX），实测可直接按全路径调用，无需换 MSI（见 §5.4）
3. ~~阿里云公网 IP 是否固定~~ —— **已确认**：包年包月，IP 固定
4. **中继是否需要心跳推送/离线消息** —— 当前 MVP 不做，走纯实时流
5. **是否把 Tailscale 作为第二通道内置** —— 可作为中继挂掉时的兜底，但会增加 App 复杂度

---

## 12. 下一步动作

M0 需要在服务器上执行（你已具备 root）：

```bash
# 1. 装 Node 22
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt-get install -y nodejs

# 2. 生成自签证书
mkdir -p /etc/relay
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout /etc/relay/key.pem -out /etc/relay/cert.pem \
  -subj "/CN=pi-relay" -addext "subjectAltName=IP:<公网IP>"

# 3. 拿指纹（这个值要填进 App 与 Agent 配置）
openssl x509 -in /etc/relay/cert.pem -noout -fingerprint -sha256
```

同时在阿里云控制台**安全组放行 TCP 443 与 8443**。
