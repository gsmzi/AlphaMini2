# 🤖 Alpha Mini 2 – Schnellstart-Anleitung für den Roboter

Diese Anleitung erklärt Schritt für Schritt, wie der **UBTECH Alpha Mini 2** ohne chinesische Handynummer eingerichtet, auf Deutsch umgestellt und mit der deutschen Sprach- und Steuerungs-App genutzt wird.

---

## 💡 Hintergrund: Warum diese Anleitung?
Offiziell verlangt der Hersteller bei der Ersteinrichtung über die Smartphone-App eine chinesische Handynummer (+86) für eine SMS-Verifikation. In Europa kann man den Roboter auf diesem Weg nicht aktivieren.

**Die Lösung:** Im Alpha Mini arbeitet im Inneren ein vollwertiges **Android-System**. Über einen versteckten USB-Anschluss am Rücken können wir den Bildschirm des Roboters direkt auf den PC-Monitor holen, ihn mit der Maus steuern, auf Deutsch umstellen und direkt ins WLAN bringen.

---

## 📦 Vorbereitung (Was wird benötigt?)

1. **Dieser gesamte Ordner (`Alphamini-2`)** (kann einfach auf einem USB-Stick oder Netzlaufwerk mitgenommen werden).
2. **Das USB-Kabel** (liegt dem Roboter bei).
3. **Ein Windows-PC** (Windows 10 oder 11).
4. **Wichtig:** Alle nötigen Hilfsprogramme (`scrcpy` und `adb`) sind bereits im Ordner `tools\scrcpy\` fertig eingerichtet. **Es muss keine Software auf dem PC vorinstalliert werden!**

---

## 🚀 Schritt 1: Roboter anschließen & Bildschirm starten

1. Klappe am Rücken des Roboters öffnen (unter dem Rucksack befindet sich der USB-Anschluss).
2. Verbinde den Roboter mit dem USB-Kabel mit dem Windows-PC.
3. Schalte den Roboter ein (Taste gedrückt halten, bis er aufwacht).
4. Mache im Hauptordner einen Doppelklick auf:
   ```
   1_BILDSCHIRM_STARTEN.bat
   ```
5. **Ergebnis:** Es öffnet sich ein Fenster, das den Bildschirm des Roboters 1:1 anzeigt!
   *(Tipp: Du kannst den Roboter in diesem Fenster jetzt direkt mit deiner Computermaus bedienen.)*

> **Hinweis:** Falls auf dem Roboter-Bildschirm eine Meldung wie *"USB-Debugging zulassen?"* erscheint, setze einen Haken bei *"Von diesem Computer immer zulassen"* und klicke auf **Zulassen / OK**.

---

## ⚙️ Schritt 2: Sprache auf Deutsch umstellen & ins WLAN bringen

*(Siehe auch die bebilderte Original-PDF `alpha_mini_setup_guide.pdf`)*

### A. Sprache ändern:
1. Klicke im gespiegelten Roboter-Fenster ganz unten links auf den **kleinen Pfeil**, um die App-Übersicht zu öffnen.
2. Klicke auf das **Zahnrad-Symbol** (Einstellungen / Settings / 設置).
3. Scrolle zu **Sprache & Eingabe** (Language / 語言).
4. Klicke auf **Sprachen**, wähle **Deutsch** aus und ziehe Deutsch an die **oberste Stelle (Position 1)**.
   *Die gesamte Benutzeroberfläche schaltet sofort auf Deutsch um.*

### B. WLAN verbinden:
1. Gehe in den Einstellungen auf **WLAN** (Wi-Fi).
2. Aktiviere WLAN und wähle dein lokales Netzwerk aus.
3. Gib das WLAN-Passwort über deine PC-Tastatur ein und verbinde den Roboter.
4. **Fertig!** Der Roboter ist nun online und entsperrt.

---

## 📱 Schritt 3: Die Terra-Robotics Sprach- & Steuerungs-App

Terra Robotics hat eine eigene Steuerungs-App für den Roboter entwickelt.

### Prüfen, ob die App schon installiert ist:
Schau in der App-Übersicht des Roboters (Pfeil unten links) nach, ob dort bereits eine App namens **`Voice Dialogue`** oder **`SDK Demo`** zu finden ist.
- Wenn **JA**: Klicke sie einfach an!
- Wenn **NEIN**: Benötigst du die fertige Installationsdatei (`sdkdemo-debug.apk`).

### So installierst du die App:
1. Fordere beim Verkäufer (*Terra Robotics*) kurz die fertige Datei an:
   > *"Bitte senden Sie uns die fertig kompilierte `sdkdemo-debug.apk` für den Alpha Mini 2."*
2. Sobald du die Datei hast, gibt es zwei kinderleichte Wege:
   - **Weg A (Am einfachsten):** Ziehe die `.apk`-Datei einfach mit gedrückter Maustaste direkt in das geöffnete `scrcpy`-Fenster (Drag & Drop). Sie installiert sich in 3 Sekunden automatisch!
   - **Weg B:** Lege die `.apk`-Datei in diesen Hauptordner und starte mit einem Doppelklick:
     ```
     2_APP_INSTALLIEREN.bat
     ```
     *(Das Skript installiert die App und schaltet automatisch die Mikrofonberechtigung frei).*

---

## 🗣️ Schritt 4: Sprachsteuerung & Befehle

In der App auf dem Roboter gibt es mehrere Dialog-Modi. Wähle:
👉 **"Voice Dialogue V3 (Continuous)"** (Lila Button – die neueste Version)

### Roboter aufwecken:
- Entweder durch langes Drücken auf den **runden Knopf auf der Brust** des Roboters.
- Oder über das Wake-Word.

### Deutsche Sprachbefehle, die der Roboter versteht:
| Befehl | Was der Roboter macht |
|---|---|
| **"tanz"** oder **"tanzen"** | Roboter führt einen Tanz vor |
| **"winke"** oder **"wink"** | Roboter winkt mit der Hand |
| **"hände hoch"** oder **"arme hoch"** | Roboter hebt beide Arme |
| **"klatsch"** | Roboter klatscht in die Hände |
| **"verbeugen"** | Roboter verbeugt sich höflich |
| **"wie spät ist es"** | Roboter nennt die aktuelle Uhrzeit |
| **"wie ist das wetter"** | Roboter liest die Wettervorhersage vor |
| **"wie heißt du"** | Roboter stellt sich vor |
| **"erzähl einen witz"** | Roboter erzählt einen Witz |
| **"tschüss"** oder **"auf wiedersehen"** | Beendet das Gespräch |

---

## ❓ Häufige Fragen & Problemlösung

- **Muss der Roboter immer am PC angeschlossen bleiben?**
  Nein! Sobald die Sprache auf Deutsch gestellt, das WLAN verbunden und die App V3 installiert ist, läuft die Sprach-App komplett eigenständig auf dem Roboter. Der PC wird nur für die Ersteinrichtung per USB gebraucht.
- **Warum höre ich keinen Ton aus dem PC?**
  Das ist Absicht (`--no-audio`): Der Ton soll aus den internen Lautsprechern des Alpha Mini kommen und nicht über den PC umgeleitet werden, da sonst das Mikrofon gestört werden könnte.
- **`1_BILDSCHIRM_STARTEN.bat` sagt "Kein Roboter erkannt":**
  1. Prüfen, ob das USB-Kabel wirklich fest in der Buchse am Rücken sitzt.
  2. Prüfen, ob der Roboter eingeschaltet ist (Augen leuchten).
  3. Einen anderen USB-Port am PC ausprobieren.
