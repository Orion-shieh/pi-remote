# Android 工程：构建与调试

## 环境（本机已实测）

| 项 | 值 |
|---|---|
| JDK | `C:\Program Files\Android\Android Studio\jbr`（OpenJDK 21） |
| SDK | `C:\Users\Orion\AppData\Local\Android\Sdk` |
| Gradle | 8.9（wrapper，走腾讯云镜像） |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| compileSdk / minSdk | 35 / 26 |
| 模拟器 AVD | `Medium_Phone_API_36.0`（API 36） |

**为什么用镜像**：`services.gradle.org` 与 `dl.google.com` 在国内会超时。
`gradle/wrapper/gradle-wrapper.properties` 已指向 `mirrors.cloud.tencent.com`，
`settings.gradle.kts` 的仓库列表把阿里云镜像放在 `google()` 之前。

## 模块

```
android/
├── terminal-emulator/   纯 Kotlin JVM 模块，无 Android 依赖 → 可纯 JVM 单测
│   └── src/test/resources/pi-capture.bin   真实 pi 捕获，作为回归基线
├── terminal-view/       Android Library：TerminalView + TerminalTheme + TerminalKeys
└── app/                 Compose UI、网络层、前台服务
```

`terminal-emulator` 刻意不依赖 Android，这样 38 项测试能在普通 JVM 上跑，不需要模拟器。

## 常用命令

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

./gradlew :terminal-emulator:test          # 38 项，纯 JVM，秒级
./gradlew :terminal-view:assembleDebug
./gradlew :app:assembleDebug               # 产出 app/build/outputs/apk/debug/
./gradlew clean :app:assembleDebug
```

## 在模拟器上跑

```bash
# Git Bash 必须关掉路径转换，否则 /sdcard/... 会被改写成 Windows 路径
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
ADB="$ANDROID_SDK/platform-tools/adb.exe"
EMU="$ANDROID_SDK/emulator/emulator.exe"

# 启动（必须设 ANDROID_AVD_HOME，否则 $HOME 是 MSYS 路径会找不到 AVD）
export ANDROID_AVD_HOME='C:\Users\Orion\.android\avd'
"$EMU" -avd Medium_Phone_API_36.0 -no-snapshot -no-boot-anim -no-audio -gpu swiftshader_indirect &

# 等启动完成
until [ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done

"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell pm grant com.piremote.app android.permission.POST_NOTIFICATIONS
"$ADB" shell am start -n com.piremote.app/.MainActivity
```

### 预置中继凭据（避免手输）

直接写 SharedPreferences，用 `run-as` 绕过权限：

```bash
# 内容参照 tools/make-agent-config.js 生成的 .secrets/relay.json
"$ADB" push prefs.xml /sdcard/pi-remote.xml
"$ADB" shell "cat /sdcard/pi-remote.xml | run-as com.piremote.app sh -c 'cat > /data/data/com.piremote.app/shared_prefs/pi-remote.xml'"
```

`prefs.xml` 的键：`endpoints`（多行）、`deviceToken`、`pinnedSha256`、`deviceId`。

### 观察运行状态

```bash
"$ADB" logcat -s PiRemoteRelay:* PiRemoteInput:*     # 连接与输入
"$ADB" shell uiautomator dump /sdcard/ui.xml && "$ADB" pull /sdcard/ui.xml .
```

> `TerminalView` 是自绘 View，**不进无障碍树**，所以 `uiautomator dump` 看不到终端内容。
> 只有 Compose 的按钮/文字能被 dump 到。要验证渲染必须靠截图做像素分析。

## 验证渲染（因为 dump 看不到）

```python
from PIL import Image, ImageChops
a = Image.open('before.png').convert('L')
b = Image.open('after.png').convert('L')
n = sum(1 for v in ImageChops.difference(a, b).getdata() if v > 30)
```

把区域降采样成 ASCII 可以直接“读”出字形，例如输入 `hello pi` 后：

```
#.   ##+  #    #.   ##+
# ## #  .# #    #.  #  +#
##  # ##### #    #. #   #
##  # #     #    #. #   #
#.  # ##### ++#+ .+#++  ####
 h    e    l    l    o   ▊
```

## 已知约束

- **必须 `enableEdgeToEdge()`**：`targetSdk 35` 在 Android 15+ 强制 edge-to-edge，
  `windowSoftInputMode="adjustResize"` 不再缩小窗口，键盘会直接盖住内容。
  同时 `imePadding()` 也需要它才能拿到 IME inset。
- 模拟器的 `-gpu swiftshader_indirect` 偶发崩（`bad color buffer handle`），重启即可。
- `AGENTS.md` 里记录的其它环境限制见仓库根 `PLAN.md` §0.2。
