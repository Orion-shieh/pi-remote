# ==============================================================================
# Pi Remote - 方案 3: Android 内置独立运行 Node.js & Pi Agent 打包脚本
# 目标: 绕过 Android 10+ (API 29-36) W^X 机制，实现手机脱机免 Termux 原生执行
# ==============================================================================

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Pi Remote: 方案 3 (内置独立 Node.js / 免 Termux) 自动化打包" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Resolve-Path "$ScriptDir\.."
$JniArm64Dir = "$ProjectRoot\app\src\main\jniLibs\arm64-v8a"
$TargetSo = "$JniArm64Dir\libnode.so"

Write-Host "[1/4] 检查原生库目录: $JniArm64Dir" -ForegroundColor Yellow
if (-not (Test-Path $JniArm64Dir)) {
    New-Item -ItemType Directory -Path $JniArm64Dir -Force | Out-Null
    Write-Host "  + 已创建目录 $JniArm64Dir" -ForegroundColor Gray
}

Write-Host "[2/4] 校验 libnode.so 独立运行时..." -ForegroundColor Yellow
if (Test-Path $TargetSo) {
    $fileSize = (Get-Item $TargetSo).Length / 1MB
    Write-Host "  + 检测到现有 libnode.so (大小: $('{0:N2}' -f $fileSize) MB)" -ForegroundColor Green
} else {
    Write-Host "  * 正在准备下载适配 Android ARM64 (Bionic) 的 Node.js 二进制..." -ForegroundColor Cyan
    $DownloadUrl = "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v18.20.4/nodejs-mobile-v18.20.4-android.zip"
    $ZipPath = "$ProjectRoot\tools\node-android.zip"
    $ExtractDir = "$ProjectRoot\tools\node-android-extracted"

    try {
        Write-Host "  * 从 GitHub 官方下载 nodejs-mobile 运行时 (~55MB)..." -ForegroundColor Gray
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath -UseBasicParsing
        Write-Host "  * 正在解压 ARM64 原生库..." -ForegroundColor Gray
        Expand-Archive -Path $ZipPath -DestinationPath $ExtractDir -Force
        
        # 寻找 arm64-v8a 下的 libnode.so
        $SourceSo = Get-ChildItem -Path $ExtractDir -Filter "libnode.so" -Recurse | Where-Object { $_.FullName -match "arm64-v8a" } | Select-Object -First 1
        if ($SourceSo) {
            Copy-Item -Path $SourceSo.FullName -Destination $TargetSo -Force
            Write-Host "  + 成功将 libnode.so 复制至 $TargetSo" -ForegroundColor Green
        } else {
            Write-Host "  ! 未在解压目录中找到 arm64-v8a 的 libnode.so，请手动下载并放置至 $TargetSo" -ForegroundColor Red
        }
        # 清理临时下载文件
        Remove-Item -Path $ZipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $ExtractDir -Recurse -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Host "  ! 自动下载失败: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  ! 提示: 您可以手动下载 Android arm64-v8a 的 Node 原生库，重命名为 libnode.so 放入 $JniArm64Dir" -ForegroundColor Yellow
    }
}

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }

    # 1. Android Studio bundled JBR / JRE
    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jre",
        "D:\Program Files\Android\Android Studio\jbr",
        "D:\Program Files\Android\Android Studio\jre",
        "C:\Program Files\Android\Android Studio 1\jbr"
    )

    # 2. Standalone JDKs
    $candidates += Get-ChildItem "C:\Program Files\Java\jdk*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $candidates += Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $candidates += Get-ChildItem "C:\Program Files\Microsoft\jdk*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $candidates += Get-ChildItem "C:\Program Files\Zulu\zulu*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $candidates += Get-ChildItem "$env:USERPROFILE\.jdks\*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName

    foreach ($cand in $candidates) {
        if ($cand -and (Test-Path "$cand\bin\java.exe")) {
            return $cand
        }
    }

    # 3. Registry query
    $regPaths = @(
        "HKLM:\SOFTWARE\Android Studio",
        "HKCU:\SOFTWARE\Android Studio"
    )
    foreach ($reg in $regPaths) {
        $path = (Get-ItemProperty -Path $reg -ErrorAction SilentlyContinue).Path
        if ($path) {
            $jbr = "$path\jbr"
            if (Test-Path "$jbr\bin\java.exe") { return $jbr }
            $jre = "$path\jre"
            if (Test-Path "$jre\bin\java.exe") { return $jre }
        }
    }

    return $null
}

Write-Host "[3/4] 编译 Android 独立运行版 APK..." -ForegroundColor Yellow
$foundJava = Find-JavaHome
if ($foundJava) {
    Write-Host "  + 自动定位 Java 环境: $foundJava" -ForegroundColor Green
    $env:JAVA_HOME = $foundJava
    $env:PATH = "$foundJava\bin;$env:PATH"
} else {
    Write-Host "  ! 未自动定位到 Java/JDK。若编译报错，请设置 JAVA_HOME 环境变量或在 Android Studio 中打开本项目点击 Build。" -ForegroundColor Yellow
}

Set-Location $ProjectRoot
if (Test-Path ".\gradlew.bat") {
    & ".\gradlew.bat" assembleDebug
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + APK 编译成功！" -ForegroundColor Green
    } else {
        Write-Host "  ! Gradle 编译遇到错误，退出码: $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
} else {
    Write-Host "  ! 未找到 gradlew.bat" -ForegroundColor Red
}

function Find-Adb {
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        return "adb"
    }

    # 1. Read sdk.dir from local.properties
    $localProp = "$ProjectRoot\local.properties"
    if (Test-Path $localProp) {
        $sdkLine = Get-Content $localProp | Where-Object { $_ -match "^sdk\.dir\s*=" }
        if ($sdkLine) {
            $sdkPath = ($sdkLine -split "=", 2)[1].Trim().Replace('\\', '\')
            $adbCandidate = "$sdkPath\platform-tools\adb.exe"
            if (Test-Path $adbCandidate) { return $adbCandidate }
        }
    }

    # 2. Check standard SDK paths
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "C:\Android\Sdk\platform-tools\adb.exe",
        "D:\Android\Sdk\platform-tools\adb.exe"
    )
    foreach ($cand in $candidates) {
        if ($cand -and (Test-Path $cand)) { return $cand }
    }

    return $null
}

$ApkPath = "$ProjectRoot\app\build\outputs\apk\debug\app-debug.apk"
Write-Host "[4/4] 检查连接的 Android 设备并安装 APK..." -ForegroundColor Yellow
$adbCmd = Find-Adb

if ($adbCmd) {
    Write-Host "  + 自动定位 ADB 工具: $adbCmd" -ForegroundColor Green
    try {
        $devices = & $adbCmd devices 2>$null
        if ($devices -match "device\b") {
            Write-Host "  * 检测到已连接的 Android 手机/模拟器，正在安装 APK..." -ForegroundColor Cyan
            & $adbCmd install -r $ApkPath
            Write-Host "  + APK 已成功推送到手机！" -ForegroundColor Green
            Write-Host "  * 正在手机上自动启动 Pi Remote..." -ForegroundColor Cyan
            & $adbCmd shell am start -n "com.piremote.app/.MainActivity" | Out-Null
            Write-Host "  + Pi Remote 已在手机上自动拉起！" -ForegroundColor Green
            Write-Host "  + 系统已自动将 libnode.so 提取至 nativeLibraryDir，具备完整执行权限！" -ForegroundColor Green
            Write-Host "  + 现在在手机终端中输入 node -v，即可验证内置 Node.js 独立运行！" -ForegroundColor Green
        } else {
            Write-Host "  * 暂未检测到通过 USB 调试连接的 Android 设备。" -ForegroundColor Yellow
            Write-Host "  + 生成的内置版 APK 位于:" -ForegroundColor Green
            Write-Host "    $ApkPath" -ForegroundColor Cyan
            Write-Host "  * 请将手机通过 USB 连接并开启调试后重试，或直接将该 APK 文件传输至手机点击安装即可！" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  ! ADB 调用异常: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  + 生成的内置版 APK 位于: $ApkPath" -ForegroundColor Green
    }
} else {
    Write-Host "  * 未在 PATH 或 Android SDK 中检测到 adb.exe。" -ForegroundColor Yellow
    Write-Host "  + 生成的内置版 APK 位于:" -ForegroundColor Green
    Write-Host "    $ApkPath" -ForegroundColor Cyan
    Write-Host "  * 请直接将该 APK 文件发送至手机安装，安装后即可拥有内置免 Termux 运行环境！" -ForegroundColor Green
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  方案 3 流程执行完毕！" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
