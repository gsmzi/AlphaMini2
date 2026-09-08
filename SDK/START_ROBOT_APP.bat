@echo off
echo ============================================
echo  Robot Voice Dialogue - Startup Script
echo ============================================
echo.

set "PROJECT_DIR=%~dp0"
if exist "%PROJECT_DIR%..\tools\scrcpy\adb.exe" (
    set "ADB_PATH=%PROJECT_DIR%..\tools\scrcpy\adb.exe"
    set "SCRCPY_PATH=%PROJECT_DIR%..\tools\scrcpy\scrcpy.exe"
) else if exist "%PROJECT_DIR%tools\scrcpy\adb.exe" (
    set "ADB_PATH=%PROJECT_DIR%tools\scrcpy\adb.exe"
    set "SCRCPY_PATH=%PROJECT_DIR%tools\scrcpy\scrcpy.exe"
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    set "SCRCPY_PATH=scrcpy.exe"
) else (
    set "ADB_PATH=adb.exe"
    set "SCRCPY_PATH=scrcpy.exe"
)
if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)

echo [1/6] Starting scrcpy (no audio mode)...
start "" "%SCRCPY_PATH%" --no-audio
timeout /t 2 >nul
echo      Scrcpy started.
echo.

echo [2/6] Setting up ADB reverse port forwarding...
"%ADB_PATH%" reverse tcp:8080 tcp:8080
"%ADB_PATH%" reverse tcp:5000 tcp:5000
echo      Ports 8080 (LLM) and 5000 (TTS) forwarded.
echo.

echo [3/6] Starting Edge TTS Server (port 5000)...
start "TTS Server" cmd /k "cd /d %~dp0tts_server && python -u server.py"
timeout /t 3 >nul
echo      TTS Server started.
echo.

echo [4/6] Starting LLM Server with Vosk (port 8080)...
start "LLM Server" cmd /k "cd /d %~dp0llm_server && python -u server.py"
timeout /t 5 >nul
echo      LLM Server started (English + German Vosk models).
echo.

echo [5/6] Building and installing app on robot...
cd /d %~dp0
call gradlew.bat :sdkdemo:installDebug
echo      App installed.
echo.

echo [6/6] Launching app on robot...
"%ADB_PATH%" shell am start -n com.ubtrobot.mini.sdkdemo/.MainActivity
echo      App launched! Navigate to Voice Dialogue.
echo.

echo ============================================
echo  All services running! Press any key to exit.
echo ============================================
pause
