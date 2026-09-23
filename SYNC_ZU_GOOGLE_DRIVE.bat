@echo off
chcp 65001 >nul
title Alpha Mini 2 - Sync zu Google Drive

set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%sync_to_google_drive.ps1"

echo.
pause
