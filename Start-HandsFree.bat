@echo off
setlocal
title HandsFree AI - Start
echo.
echo HandsFree AI - Team MERCI
echo Connect and unlock your phone before continuing.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local.ps1" -OpenApp
set "handsfreeExit=%ERRORLEVEL%"
echo.
if not "%handsfreeExit%"=="0" (
    echo Startup needs attention. Follow the message above, then run this file again.
) else (
    echo Ready. Tap Start conversation on your phone.
    echo Keep USB connected and your laptop awake. You may close this window.
)
echo.
if /I not "%~1"=="--no-pause" pause
exit /b %handsfreeExit%
