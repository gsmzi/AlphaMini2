@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Alpha Mini 2 - App Installation
echo ========================================================
echo       ALPHA MINI 2 - APP INSTALLATION
echo ========================================================
echo.

REM Automatische Unterstuetzung fuer Netzwerkpfade (UNC wie \\Server\Freigabe)
pushd "%~dp0"
set "SCRIPT_DIR=%CD%\"
set "ADB_PATH=%SCRIPT_DIR%tools\scrcpy\adb.exe"

REM Pruefe, ob das Skript in einer unentpackten ZIP ausgefuehrt wird oder tools fehlt
if not exist "%ADB_PATH%" goto ERR_NO_ADB

REM Pruefe Geraeteverbindung
"%ADB_PATH%" devices | findstr /R /C:"[a-zA-Z0-9].*device$" >nul 2>&1
if errorlevel 1 goto ERR_NO_ROBOT

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

REM 3. Automatischer Download von GitHub, falls lokal noch nicht vorhanden
echo Keine lokale APK gefunden. Versuche automatischen Download von GitHub...
set "DOWNLOAD_URL=https://github.com/gsmzi/AlphaMini2/releases/download/latest/sdkdemo-debug.apk"
set "DOWNLOAD_TARGET=%SCRIPT_DIR%sdkdemo-debug.apk"

where curl.exe >nul 2>&1
if not errorlevel 1 (
    echo [Download] Lade sdkdemo-debug.apk herunter (ca. 200 MB)...
    curl.exe -L --progress-bar -f -o "%DOWNLOAD_TARGET%" "%DOWNLOAD_URL%"
)

if not exist "%DOWNLOAD_TARGET%" (
    echo [Download] Lade sdkdemo-debug.apk via PowerShell herunter...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('%DOWNLOAD_URL%', '%DOWNLOAD_TARGET%')" >nul 2>&1
)

if exist "%DOWNLOAD_TARGET%" (
    set "APK_FILE=%DOWNLOAD_TARGET%"
    echo [OK] Automatisch heruntergeladen!
    goto FOUND_APK
)

:FOUND_APK
if "%APK_FILE%"=="" goto ERR_NO_APK

echo [OK] Gefunden: %APK_FILE%
echo.
echo [2/3] Installiere App auf dem Roboter...

set "TARGET_APK=%APK_FILE%"
if "%APK_FILE:~0,2%"=="\\" (
    echo Kopiere temporaer fuer fehlerfreie Netzwerk-Installation...
    copy /y "%APK_FILE%" "%TEMP%\sdkdemo-debug.apk" >nul 2>&1
    if exist "%TEMP%\sdkdemo-debug.apk" set "TARGET_APK=%TEMP%\sdkdemo-debug.apk"
)

"%ADB_PATH%" install -r "%TARGET_APK%"
set "INSTALL_STATUS=%ERRORLEVEL%"
if exist "%TEMP%\sdkdemo-debug.apk" del "%TEMP%\sdkdemo-debug.apk" >nul 2>&1

if not "%INSTALL_STATUS%"=="0" goto ERR_INSTALL_FAILED

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
popd
pause
exit /b 0

:ERR_NO_ADB
popd
echo [FEHLER] adb.exe wurde nicht in tools\scrcpy gefunden!
echo.
echo ============================================================================
echo HINWEIS: Hast du die ZIP-Datei vor dem Start ENTPACKT?
echo.
echo Wenn du die Batch-Datei direkt in der ZIP-Datei oeffnest, kann Windows
echo die benoetigten Hilfsprogramme nicht laden.
echo.
echo LOESUNG:
echo 1. Klicke mit der RECHTEN Maustaste auf die ZIP-Datei.
echo 2. Waehle "Alle extrahieren..." und bestaetige mit "Extrahieren".
echo 3. Oeffne den entpackten Ordner und starte die Datei dort erneut!
echo ============================================================================
echo.
pause
exit /b 1

:ERR_NO_ROBOT
popd
echo [WARNUNG] Kein Roboter ueber USB erkannt.
echo Bitte schliesse den Roboter per USB an und schalte ihn ein.
echo.
pause
exit /b 1

:ERR_NO_APK
popd
echo.
echo [FEHLER] Keine APK-Datei gefunden und automatischer Download nicht moeglich!
echo.
echo So kannst du die Datei manuell herunterladen:
echo 1. Direkter Download-Link der APK:
echo    https://github.com/gsmzi/AlphaMini2/releases/download/latest/sdkdemo-debug.apk
echo 2. Kopiere die heruntergeladene Datei "sdkdemo-debug.apk" in diesen Hauptordner.
echo 3. Starte dieses Skript erneut!
echo.
echo TIPP: Du kannst die heruntergeladene .apk-Datei auch einfach per Drag and Drop
echo direkt in das geoeffnete scrcpy-Bildschirmfenster des Roboters ziehen!
echo.
pause
exit /b 1

:ERR_INSTALL_FAILED
popd
echo.
echo [FEHLER] Installation fehlgeschlagen!
pause
exit /b 1
