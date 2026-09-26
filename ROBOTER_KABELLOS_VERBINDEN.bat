@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Alpha Mini 2 - Kabellos verbinden (WLAN)
echo ========================================================
echo       ALPHA MINI 2 - KABELLOS VERBINDEN (WLAN)
echo ========================================================
echo.

pushd "%~dp0"
set "SCRIPT_DIR=%CD%\"

set "ADB_PATH=%SCRIPT_DIR%tools\scrcpy\adb.exe"
if not exist "%ADB_PATH%" (
    set "ADB_PATH=%SCRIPT_DIR%dateien\tools\scrcpy\adb.exe"
)
if not exist "%ADB_PATH%" (
    set "ADB_PATH=%SCRIPT_DIR%..\tools\scrcpy\adb.exe"
)
if not exist "%ADB_PATH%" (
    set "ADB_PATH=adb.exe"
)

echo [1/3] Suche nach Roboter...

REM Pruefe ob bereits ein Geraet erkannt wird (USB oder schon per WLAN)
set "CURRENT_DEVICE="
for /f "tokens=1,2" %%A in ('"%ADB_PATH%" devices') do (
    if "%%B"=="device" (
        set "CURRENT_DEVICE=%%A"
    )
)

if defined CURRENT_DEVICE (
    echo [OK] Roboter gefunden: !CURRENT_DEVICE!
    echo.
    REM Falls das Geraet bereits eine IP:Port ist, ist es schon kabellos
    echo !CURRENT_DEVICE! | findstr /C:":5555" >nul 2>&1
    if not errorlevel 1 (
        echo ========================================================
        echo  DER ROBOTER IST BEREITS KABELLOS VERBUNDEN!
        echo  Geraet: !CURRENT_DEVICE!
        echo.
        echo  Du kannst das USB-Kabel laengst abziehen.
        echo  Alle Programme und AlphaBlock laufen kabellos!
        echo ========================================================
        popd
        pause
        exit /b 0
    )
    
    echo [2/3] Ermittle WLAN-IP-Adresse des Roboters...
    set "ROBOT_IP="
    
    REM Methode 1: getprop dhcp.wlan0.ipaddress
    for /f "tokens=*" %%I in ('"%ADB_PATH%" -s !CURRENT_DEVICE! shell getprop dhcp.wlan0.ipaddress 2^>nul') do (
        set "IP_CANDIDATE=%%I"
        set "IP_CANDIDATE=!IP_CANDIDATE:~0,15!"
        echo !IP_CANDIDATE! | findstr /R "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*" >nul 2>&1
        if not errorlevel 1 (
            set "ROBOT_IP=!IP_CANDIDATE!"
        )
    )
    
    REM Methode 2: ip route get 1
    if not defined ROBOT_IP (
        for /f "tokens=*" %%I in ('"%ADB_PATH%" -s !CURRENT_DEVICE! shell "ip -f inet addr show wlan0 | grep -oE 'inet [0-9.]+' | cut -d' ' -f2" 2^>nul') do (
            if not defined ROBOT_IP set "ROBOT_IP=%%I"
        )
    )

    if defined ROBOT_IP (
        echo [OK] WLAN-IP gefunden: !ROBOT_IP!
    ) else (
        echo [!] Konnte IP-Adresse nicht automatisch auslesen.
        set /p "ROBOT_IP=Bitte gib die IP-Adresse des Roboters manuell ein: "
    )
    
    echo.
    echo [3/3] Aktiviere kabellosen Modus auf Port 5555...
    "%ADB_PATH%" -s !CURRENT_DEVICE! tcpip 5555
    timeout /t 2 >nul
    
    echo Verbinde kabellos mit !ROBOT_IP!:5555...
    "%ADB_PATH%" connect !ROBOT_IP!:5555
    timeout /t 2 >nul
    
    REM Pruefe Erfolg
    "%ADB_PATH%" devices | findstr /C:"!ROBOT_IP!:5555" >nul 2>&1
    if not errorlevel 1 (
        echo.
        echo ========================================================
        echo  🎉 ERFOLG! ROBOTER IST JETZT KABELLOS VERBUNDEN!
        echo ========================================================
        echo.
        echo  IP-Adresse: !ROBOT_IP!:5555
        echo.
        echo  👉 DU KANNST DAS USB-KABEL JETZT ABZIEHEN!
        echo.
        echo  - AlphaBlock steuert den Roboter ab jetzt ueber WLAN.
        echo  - Kein Kabel mehr im Weg beim Laufen und Tanzen!
        echo ========================================================
        popd
        pause
        exit /b 0
    ) else (
        echo.
        echo [HINWEIS] Verbindung zu !ROBOT_IP! konnte nicht direkt bestaetigt werden.
        echo Bitte sicherstellen, dass PC und Roboter im GLEICHEN WLAN sind!
    )
) else (
    echo [!] Kein Roboter ueber USB erkannt.
    echo.
    echo Falls der Roboter bereits im WLAN ist und du seine IP-Adresse kennst:
    echo (Die IP siehst du auf dem Roboter unter Einstellungen -> WLAN -> dein Netzwerk)
    echo.
    set /p "MANUAL_IP=Roboter IP-Adresse eingeben (oder Enter zum Abbrechen): "
    if defined MANUAL_IP (
        echo Verbinde mit !MANUAL_IP!:5555...
        "%ADB_PATH%" connect !MANUAL_IP!:5555
        "%ADB_PATH%" devices
    ) else (
        echo.
        echo Tipp fuer den ersten Start:
        echo 1. Schliesse den Roboter einmal kurz per USB an den PC an.
        echo 2. Starte dieses Skript erneut - es aktiviert das WLAN vollautomatisch!
        echo 3. Danach Kabel abziehen.
    )
)

popd
pause
