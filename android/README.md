# Pi Remote (Android)

Pi Remote 是一个在 Android 手机上远程控制电脑终端以及 AI 编程 Agent（如 Pi Agent）的客户端应用。通过轻量中继服务，在电脑无需公网 IP 和端口转发的情况下，实现手机端对电脑终端操作与 Agent 运行状态的查看与交互。

---

## 整体架构与互联机制

系统由 **Android 客户端**、**公网中继服务 (Relay)** 和 **电脑端代理 (PC Agent)** 三部分组成：

```
┌─────────────────┐         TLS 1.3 / WSS          ┌──────────────────┐         TLS 1.3 / WSS          ┌──────────────────┐
│   Android App   │ ◄────────────────────────────► │   中继服务 Relay  │ ◄────────────────────────────► │  电脑端 PC Agent  │
│                 │                                │  (Node.js 守护)   │                                │  (Windows 服务)   │
│ • 图形 / 终端视图│  • 二进制多路复用帧 (8-byte 头) │                  │  • 二进制多路复用帧 (8-byte 头) │                  │
│ • 远程文件与会话  │  • HMAC-SHA256 签名握手鉴权     │ • 路由转发与心跳  │  • HMAC-SHA256 签名握手鉴权     │ • ConPTY 终端仿真 │
│ • 手机本地 Agent │  • 证书 SHA-256 指纹强校验      │ • 零持久化中转   │  • 证书 SHA-256 指纹强校验      │ • pi RPC 子进程桥接│
└─────────────────┘                                └──────────────────┘                                └──────────────────┘
```

- **无需公网 IP**：电脑端运行 Agent 守护服务，主动向中继服务器发起 WebSocket 连接；手机端同样连接中继。两侧均为出站连接，电脑位于局域网或 NAT 路由器后也能正常使用。
- **通信安全**：采用 HMAC-SHA256 签名握手与 TLS 自签证书 SHA-256 指纹强校验（Certificate Pinning），无需配置商业 CA 域名证书。

---

## 快速上手与配置指南

> 💡 **给 AI Agent 用户的极速上手技巧**：
> 如果你正在使用 AI 编程助手（如 Claude Code、Cursor、Windsurf、Pi Agent、Copilot CLI 等），无需手动逐行执行以下步骤，直接将本项目克隆到本地后对你的 Agent 发送：
> 
> ```text
> 请阅读本项目 README.md，帮我检查当前电脑的环境依赖，配置好 agent/config.json 并运行脚本注册启动 PC Agent 服务。
> ```
> 
> Agent 会自动检测你的 Node.js / PowerShell 环境、生成配置并完成服务部署。

---

### 第一步：部署中继服务器（Relay）

中继服务器负责手机与电脑之间的流量转发（需要一台具有公网 IP 的云服务器，如 Debian / Ubuntu）：

1. **进入中继目录并运行部署脚本**：
   
   ```bash
   cd relay/
   sudo ./deploy.sh
   ```
2. **记录生成的凭据**：
   脚本会自动安装 systemd 守护服务、生成 TLS 1.3 自签证书，并在控制台输出配置信息：
   - **中继地址**：`wss://<你的服务器公网IP>:443/relay`（或备用端口 `8443`）
   - **设备 Token**：在 `/etc/pi-relay/config.json` 中配置的一组随机安全密钥
   - **证书 SHA-256 指纹**：格式形如 `AB:CD:EF:...`（64位十六进制）
3. **健康检查验证**：
   在任意设备上运行 `curl -sk https://<你的服务器公网IP>/healthz`，若返回 `{"ok":true}` 即表示中继运行正常。

---

### 第二步：电脑端配置（PC Agent）

电脑端作为受控端，桥接 Windows 底层终端（PowerShell）与 Pi Agent 进程：

1. **环境准备**：
   - 安装 Node.js 18+ 与 PowerShell 7 (`pwsh`)；
   - 若需使用图形工作台，安装 Pi 编码 Agent：`npm install -g @earendil-works/pi-coding-agent`。
2. **配置文件**：
   在 `agent/` 目录下创建 `config.json`（可参考 `config.example.json`）：
   
   ```json
   {
     "relay": {
       "endpoints": ["wss://<你的中继服务器IP>:443/relay"],
       "token": "<在relay生成的Token>",
       "pinnedSha256": "<在relay生成的证书SHA-256指纹>"
     },
     "agentId": "my-pc",
     "presets": [
       { "id": "pwsh", "name": "PowerShell 7", "type": "pty", "cwd": "C:/Users/YourName" },
       { "id": "pi", "name": "Pi Agent", "type": "rpc", "cwd": "D:/Projects" }
     ]
   }
   ```
3. **安装与启动服务**：
   - 右键管理员身份运行 `agent/install-service.cmd`，自动安装为 Windows 系统服务 `PiRemoteAgent` 并开机自启；
   - （或在终端手动前台调试启动）：`node agent/server.js`；
   - 启动后，PC Agent 会自动连接中继并在中继中注册在线状态。

---

### 第三步：手机端配置与使用（Android App）

1. **安装 APK**：
   编译生成的 APK 安装至手机，首次打开应用；
2. **配置连接参数**：
   - 点击应用右上角的 **「设置」** 图标；
   - **中继地址**：输入 `wss://<你的中继服务器IP>:443/relay`（每行一个，支持配置备用端口 `wss://<你的中继服务器IP>:8443/relay`）；
   - **设备 Token**：填入第二步中相同的 Token；
   - **证书指纹**：填入对应的 SHA-256 指纹（冒号分隔大写十六进制）；
   - 点击 **「保存并连接」**。
3. **日常操作流程**：
   - **查看连接状态**：返回主页，顶部状态栏显示 `已连接 · 电脑端 my-pc` 即表示链路打通；
   - **远程浏览全盘目录**：点击工作目录栏（Cwd），可直接远程浏览电脑上 C/D/E 等全盘目录并选定工作区；
   - **发起与管理会话**：点击预设卡片即可发起全新会话；正在运行的会话可随时点击「打开」接管或「终止」回收；
   - **双视图自由切换**：进入会话后，右上角一键在「图形卡片视图」与「底层终端视图」之间热切换；
   - **全双工中途引导**：在图形视图中，Agent 思考与跑测试期间输入框不锁定，随时可输入指导建议（Steer）实时矫正方向。

---

### 第四步：手机本地 AI Agent（可选）

如需使用应用内脱机的手机本地 AI Agent：

1. 点击底部「本地 Agent」标签页；
2. 点击设置，选择服务商（如 DeepSeek、SiliconFlow、OpenAI 或局域网内运行的 Ollama `http://192.168.x.x:11434/v1`）；
3. 填入对应的 API Key（存储于手机本地安全沙箱）；
4. 本地 Agent 支持文件读写、网页代码生成，并自动通过内置的 `http://127.0.0.1:8765/` 服务提供应用内实时 WebView 预览。

---

## 核心功能清单

- **双视图交互**：
  - **图形视图**：结构化展示思考过程折叠、工具调用记录与 Markdown 输出；遇到选项可直接在手机上触控点击选择；提供老旧 PTY 会话的文本解析兜底。
  - **终端视图**：基于 Termux 模拟内核，支持 ANSI 色彩；屏幕底部提供 `Ctrl`/`Alt`/`Tab` 等快捷按键栏；适配软键盘弹出防抖动避让。
- **全双工中途引导（Steering）**：在远程 Agent 思考或运行命令期间，随时插入引导指令实时调向。
- **远端目录与会话漫游**：手机端浏览电脑端全盘符文件系统，支持接管历史持久化会话快照。
- **手机内置终端环境**：内置 arm64 GNU Bash 与 BusyBox 核心命令集，支持脱机运行本地 Shell 脚本。

---

## 编译与构建

### 环境要求

- Android Studio Ladybug (2024.2) 或更高版本
- JDK 17
- Android SDK 35 (最低支持 Android 8.0 / API 26)

### 编译 APK

```bash
# 运行单元测试
./gradlew :terminal-emulator:test
./gradlew :app:test

# 编译 Debug APK
./gradlew :app:assembleDebug
```

产出路径：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 许可证

- 终端模拟器底层组件源自 [Termux](https://github.com/termux/termux-app)，遵循 GPLv3 许可证（详见 [LICENSE-GPLv3.md](terminal-emulator/LICENSE-GPLv3.md)）。
- 其余代码遵循通用开源规范。
