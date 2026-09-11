@echo off
REM Removes the Pi Remote Agent service. Self-elevating.
REM Double-click and approve the UAC prompt.

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set SERVICE=PiRemoteAgent

where nssm.exe >nul 2>&1
if %errorlevel% neq 0 (
    set NSSM=C:\Program Files\nssm\win64\nssm.exe
) else (
    for /f "delims=" %%i in ('where nssm.exe') do set NSSM=%%i
)

if not exist "%NSSM%" (
    echo Could not find nssm.exe. Stopping/removing with sc.exe instead.
    sc stop %SERVICE%
    sc delete %SERVICE%
    pause
    exit /b
)

echo Stopping and removing %SERVICE%...
"%NSSM%" stop %SERVICE%
"%NSSM%" remove %SERVICE% confirm
echo Done.
pause
