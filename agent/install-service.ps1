<#
    Registers the PC agent as a Windows service via nssm, running as LocalSystem.

    Must run elevated. The companion install-service.cmd self-elevates.

    Running as LocalSystem means no stored password and start-at-boot without a
    login, but the process inherits SYSTEM's environment instead of the desktop
    user's. The agent compensates by injecting USERPROFILE / APPDATA / PATH into
    the shell it spawns (see shellEnv + pathPrepend in agent/config.json).
#>

$ErrorActionPreference = 'Stop'

$ServiceName = 'PiRemoteAgent'
$AgentDir    = 'D:\Program\Pi_Agent\agent'
$NodeExe     = 'C:\Users\Orion\AppData\Local\pi-node\current\node.exe'
$LogDir      = 'D:\Program\Pi_Agent\logs'
$ServerJs    = Join-Path $AgentDir 'server.js'
$Stdout      = Join-Path $LogDir 'agent.log'
$Stderr      = Join-Path $LogDir 'agent.err.log'

function Write-Step($text) { Write-Host "`n==> $text" -ForegroundColor Green }
function Write-Warn($text) { Write-Host "    $text" -ForegroundColor Yellow }

Write-Step 'checking elevation'
$identity  = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'This script must run elevated. Use install-service.cmd instead.' -ForegroundColor Red
    exit 1
}
Write-Host "    ok ($($identity.Name))"

Write-Step 'checking prerequisites'
foreach ($path in @($NodeExe, $ServerJs)) {
    if (-not (Test-Path $path)) {
        Write-Host "Missing: $path" -ForegroundColor Red
        exit 1
    }
}
Write-Host "    node   : $NodeExe"
Write-Host "    server : $ServerJs"

Write-Step 'locating nssm'
$nssm = $null
foreach ($candidate in @(
    (Get-Command nssm.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source),
    'C:\Program Files\nssm\win64\nssm.exe',
    'C:\Program Files\nssm\nssm.exe',
    'C:\ProgramData\chocolatey\bin\nssm.exe'
)) {
    if ($candidate -and (Test-Path $candidate)) { $nssm = $candidate; break }
}

if (-not $nssm) {
    Write-Warn 'not found, installing via winget'
    winget install --id NSSM.NSSM --accept-source-agreements --accept-package-agreements --silent | Out-Null
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
    foreach ($candidate in @(
        (Get-Command nssm.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source),
        'C:\Program Files\nssm\win64\nssm.exe',
        'C:\Program Files\nssm\nssm.exe'
    )) {
        if ($candidate -and (Test-Path $candidate)) { $nssm = $candidate; break }
    }
}
if (-not $nssm) {
    Write-Host 'Could not locate nssm after install. Install it manually and re-run.' -ForegroundColor Red
    exit 1
}
Write-Host "    $nssm"

Write-Step 'creating log directory'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Write-Host "    $LogDir"

Write-Step "removing any existing $ServiceName service"
if (Get-Service $ServiceName -ErrorAction SilentlyContinue) {
    & $nssm stop $ServiceName | Out-Null
    Start-Sleep -Seconds 2
    & $nssm remove $ServiceName confirm | Out-Null
    Start-Sleep -Seconds 1
    Write-Host '    removed'
} else {
    Write-Host '    none'
}

Write-Step 'registering the service'
& $nssm install $ServiceName $NodeExe $ServerJs | Out-Null
& $nssm set $ServiceName DisplayName 'Pi Remote Agent' | Out-Null
& $nssm set $ServiceName Description 'Remote terminal agent (PowerShell / pi) for Pi_Agent' | Out-Null
& $nssm set $ServiceName AppDirectory $AgentDir | Out-Null
& $nssm set $ServiceName AppStdout $Stdout | Out-Null
& $nssm set $ServiceName AppStderr $Stderr | Out-Null
& $nssm set $ServiceName AppRotateFiles 1 | Out-Null
& $nssm set $ServiceName AppRotateOnline 1 | Out-Null
& $nssm set $ServiceName AppRotateBytes 1048576 | Out-Null
& $nssm set $ServiceName Start SERVICE_AUTO_START | Out-Null
& $nssm set $ServiceName ObjectName LocalSystem | Out-Null
& $nssm set $ServiceName AppStopMethodConsole 5000 | Out-Null
& $nssm set $ServiceName AppStopMethodWindow 5000 | Out-Null
& $nssm set $ServiceName AppStopMethodThreads 5000 | Out-Null
Write-Host '    ok'

Write-Step 'starting the service'
Start-Service $ServiceName
Start-Sleep -Seconds 5

$service = Get-Service $ServiceName
Write-Host "    status: $($service.Status)"

Write-Step 'service log'
if (Test-Path $Stdout) {
    Get-Content $Stdout -Tail 25 | ForEach-Object { Write-Host "    $_" }
} else {
    Write-Warn "no log yet at $Stdout"
}
if ((Test-Path $Stderr) -and (Get-Item $Stderr).Length -gt 0) {
    Write-Warn '--- stderr ---'
    Get-Content $Stderr -Tail 15 | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
}

Write-Host ''
if ($service.Status -eq 'Running') {
    $log = if (Test-Path $Stdout) { Get-Content $Stdout -Raw } else { '' }
    if ($log -match 'connected as agent') {
        Write-Host 'SUCCESS: the agent is running as a service and connected to the relay.' -ForegroundColor Green
    } else {
        Write-Host 'WARNING: the service is running but has not reported a relay connection yet.' -ForegroundColor Yellow
        Write-Host '         Check the log above.' -ForegroundColor Yellow
    }
} else {
    Write-Host "FAILED: service status is $($service.Status)." -ForegroundColor Red
    exit 1
}
