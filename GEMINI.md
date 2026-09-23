# Alpha Mini 2 - Projekt-Richtlinien

## Automatische Google-Drive-Synchronisation (Bufdi-Paket)
- Wann immer relevante Änderungen an den nutzer- und schülerrelevanten Dateien vorgenommen werden (z. B. `.bat`-Skripte, `ANLEITUNG_FUER_DEN_BUFDI.md`, `UNTERRICHTSMATERIAL_5_KLASSE.md`, `alphablock/`, `alphablock_server.py`, `.apk`):
  - Führe am Ende der Änderungen automatisch das Skript `.\sync_to_google_drive.ps1` aus.
  - Dadurch wird der Ordner `G:\Meine Ablage\Schule\Robotik-AG\AlphaMini\Software` für die Bufdis und Lehrkräfte ohne manuellen Aufwand auf dem neuesten Stand gehalten.
  - Das Skript nutzt `robocopy /MIR` und schließt `.git`, `.github`, `SDK` und temporäre Dateien automatisch aus.
