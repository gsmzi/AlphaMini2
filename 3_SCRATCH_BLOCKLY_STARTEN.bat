@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title AlphaBlock - Scratch fuer Alpha Mini (5. Klasse)
echo ========================================================
echo       ALPHABLOCK - SCRATCH FUER ALPHA MINI
echo               Fuer die 5. Klasse
echo ========================================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM Pruefe, ob das Skript in einer unentpackten ZIP ausgefuehrt wird
if not exist "%SCRIPT_DIR%alphablock_server.py" (
    echo [FEHLER] alphablock_server.py wurde nicht gefunden!
    echo.
    echo ============================================================================
    echo HINWEIS: Hast du die ZIP-Datei vor dem Start ENTPACKT?
    echo.
    echo Wenn du die Batch-Datei direkt in der ZIP-Datei oeffnest, kann Windows
    echo die benoetigten Programmdateien nicht finden.
    echo.
    echo LOESUNG:
    echo 1. Klicke mit der RECHTEN Maustaste auf die ZIP-Datei.
    echo 2. Waehle "Alle extrahieren..." und bestaetige mit "Extrahieren".
    echo 3. Oeffne den entpackten Ordner und starte die Datei dort erneut!
    echo ============================================================================
    echo.
    pause
    exit /b 1
)

echo [1/3] Suche nach Python 3 auf diesem PC...

set "PYTHON_CMD="

REM 1. Teste offiziellen Windows Python Launcher (py -3)
py -3 --version >nul 2>&1
if not errorlevel 1 (
    set "PYTHON_CMD=py -3"
    goto PYTHON_FOUND
)

REM 2. Teste python im PATH
python -c "import sys; sys.exit(0)" >nul 2>&1
if not errorlevel 1 (
    set "PYTHON_CMD=python"
    goto PYTHON_FOUND
)

REM 3. Suche in Benutzerordner (Standard-Installationspfad von python.org)
for /d %%I in ("%LOCALAPPDATA%\Programs\Python\Python3*") do (
    if exist "%%I\python.exe" (
        set "PYTHON_CMD=%%I\python.exe"
    )
)
if defined PYTHON_CMD goto PYTHON_FOUND

REM 4. Suche in Program Files (Systemweite Installation)
for /d %%I in ("%ProgramFiles%\Python3*") do (
    if exist "%%I\python.exe" (
        set "PYTHON_CMD=%%I\python.exe"
    )
)
if defined PYTHON_CMD goto PYTHON_FOUND

for /d %%I in ("%ProgramFiles(x86)%\Python3*") do (
    if exist "%%I\python.exe" (
        set "PYTHON_CMD=%%I\python.exe"
    )
)
if defined PYTHON_CMD goto PYTHON_FOUND

REM 5. Suche in C:\Python3*
for /d %%I in ("C:\Python3*") do (
    if exist "%%I\python.exe" (
        set "PYTHON_CMD=%%I\python.exe"
    )
)
if defined PYTHON_CMD goto PYTHON_FOUND

REM 6. Suche ueber Windows-Registry
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "(Get-ItemProperty 'HKCU:\Software\Python\PythonCore\*\InstallPath', 'HKLM:\Software\Python\PythonCore\*\InstallPath' -ErrorAction SilentlyContinue).'(default)'" 2^>nul`) do (
    if exist "%%Ppython.exe" (
        set "PYTHON_CMD=%%Ppython.exe"
    )
    if exist "%%P\python.exe" (
        set "PYTHON_CMD=%%P\python.exe"
    )
)
if defined PYTHON_CMD goto PYTHON_FOUND

:PYTHON_FOUND
if not defined PYTHON_CMD (
    echo.
    echo ============================================================================
    echo [FEHLER] Python 3 wurde auf diesem Computer nicht gefunden!
    echo ============================================================================
    echo.
    echo Fuer die Scratch-Programmierung wird Python 3 benoetigt.
    echo.
    echo Falls du Python bereits heruntergeladen hast:
    echo 1. Starte die heruntergeladene Python-Installationsdatei erneut.
    echo 2. Waehle "Modify" (Aendern) oder deinstalliere und installiere neu.
    echo 3. WICHTIG: Setze ganz unten den Haken bei:
    echo    [X] "Add python.exe to PATH"  (oder: "Python zum Pfad hinzufuegen")
    echo 4. Klicke auf "Install Now" und starte danach diese Datei erneut!
    echo.
    echo Schnelle Alternative (Windows Terminal / CMD):
    echo    winget install Python.Python.3.12
    echo ============================================================================
    echo.
    pause
    exit /b 1
)

echo [OK] Python gefunden: !PYTHON_CMD!
echo.
echo [2/3] Starte AlphaBlock Schul-Server...

if "!PYTHON_CMD!"=="py -3" (
    start "AlphaBlock Server" cmd /k "py -3 -u alphablock_server.py"
) else (
    start "AlphaBlock Server" cmd /k ""!PYTHON_CMD!" -u alphablock_server.py"
)

echo [3/3] Oeffne Programmierumgebung im Webbrowser...
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
