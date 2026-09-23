# ==============================================================================
# Alpha Mini 2 - Automatische Synchronisation nach Google Drive (Bufdi-Paket)
# Ziel: Bereitstellung aller noetigen Skripte, Apps und Anleitungen ohne Entwickler-Dateien
# ==============================================================================
param(
    [string]$TargetDir = "G:\Meine Ablage\Schule\Robotik-AG\AlphaMini\Software"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   ALPHA MINI 2 -> GOOGLE DRIVE SYNCHRONISATION" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Quelle: $ScriptDir"
Write-Host "Ziel:   $TargetDir"
Write-Host ""

$driveRoot = Split-Path -Qualifier $TargetDir
if (-not (Test-Path -Path $driveRoot)) {
    Write-Host "[FEHLER] Laufwerk $driveRoot nicht erreichbar!" -ForegroundColor Red
    Write-Host "Bitte sicherstellen, dass Google Drive fuer Desktop laeuft." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path -Path $TargetDir)) {
    Write-Host "[INFO] Zielordner existiert noch nicht. Wird neu erstellt..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
}

$excludeDirs = @(".git", ".github", "SDK", "__pycache__", ".idea", ".vscode", "temp", "tmp")
$excludeFiles = @(".gitignore", ".gitattributes", "desktop.ini", "sync_to_google_drive.ps1", "SYNC_ZU_GOOGLE_DRIVE.bat", "GEMINI.md", "*.pyc")

Write-Host "Synchronisiere Dateien via robocopy..." -ForegroundColor Green

& robocopy $ScriptDir $TargetDir /MIR /FFT /R:2 /W:2 /NFL /NDL /XD $excludeDirs /XF $excludeFiles
$exitCode = $LASTEXITCODE

# Robocopy Exit-Codes: 0 = keine Aenderung, 1-7 = Dateien kopiert/erfolgreich, >=8 = Fehler
if ($exitCode -ge 8) {
    Write-Host "[FEHLER] Robocopy fehlgeschlagen mit Fehlercode $exitCode!" -ForegroundColor Red
    exit $exitCode
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " [OK] Synchronisation erfolgreich abgeschlossen!" -ForegroundColor Green
Write-Host "      Die Bufdis haben jetzt den neuesten Stand." -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
exit 0
