@echo off
chcp 65001 >nul
title AlphaBlock - Scratch fuer Alpha Mini (5. Klasse)
echo ========================================================
echo       ALPHABLOCK - SCRATCH FUER ALPHA MINI
echo               Fuer die 5. Klasse
echo ========================================================
echo.

cd /d "%~dp0"
set "PYTHON_EXE=python"

if exist "C:\Python314\python.exe" (
    set "PYTHON_EXE=C:\Python314\python.exe"
)

"%PYTHON_EXE%" --version >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] Python wurde auf diesem Computer nicht gefunden!
    echo Bitte stelle sicher, dass Python 3 installiert ist.
    pause
    exit /b 1
)

echo [1/2] Starte AlphaBlock Schul-Server...
start "AlphaBlock Server" cmd /k "%PYTHON_EXE% -u alphablock_server.py"

echo [2/2] Oeffne Programmierumgebung im Webbrowser...
ping 127.0.0.1 -n 3 >nul
start http://localhost:8080

echo.
echo ========================================================
echo  ALPHABLOCK WURDE ERFOLGREICH GESTARTET!
echo.
echo  - Der Webbrowser oeffnet sich jetzt mit AlphaBlock
echo  - Roboter per USB angeschlossen? -^> Automatisch erkannt!
echo  - Kein Roboter angeschlossen?   -^> Bildschirm-Simulator laeuft!
echo ========================================================
echo.
echo Dieses Fenster kann minimiert oder geschlossen werden.
pause
