# ==============================================================================
# Pi Remote - 方案一: Android 内置完整 Linux (GNU Bash + BusyBox + PRoot) 打包脚本
# 目标: 绕过 Android 10+ (API 29-36) W^X 与 SELinux 限制，实现手机端内置完整 Linux (Bash)
# ==============================================================================

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Pi Remote: 方案一 (内置完整 Linux / GNU Bash) 自动化配置" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Resolve-Path "$ScriptDir\.."
$JniArm64Dir = "$ProjectRoot\app\src\main\jniLibs\arm64-v8a"

Write-Host "[1/5] 检查原生库目录: $JniArm64Dir" -ForegroundColor Yellow
if (-not (Test-Path $JniArm64Dir)) {
    New-Item -ItemType Directory -Path $JniArm64Dir -Force | Out-Null
    Write-Host "  + 已创建目录 $JniArm64Dir" -ForegroundColor Gray
}

# 辅助下载函数
function Download-BinaryIfMissing {
    param(
        [string]$TargetFile,
        [string[]]$Urls,
        [string]$Description
    )

    if (Test-Path $TargetFile) {
        $sizeMb = (Get-Item $TargetFile).Length / 1MB
        Write-Host "  + 检测到现有 $Description ($TargetFile, 大小: $('{0:N2}' -f $sizeMb) MB)" -ForegroundColor Green
        return
    }

    Write-Host "  * 正在准备下载 $Description..." -ForegroundColor Cyan
    $success = $false
    foreach ($url in $Urls) {
        try {
            Write-Host "  * 尝试从 $url 下载..." -ForegroundColor Gray
            Invoke-WebRequest -Uri $url -OutFile $TargetFile -UseBasicParsing -TimeoutSec 60
            if (Test-Path $TargetFile) {
                $size = (Get-Item $TargetFile).Length
                if ($size -gt 10000) {
                    Write-Host "  + 成功下载 $Description ($('{0:N2}' -f ($size / 1MB)) MB)" -ForegroundColor Green
                    $success = $true
                    break
                } else {
                    Remove-Item -Path $TargetFile -Force -ErrorAction SilentlyContinue
                }
            }
        } catch {
            Write-Host "  ! 下载失败: $($_.Exception.Message)" -ForegroundColor Yellow
            Remove-Item -Path $TargetFile -Force -ErrorAction SilentlyContinue
        }
    }

    if (-not $success) {
        Write-Host "  ! 未能自动下载 $Description，您可以手动下载 aarch64 原生文件放入: $TargetFile" -ForegroundColor Red
    }
}

Write-Host "[2/5] 校验与下载 Linux 核心原生 ELF 运行时..." -ForegroundColor Yellow

# 1. GNU Bash aarch64
$bashTarget = "$JniArm64Dir\libbash.so"
$bashUrls = @(
    "https://fastly.jsdelivr.net/gh/andrew-d/static-binaries@master/binaries/linux/aarch64/bash",
    "https://ghproxy.net/https://github.com/robxu9/bash-static/releases/download/5.2.015-1.2.3/bash-linux-aarch64",
    "https://raw.githubusercontent.com/andrew-d/static-binaries/master/binaries/linux/aarch64/bash",
    "https://github.com/robxu9/bash-static/releases/download/5.2.015-1.2.3/bash-linux-aarch64"
)
Download-BinaryIfMissing -TargetFile $bashTarget -Urls $bashUrls -Description "GNU Bash (libbash.so)"

# 2. BusyBox aarch64 (100+ Linux 基础工具)
$busyboxTarget = "$JniArm64Dir\libbusybox.so"
$busyboxUrls = @(
    "https://fastly.jsdelivr.net/gh/andrew-d/static-binaries@master/binaries/linux/aarch64/busybox",
    "https://busybox.net/downloads/binaries/1.31.0-defconfig-multiarch-musl/busybox-armv8l",
    "https://raw.githubusercontent.com/andrew-d/static-binaries/master/binaries/linux/aarch64/busybox"
)
Download-BinaryIfMissing -TargetFile $busyboxTarget -Urls $busyboxUrls -Description "BusyBox 多工具箱 (libbusybox.so)"

# 3. PRoot aarch64 (用户态沙箱虚拟化)
$prootTarget = "$JniArm64Dir\libproot.so"
$prootUrls = @(
    "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot",
    "https://fastly.jsdelivr.net/gh/skirsten/proot-portable-android-binaries@master/aarch64/proot",
    "https://ghproxy.net/https://raw.githubusercontent.com/skirsten/proot-portable-android-binaries/master/aarch64/proot",
    "https://raw.githubusercontent.com/skirsten/proot-portable-android-binaries/master/aarch64/proot"
)
Download-BinaryIfMissing -TargetFile $prootTarget -Urls $prootUrls -Description "PRoot 用户态沙箱 (libproot.so)"

# 4. Node.js (libnode.so)
$nodeTarget = "$JniArm64Dir\libnode.so"
if (Test-Path $nodeTarget) {
    $fileSize = (Get-Item $nodeTarget).Length / 1MB
    Write-Host "  + 检测到现有 Node.js 运行时 (大小: $('{0:N2}' -f $fileSize) MB)" -ForegroundColor Green
} else {
    Write-Host "  * 正在调用 setup-native-node.ps1 下载 libnode.so..." -ForegroundColor Yellow
    & "$ScriptDir\setup-native-node.ps1"
}

Write-Host "[3/5] 检查 Java 编译环境..." -ForegroundColor Yellow
function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }
    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jre",
        "D:\Program Files\Android\Android Studio\jbr",
        "D:\Program Files\Android\Android Studio\jre"
    )
    $candidates += Get-ChildItem "C:\Program Files\Java\jdk*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $candidates += Get-ChildItem "$env:USERPROFILE\.jdks\*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    foreach ($cand in $candidates) {
        if ($cand -and (Test-Path "$cand\bin\java.exe")) { return $cand }
    }
    return $null
}

$foundJava = Find-JavaHome
if ($foundJava) {
    Write-Host "  + 自动定位 Java: $foundJava" -ForegroundColor Green
    $env:JAVA_HOME = $foundJava
    $env:PATH = "$foundJava\bin;$env:PATH"
}

Write-Host "[4/5] 编译包含完整 Linux 原生库的 APK..." -ForegroundColor Yellow
Set-Location $ProjectRoot
if (Test-Path ".\gradlew.bat") {
    & ".\gradlew.bat" assembleDebug
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  + APK 编译成功！" -ForegroundColor Green
    } else {
        Write-Host "  ! Gradle 编译失败，退出码: $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Write-Host "[5/5] 安装至 Android 设备并启动..." -ForegroundColor Yellow
function Find-Adb {
    if (Get-Command adb -ErrorAction SilentlyContinue) { return "adb" }
    $localProp = "$ProjectRoot\local.properties"
    if (Test-Path $localProp) {
        $sdkLine = Get-Content $localProp | Where-Object { $_ -match "^sdk\.dir\s*=" }
        if ($sdkLine) {
            $sdkPath = ($sdkLine -split "=", 2)[1].Trim().Replace('\\', '\')
            $adbCandidate = "$sdkPath\platform-tools\adb.exe"
            if (Test-Path $adbCandidate) { return $adbCandidate }
        }
    }
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

$adbCmd = Find-Adb
$ApkPath = "$ProjectRoot\app\build\outputs\apk\debug\app-debug.apk"

if ($adbCmd) {
    try {
        $devices = & $adbCmd devices 2>$null
        if ($devices -match "device\b") {
            Write-Host "  * 正在将包含完整 Linux 运行时的 APK 安装至手机..." -ForegroundColor Cyan
            & $adbCmd install -r $ApkPath
            Write-Host "  + 安装完成！正在拉起 Pi Remote..." -ForegroundColor Green
            & $adbCmd shell am start -n "com.piremote.app/.MainActivity" | Out-Null
            Write-Host "  + Pi Remote 已在手机上拉起！" -ForegroundColor Green
            Write-Host "  + 手机安装后已将 libbash.so, libbusybox.so, libnode.so 自动提取至 nativeLibraryDir，具备完整免 Root 执行权限！" -ForegroundColor Green
        } else {
            Write-Host "  * 暂无 USB 调试设备连接。APK 生成于: $ApkPath" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  ! ADB 异常: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "  * 未定位到 adb，APK 文件位于: $ApkPath" -ForegroundColor Yellow
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  方案一配置流程结束！" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
