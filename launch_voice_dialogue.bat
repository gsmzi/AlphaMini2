@echo off
REM ========================================================================
REM Voice Dialogue App Launcher
REM Starts all servers and launches the app on the robot
REM ========================================================================

set ADB_PATH=C:\Users\wissem.malleh\AppData\Local\Android\Sdk\platform-tools\adb.exe
set PROJECT_DIR=%~dp0

echo ========================================
echo Voice Dialogue App Launcher
echo ========================================
echo.

REM Check if robot is connected
echo [1/6] Checking robot connection...
"%ADB_PATH%" devices | findstr /i "device" >nul 2>&1
if errorlevel 1 (
    echo ERROR: No device connected. Please connect the robot via USB.
    pause
    exit /b 1
)
echo Robot connected!
echo.

REM Kill any existing Python processes
echo [2/6] Stopping any existing Python servers...
taskkill /F /IM python.exe >nul 2>&1
timeout /t 2 /nobreak >nul
echo Done.
echo.

REM Start TTS Server
echo [3/6] Starting TTS Server (port 5000)...
start "TTS Server" cmd /c "cd /d "%PROJECT_DIR%tts_server" && python -u server.py"
timeout /t 3 /nobreak >nul
echo TTS Server started.
echo.

REM Start LLM Server
echo [4/6] Starting LLM Server (port 8080)...
start "LLM Server" cmd /c "cd /d "%PROJECT_DIR%llm_server" && python -u server.py"
timeout /t 5 /nobreak >nul
echo LLM Server started.
echo.

REM Setup ADB reverse port forwarding
echo [5/6] Setting up ADB port forwarding...
"%ADB_PATH%" reverse tcp:5000 tcp:5000
"%ADB_PATH%" reverse tcp:8080 tcp:8080
echo Port forwarding configured.
echo.

REM Check server health
echo Checking server health...
curl -s http://127.0.0.1:5000/health >nul 2>&1
if errorlevel 1 (
    echo WARNING: TTS server may not be ready yet.
) else (
    echo TTS Server: OK
)
curl -s http://127.0.0.1:8080/health >nul 2>&1
if errorlevel 1 (
    echo WARNING: LLM server may not be ready yet.
) else (
    echo LLM Server: OK
)
echo.

REM Launch the app
echo [6/6] Launching Voice Dialogue App...
"%ADB_PATH%" shell am start -n com.ubtrobot.mini.sdkdemo/.MainActivity
echo.

echo ========================================
echo Setup Complete!
echo ========================================
echo.
echo The app is now running on the robot.
echo.
echo Available features:
echo   - Voice Dialogue V3 (Continuous) - PURPLE button
echo   - Voice Dialogue V2 (Low Latency) - Blue button
echo   - Voice Dialogue (Original) - Green button
echo.
echo Commands the robot understands:
echo   - "dance" / "tanz" - Robot dances
echo   - "wave" / "winke" - Robot waves
echo   - "hands up" / "hande hoch" - Robot raises hands
echo   - "clap" / "klatsch" - Robot claps
echo   - "what time is it" / "wie spaet ist es"
echo   - "how's the weather" / "wie ist das wetter"
echo   - "goodbye" / "tschuess" - End conversation
echo.
echo Press any key to close this window (servers will keep running)...
pause >nul
