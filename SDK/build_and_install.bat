@echo off
REM ========================================================================
REM Build and Install Voice Dialogue App
REM ========================================================================

set PROJECT_DIR=%~dp0
if exist "%PROJECT_DIR%..\tools\scrcpy\adb.exe" (
    set "ADB_PATH=%PROJECT_DIR%..\tools\scrcpy\adb.exe"
) else if exist "%PROJECT_DIR%tools\scrcpy\adb.exe" (
    set "ADB_PATH=%PROJECT_DIR%tools\scrcpy\adb.exe"
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
) else (
    set "ADB_PATH=adb.exe"
)
if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)

echo ========================================
echo Building and Installing App
echo ========================================
echo.

REM Check if robot is connected
echo Checking robot connection...
"%ADB_PATH%" devices | findstr /i "device" >nul 2>&1
if errorlevel 1 (
    echo ERROR: No device connected. Please connect the robot via USB.
    pause
    exit /b 1
)
echo Robot connected!
echo.

REM Build the app
echo Building app (this may take a minute)...
cd /d "%PROJECT_DIR%"
call gradlew.bat :sdkdemo:assembleDebug
if errorlevel 1 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)
echo Build successful!
echo.

REM Install the app
echo Installing app on robot...
"%ADB_PATH%" install -r "%PROJECT_DIR%sdkdemo\build\outputs\apk\debug\sdkdemo-debug.apk"
if errorlevel 1 (
    echo ERROR: Install failed!
    pause
    exit /b 1
)
echo Install successful!
echo.

echo ========================================
echo Done! App is installed on the robot.
echo Run launch_voice_dialogue.bat to start servers and launch the app.
echo ========================================
pause
