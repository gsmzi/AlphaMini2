#!/usr/bin/env python3
"""
=============================================================================
AlphaBlock Server - Scratch-Programmierumgebung für Alpha Mini
=============================================================================
Ein schlanker, robuster Python-Server (100% Standardbibliothek, keine Installation
von externen Bibliotheken wie Flask nötig).

Funktionen:
1. Serviert die webbasierte Scratch-Oberfläche (Blockly) für Browser im Schulnetzwerk.
2. Prüft automatisch, ob ein Alpha Mini über USB (ADB) oder WLAN verbunden ist.
3. Überträgt Befehle (Sprache, Mimik, Aktionen, Lichter) direkt an den Roboter.
"""

import http.server
import json
import os
import subprocess
import sys
import threading
import time
import urllib.parse
from pathlib import Path

# Windows Konsolen-Encoding auf UTF-8 absichern
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Basis-Pfade ermitteln
SCRIPT_DIR = Path(__file__).resolve().parent
WEB_DIR = SCRIPT_DIR / "alphablock"
ADB_CANDIDATES = [
    SCRIPT_DIR / "tools" / "scrcpy" / "adb.exe",
    SCRIPT_DIR / ".." / "tools" / "scrcpy" / "adb.exe",
    Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
]

def find_adb():
    """Findet den Pfad zu adb.exe"""
    for candidate in ADB_CANDIDATES:
        if candidate.exists():
            return str(candidate)
    return "adb"

ADB_PATH = find_adb()
LOG_FILE = SCRIPT_DIR / "alphablock_server.log"

def server_log(msg):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

WIN32_NO_WINDOW = 0x08000000 if sys.platform == "win32" else 0

class RobotBridge:
    """Kommunikations-Brücke zum Alpha Mini Roboter über ADB"""

    _cached_devices = []
    _last_device_check = 0.0

    @staticmethod
    def get_connected_devices():
        """Gibt eine Liste aller per ADB erkannten Geräte zurück (mit 3s Cache)"""
        now = time.time()
        if now - RobotBridge._last_device_check < 3.0:
            return RobotBridge._cached_devices
        try:
            result = subprocess.run(
                [ADB_PATH, "devices"],
                capture_output=True,
                text=True,
                creationflags=WIN32_NO_WINDOW,
                timeout=4
            )
            devices = []
            for line in result.stdout.strip().splitlines():
                parts = line.split()
                if len(parts) >= 2 and parts[1] == "device":
                    devices.append(parts[0])
            RobotBridge._cached_devices = devices
            RobotBridge._last_device_check = now
            return devices
        except Exception:
            return RobotBridge._cached_devices

    @staticmethod
    def is_connected():
        devices = RobotBridge.get_connected_devices()
        return len(devices) > 0, devices[0] if devices else None

    @staticmethod
    def run_adb_shell(command_args):
        """Führt einen ADB shell Befehl auf dem ersten verbundenen Gerät aus"""
        try:
            cmd = [ADB_PATH, "shell"] + command_args
            res = subprocess.run(cmd, capture_output=True, text=True, creationflags=WIN32_NO_WINDOW, timeout=6)
            return res.returncode == 0, res.stdout
        except Exception as e:
            server_log(f"[ADB Fehler] {e}")
            return False, str(e)

    @staticmethod
    def speak(text):
        """Lässt den Roboter über die installierte App sprechen/Mimik zeigen"""
        server_log(f"[Roboter] 🗣️ Spreche: '{text}'")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.SPEAK_TEST",
            "-p", "com.ubtrobot.mini.sdkdemo",
            "--es", "text", text
        ]
        RobotBridge.run_adb_shell(cmd)
        return True

    @staticmethod
    def walk(direction="forward", steps=2):
        """Lässt den Roboter vorwärts oder rückwärts laufen"""
        server_log(f"[Roboter] 🚶 Laufe {steps} Schritte {direction}")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.WALK",
            "-p", "com.ubtrobot.mini.sdkdemo",
            "--es", "direction", direction,
            "--ei", "steps", str(steps)
        ]
        RobotBridge.run_adb_shell(cmd)
        return True

    @staticmethod
    def turn(direction="left", steps=2):
        """Dreht den Roboter um eine bestimmte Anzahl Schritte nach links/rechts"""
        server_log(f"[Roboter] 🔄 Drehe {steps} Schritte {direction}")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.TURN",
            "-p", "com.ubtrobot.mini.sdkdemo",
            "--es", "direction", direction,
            "--ei", "steps", str(steps)
        ]
        RobotBridge.run_adb_shell(cmd)
        return True

    @staticmethod
    def play_action(action_id):
        """Führt eine Bewegung aus (z.B. 010=Winken, 014=Tanzen, pressup=Liegestütze)"""
        server_log(f"[Roboter] 🕺 Aktion: {action_id}")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.ACTION",
            "-p", "com.ubtrobot.mini.sdkdemo",
            "--es", "action", action_id
        ]
        RobotBridge.run_adb_shell(cmd)
        return True

    @staticmethod
    def stop_action():
        """Stoppt laufende Bewegungen"""
        server_log("[Roboter] 🛑 Stoppe Bewegung")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.ACTION_STOP",
            "-p", "com.ubtrobot.mini.sdkdemo"
        ]
        RobotBridge.run_adb_shell(cmd)
        return True

    @staticmethod
    def set_expression(expression_id):
        """Setzt die LCD-Augenmimik (z.B. emo_007=Lächeln)"""
        server_log(f"[Roboter] 😊 Mimik: {expression_id}")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.EXPRESSION",
            "-p", "com.ubtrobot.mini.sdkdemo",
            "--es", "expression", expression_id
        ]
        RobotBridge.run_adb_shell(cmd)
        return True

    @staticmethod
    def set_light(color):
        """Setzt LED-Farben"""
        server_log(f"[Roboter] 💡 Lichter: {color}")
        cmd = [
            "am", "broadcast",
            "-a", "com.ubtrobot.mini.sdkdemo.LIGHT",
            "-p", "com.ubtrobot.mini.sdkdemo",
            "--es", "color", color
        ]
        RobotBridge.run_adb_shell(cmd)
        return True


class AlphaBlockHandler(http.server.SimpleHTTPRequestHandler):
    """HTTP-Handler für statische Webdateien und REST API"""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def end_headers(self):
        # CORS Header für browserübergreifenden Zugriff im Schulnetzwerk
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)

        # API: Favicon abfangen (verhindert 404 und Fehlermeldungen)
        if parsed.path in ("/favicon.ico", "favicon.ico"):
            self.send_response(204)
            self.end_headers()
            return

        # API: Roboter-Status abfragen
        if parsed.path == "/api/status":
            connected, device_id = RobotBridge.is_connected()
            response_data = {
                "connected": connected,
                "device_id": device_id or "Kein Roboter erkannt",
                "adb_path": ADB_PATH,
                "server_time": time.time()
            }
            self.send_json(response_data)
            return

        # API: Health-Check
        if parsed.path == "/health":
            self.send_json({"status": "ok", "app": "AlphaBlock"})
            return

        # Statische Dateien ausliefern
        return super().do_GET()

    def do_POST(self):
        try:
            self._handle_post()
        except Exception as e:
            import traceback
            server_log(f"[POST Fehler] {e}\n{traceback.format_exc()}")
            try:
                self.send_error(500, f"Interner Serverfehler: {e}")
            except Exception:
                pass

    def _handle_post(self):
        parsed = urllib.parse.urlparse(self.path)

        # JSON Body lesen
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else b""
        data = {}
        if body:
            try:
                data = json.loads(body.decode("utf-8"))
            except Exception:
                pass

        # API: Sprechen
        if parsed.path == "/api/robot/say":
            text = data.get("text", "")
            mood = data.get("mood", "normal")
            if text:
                RobotBridge.speak(text)
            self.send_json({"success": True})
            return

        # API: Aktion ausführen
        if parsed.path == "/api/robot/action":
            action = data.get("action", "010")
            RobotBridge.play_action(action)
            self.send_json({"success": True})
            return

        # API: Laufen
        if parsed.path == "/api/robot/walk":
            direction = data.get("direction", "forward")
            steps = int(data.get("steps", 2))
            RobotBridge.walk(direction, steps)
            self.send_json({"success": True})
            return

        # API: Drehen
        if parsed.path == "/api/robot/turn":
            direction = data.get("direction", "left")
            steps = int(data.get("steps", 2))
            RobotBridge.turn(direction, steps)
            self.send_json({"success": True})
            return

        # API: Mimik ändern
        if parsed.path == "/api/robot/express":
            expr = data.get("expression", "emo_007")
            RobotBridge.set_expression(expr)
            self.send_json({"success": True})
            return

        # API: Lichter setzen
        if parsed.path == "/api/robot/light":
            color = data.get("color", "green")
            RobotBridge.set_light(color)
            self.send_json({"success": True})
            return

        # API: Stopp
        if parsed.path == "/api/robot/stop":
            RobotBridge.stop_action()
            self.send_json({"success": True})
            return

        # API: Live-Verbindungstest (Winken)
        if parsed.path == "/api/robot/test":
            server_log("[API] 🧪 Live-Test Winken ausgelöst")
            RobotBridge.play_action("010")
            self.send_json({"success": True, "message": "Testaktion Winken gestartet"})
            return

        # API: Browser Client-Logs
        if parsed.path == "/api/log":
            msg = data.get("message", "")
            server_log(f"[Browser-Log] {msg}")
            self.send_json({"success": True})
            return

        # Fallback 404
        self.send_error(404, "Endpunkt nicht gefunden")

    def send_json(self, data, status=200):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Ruhigere Konsolenausgabe: Status-Polling nicht jedes Mal ausgeben
        try:
            msg = format % args
            if "/api/status" in msg and "200" in msg:
                return
            sys.stdout.write(f"{self.address_string()} - - [{self.log_date_time_string()}] {msg}\n")
            sys.stdout.flush()
        except Exception:
            pass


def start_server(port=8080):
    """Startet den AlphaBlock Server"""
    server_address = ("0.0.0.0", port)
    try:
        httpd = http.server.ThreadingHTTPServer(server_address, AlphaBlockHandler)
    except OSError:
        # Fallback falls Port 8080 belegt ist
        port = 8088
        server_address = ("0.0.0.0", port)
        httpd = http.server.ThreadingHTTPServer(server_address, AlphaBlockHandler)

    print("=" * 65)
    print(" 🤖 AlphaBlock - Programmierumgebung für Alpha Mini")
    print("=" * 65)
    print(f" [OK] Server läuft erfolgreich auf Port {port}!")
    print(f" 👉 Lokal öffnen:     http://127.0.0.1:{port}")
    
    # Lokale IP für das Schulnetzwerk anzeigen
    import socket
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        print(f" 🌐 Im Schul-WLAN:    http://{local_ip}:{port}")
    except Exception:
        pass

    connected, device = RobotBridge.is_connected()
    if connected:
        print(f" [OK] Alpha Mini Roboter erkannt: {device}")
    else:
        print(" [!] Kein Roboter per USB erkannt (Simulator-Modus aktiv).")
        print("     Schließe den Roboter per USB an, um ihn live zu steuern.")
    print("=" * 65)
    print(" Drücke Strg + C im Konsolenfenster, um den Server zu beenden.\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[Server beendet]")
        httpd.server_close()


if __name__ == "__main__":
    port = 8080
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            pass
    start_server(port)
