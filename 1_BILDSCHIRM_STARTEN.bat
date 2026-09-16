@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Alpha Mini 2 - Bildschirmsteuerung (scrcpy)
echo ========================================================
echo       ALPHA MINI 2 - BILDSCHIRM-STEUERUNG
echo ========================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "ADB_PATH=%SCRIPT_DIR%tools\scrcpy\adb.exe"
set "SCRCPY_PATH=%SCRIPT_DIR%tools\scrcpy\scrcpy.exe"

REM Pruefe, ob das Skript in einer unentpackten ZIP ausgefuehrt wird oder tools fehlt
if not exist "%SCRCPY_PATH%" goto ERR_NO_SCRCPY

echo [1/2] Suche nach verbundenem Alpha Mini Roboter...
echo       Bitte Roboter per USB anschliessen und einschalten.
echo.

:CHECK_DEVICE
"%ADB_PATH%" devices | findstr /R /C:"[a-zA-Z0-9].*device$" >nul 2>&1
if errorlevel 1 goto RETRY_DEVICE

echo [OK] Roboter erfolgreich gefunden!
echo.
echo [2/2] Starte Bildschirmuebertragung...
echo       Tipp: Im geoeffneten Fenster kannst du den Roboter direkt mit der Maus bedienen!
echo.

start "" "%SCRCPY_PATH%" --no-audio

echo ========================================================
echo Das Roboter-Display sollte jetzt auf deinem Monitor sein!
echo.
echo Naechste Schritte am Roboter-Display:
echo 1. Pfeil unten links anklicken, um die Apps zu sehen
echo 2. Einstellungen (Settings) oeffnen
echo 3. Sprache auf Deutsch umstellen
echo 4. Mit dem heimischen WLAN verbinden
echo ========================================================
echo.
echo Dieses Fenster kann geschlossen werden.
pause
exit /b 0

:RETRY_DEVICE
echo [!] Kein Roboter erkannt.
echo.
echo Bitte pruefen:
echo 1. Klappe am Rucksack des Roboters geoeffnet?
echo 2. USB-Kabel fest am Roboter und am PC angeschlossen?
echo 3. Roboter eingeschaltet?
echo.
echo Druecke eine beliebige Taste, um die Verbindung erneut zu pruefen...
pause >nul
echo Suche erneut...
goto CHECK_DEVICE

:ERR_NO_SCRCPY
echo [FEHLER] scrcpy.exe wurde nicht im Ordner tools\scrcpy gefunden!
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
