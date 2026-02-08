# Robot Voice Dialogue - PowerShell Startup Script
# Run with: powershell -ExecutionPolicy Bypass -File START_SERVERS.ps1

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Robot Voice Dialogue - Startup Script" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$ADB = "C:\Users\wissem.malleh\Downloads\scrcpy-win64-v3.3.4\scrcpy-win64-v3.3.4\adb.exe"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$BaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Step 1: ADB Reverse
Write-Host "[1/5] Setting up ADB reverse port forwarding..." -ForegroundColor Yellow
& $ADB reverse tcp:8080 tcp:8080
& $ADB reverse tcp:5000 tcp:5000
Write-Host "      Done." -ForegroundColor Green

# Step 2: Start TTS Server
Write-Host "[2/5] Starting TTS Server (port 5000)..." -ForegroundColor Yellow
Start-Process -FilePath "python" -ArgumentList "-u", "edge_tts_server.py" -WorkingDirectory "$BaseDir\tts_server" -WindowStyle Normal
Start-Sleep -Seconds 2
Write-Host "      Done." -ForegroundColor Green

# Step 3: Start LLM Server
Write-Host "[3/5] Starting LLM Server (port 8080)..." -ForegroundColor Yellow
Start-Process -FilePath "python" -ArgumentList "-u", "server.py" -WorkingDirectory "$BaseDir\llm_server" -WindowStyle Normal
Start-Sleep -Seconds 3
Write-Host "      Done." -ForegroundColor Green

# Step 4: Build and Install
Write-Host "[4/5] Building and installing app..." -ForegroundColor Yellow
Set-Location $BaseDir
& .\gradlew.bat :sdkdemo:installDebug
Write-Host "      Done." -ForegroundColor Green

# Step 5: Launch App
Write-Host "[5/5] Launching app on robot..." -ForegroundColor Yellow
& $ADB shell am start -n com.ubtrobot.mini.sdkdemo/.MainActivity
Write-Host "      Done." -ForegroundColor Green

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " All services running!" -ForegroundColor Green
Write-Host " Navigate to Voice Dialogue in the app." -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
