@echo off
REM ========================================================================
REM Stop Voice Dialogue Servers
REM ========================================================================

echo Stopping all Python servers...
taskkill /F /IM python.exe >nul 2>&1
echo.
echo Servers stopped.
echo.
pause
