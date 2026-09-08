@echo off
chcp 65001 >nul
title Alpha Mini 2 - App Installation
echo ========================================================
echo       ALPHA MINI 2 - APP INSTALLATION
echo ========================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "ADB_PATH=%SCRIPT_DIR%tools\scrcpy\adb.exe"

if not exist "%ADB_PATH%" (
    echo [FEHLER] adb.exe wurde nicht in tools\scrcpy gefunden!
    pause
    exit /b 1
)

REM Pruefe Geraeteverbindung
"%ADB_PATH%" devices | findstr /R /C:"[a-zA-Z0-9].*device$" >nul 2>&1
if errorlevel 1 (
    echo [!] Kein Roboter ueber USB erkannt.
    echo Bitte schliesse den Roboter per USB an und schalte ihn ein.
    echo.
    pause
    exit /b 1
)

echo [1/3] Suche nach APK-Installationsdatei...

set "APK_FILE="

REM 1. Suche im Hauptordner
for %%F in ("%SCRIPT_DIR%*.apk") do (
    set "APK_FILE=%%F"
    goto FOUND_APK
)

REM 2. Suche im SDK-Build-Ordner
if exist "%SCRIPT_DIR%SDK\sdkdemo\build\outputs\apk\debug\sdkdemo-debug.apk" (
    set "APK_FILE=%SCRIPT_DIR%SDK\sdkdemo\build\outputs\apk\debug\sdkdemo-debug.apk"
    goto FOUND_APK
)

:FOUND_APK
if "%APK_FILE%"=="" (
    echo.
    echo [!] Keine APK-Datei gefunden!
    echo.
    echo So geht es:
    echo 1. Fordere vom Verkaeufer (Terra Robotics) die fertig gebaute Datei an:
    echo    z. B. "sdkdemo-debug.apk"
    echo 2. Kopiere die .apk-Datei einfach hier in diesen Hauptordner.
    echo 3. Starte dieses Skript (2_APP_INSTALLIEREN.bat) erneut!
    echo.
    echo TIPP: Du kannst jede .apk-Datei auch einfach mit gedrueckter Maustaste
    echo direkt in das geoeffnete scrcpy-Bildschirmfenster ziehen (Drag and Drop)!
    echo.
    pause
    exit /b 1
)

echo [OK] Gefunden: %APK_FILE%
echo.
echo [2/3] Installiere App auf dem Roboter...
"%ADB_PATH%" install -r "%APK_FILE%"
if errorlevel 1 (
    echo.
    echo [FEHLER] Installation fehlgeschlagen!
    pause
    exit /b 1
)

echo [OK] App erfolgreich installiert!
echo.
echo [3/3] Berechtigungen fuer Mikrofon setzen...
"%ADB_PATH%" shell pm grant com.ubtrobot.mini.sdkdemo android.permission.RECORD_AUDIO >nul 2>&1
echo [OK] Mikrofon-Berechtigung erteilt.
echo.
echo ========================================================
echo Fertig! Die App ist auf dem Roboter installiert.
echo Du kannst sie nun auf dem Display des Roboters antippen!
echo ========================================================
pause
