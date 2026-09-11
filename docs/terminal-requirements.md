# 终端模拟器需求（从真实 pi 输出反推）

本文档不是推测，而是对 `agent/test/pi-check.js` 捕获的真实字节流做逆向分析的结果。
安卓终端模拟器（`:terminal-emulator`）以此为准来实现，优先做「必需」部分。

复现方式：

```bash
node agent/test/pi-check.js 20      # 捕获 20 秒，写出 agent/test/pi-capture.bin
```

---

## 1. 结论摘要

| 项 | 结论 |
|---|---|
| 备用屏幕 `?1049h` | **不使用** —— pi 是内联渲染，不需要实现备用屏幕缓冲 |
| 颜色 | **必须支持真彩色** `38;2;r;g;b` |
| 光标 | **必须支持绝对定位** `CSI r;c H` 与相对右移 `CSI n C` |
| 滚动区域 `CSI r` | 未使用 |
| 键盘 | **pi 会尝试启用 Kitty 键盘协议**，见 §4 |
| 同步输出 `?2026` | pi 会发送，见 §4 |
| 中文 | pi 的 UI 里有非 ASCII 字符，必须处理双宽字符 |

---

## 2. 实测出现的 CSI 序列（31 个）

```
ESC [ 1 C          光标右移 1
ESC [ 101 C        光标右移 101
ESC [ 119 C        光标右移 119
ESC [ 10 ; 1 H     绝对定位到 第10行 第1列
ESC [ 13 ; 1 H
ESC [ 16 ; 1 H
ESC [ 19 ; 1 H
ESC [ 23 ; 1 H
ESC [ H            光标归位
ESC [ 1 m          粗体
ESC [ 22 m         取消粗体
ESC [ 23 ; 1 H
ESC [ 27 m         取消反显
ESC [ 2 J          清屏
ESC [ 7 m          反显
ESC [ 93 m         亮黄色（8/16 色）
ESC [ K            擦除到行尾
ESC [ m            重置所有属性
ESC [ 38 ; 2 ; 138 ; 190 ; 183 m    真彩色前景色
ESC [ 38 ; 2 ; 102 ; 102 ; 102 m
ESC [ 38 ; 2 ; 128 ; 128 ; 128 m
ESC [ 38 ; 2 ; 129 ; 162 ; 190 m
ESC [ 38 ; 2 ; 240 ; 198 ; 116 m
ESC [ 38 ; 2 ; 255 ; 255 ; 0 m
ESC [ ? 25 l / h    隐藏 / 显示光标
ESC [ ? 1004 h      焦点上报
ESC [ ? 2004 h      bracketed paste 模式
ESC [ ? 2026 h / l  同步输出更新 开始 / 结束
ESC [ ? 9001 h      ConPTY 私有模式（与终端无关，可忽略）
ESC [ ? u           查询 Kitty 键盘协议当前标志
ESC [ > 7 u         推入 Kitty 键盘协议标志 = 7
```

另有 OSC 序列：

```
ESC ] 0 ; <title> BEL      设置窗口/标签标题
```

---

## 3. 必需实现（P0）

### 3.1 字符渲染

- 等宽字符网格，`Paint.drawText` 批量绘制
- **wcwidth 双宽处理**：CJK 占 2 列，需正确处理换行与光标推进
- UTF-8 解码必须跨 chunk 边界正确（pi 会分片输出多字节字符）
- OSC 0/2 设置标题 → 更新 App 的标题栏

### 3.2 光标与编辑

| 序列 | 含义 |
|---|---|
| `CSI n A/B/C/D` | 光标上/下/右/左移 n |
| `CSI n ; m H` / `CSI n ; m f` | 绝对定位（行列） |
| `CSI n G` | 绝对列定位 |
| `CSI H` / `CSI f` | 归位（1,1） |
| `CSI J` | 擦除显示（0/1/2） |
| `CSI K` | 擦除行（0/1/2） |
| `CSI L` / `CSI M` | 插入/删除行 |
| `CSI @` / `CSI P` | 插入/删除字符 |
| `ESC [ ? 25 h/l` | 光标显隐 |
| `ESC [ ? 7 h/l` | 自动换行开关 |

### 3.3 SGR 属性

- 基础：`0` 重置、`1` 粗体、`4` 下划线、`7` 反显、`22`/`24`/`27` 关闭
- 前景/背景 8 色与 16 色：`30–37` / `40–47` / `90–97` / `100–107`
- **256 色**：`38;5;n` / `48;5;n`
- **真彩色**：`38;2;r;g;b` / `48;2;r;g;b`

### 3.4 其他模式

- `?2004` bracketed paste：**必须实现**。开启后 App 主动粘贴时要包在 `ESC[200~ … ESC[201~` 里；否则多行粘贴会被 shell 逐行执行
- `?1004` 焦点上报：可选。若支持，聚焦/失焦时发 `ESC[I` / `ESC[O`

---

## 4. 两个「新协议」陷阱

### 4.1 Kitty 键盘协议（`CSI > 7 u` / `CSI ? u`）

pi 启动时会：

1. 发 `CSI > 7 u` —— 请求推入键盘协议标志 7
2. 发 `CSI ? u` —— 查询当前标志

**安卓端的处理策略**：**两者都直接忽略，不要响应 `CSI ? u`**。

如果响应了，pi 会认为终端支持扩展按键编码，进而期望收到 `CSI key;mods u` 形式的按键。安卓端只要不回应，pi 就会回退到传统转义序列（`ESC[A` 之类），这是我们要的行为。

⚠️ 这是一个**必须用测试锁死**的行为：一旦有人「顺手」实现了 `CSI ? u` 的响应，方向键和 Ctrl 组合键都会失效。

#### ⚠️ 已踩过的坑：`CSI > u` 被误解析为恢复光标

**这不是猜测，是实际发生过的 bug。**

`u` 在无前缀时是 `CSI u`（DECRC 恢复光标）。早期实现只把 `?` 前缀单独分支，`>` 前缀放行到了普通 CSI 处理，于是：

```
pi 发送   ESC [ > 7 u      (Kitty push，本应是空操作)
实现误解为 ESC [ u          (恢复光标)
结果       光标瞬间跳回 (0,0)
```

因为 pi 在**启动时就发**这个序列，后果是整个 TUI 布局错位（banner 写到错误的行、后续所有相对换行全部偏一行），而且**不会报错**。

**正确的处理**：只有**无前缀**的 `CSI` 才进普通分支，`?` 走私有模式分支，其余的 `<` `=` `>` 一律**直接 return 忽略**。

```kotlin
if (prefix == '?') { /* 私有模式 */ return }
if (prefix != '\u0000') return   // '<' '=' '>' 是 xterm/Kitty 扩展，一律不实现
```

同理，`ESC [ > 4 ; 2 m`（modifyOtherKeys）也不能当 SGR 处理，否则会意外点亮下划线/暗色属性。

**回归测试**：`TerminalEmulatorTest` 中的 `csi greater-than-u does not restore the cursor` 与 `csi greater-than m does not change sgr state` 锁死这两条。

### 4.2 同步输出更新（`CSI ? 2026 h/l`）

pi 在每个渲染帧前后发 `?2026h` / `?2026l`，语义是「这一帧的写入请原子提交」。

**安卓端的处理策略**：分两档

- **P0（可以不做）**：直接忽略。逐帧写入可能导致轻微闪烁/撕裂，但功能正常
- **P1（推荐）**：收到 `?2026h` 后把屏幕更新写入影子缓冲，收到 `?2026l` 时一次性提交并重绘。这能显著减少滚动和刷新时的撕裂感

---

## 5. 参考样本

`agent/test/pi-capture.bin` 是完整的原始捕获，已被复制到
`android/terminal-emulator/src/test/resources/pi-capture.bin`，直接作为单元测试的回归基线：
喂进模拟器后断言 banner 落在首行、四个绝对定位的章节标题（[Context]/[Skills]/[Prompts]/[Extensions]）都在、OSC 标题正确。

`android/tools/analyze-capture.py` 可以把任何捕获拆成「文本 / 转义序列」交替的 token 列表，
用来定位某个字符是被哪条序列写坏的：

```bash
python android/tools/analyze-capture.py android/terminal-emulator/src/test/resources/pi-capture.bin
```

---

## 6. 与 Windows 侧的额外约束

`node-pty` 的 `kill()` 在 Windows 上会 fork 一个 helper 去 `AttachConsole` 枚举控制台进程。**当调用进程没有控制台时会失败**（管道 stdio、Session 0 的 Windows 服务都会触发）。见 `agent/lib/session.js` 中的处理：改用 `taskkill /PID <pid> /T /F` 杀整棵进程树，既不依赖控制台，也能回收孙进程（例如 pi 本身）。
