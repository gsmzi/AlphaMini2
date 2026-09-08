# 🤖 Alpha Mini 2 – Schnellstart-Anleitung für den Roboter

Diese Anleitung erklärt Schritt für Schritt, wie der **UBTECH Alpha Mini 2** ohne chinesische Handynummer eingerichtet, auf Deutsch umgestellt und mit der deutschen Sprach- und Steuerungs-App genutzt wird.

---

## 💡 Hintergrund: Warum diese Anleitung?
Offiziell verlangt der Hersteller bei der Ersteinrichtung über die Smartphone-App eine chinesische Handynummer (+86) für eine SMS-Verifikation. In Europa kann man den Roboter auf diesem Weg nicht aktivieren.

**Die Lösung:** Im Alpha Mini arbeitet im Inneren ein vollwertiges **Android-System**. Über einen versteckten USB-Anschluss am Rücken können wir den Bildschirm des Roboters direkt auf den PC-Monitor holen, ihn mit der Maus steuern, auf Deutsch umstellen und direkt ins WLAN bringen.

---

## 📦 Was ist in diesem Ordner bereits vorbereitet?

1. **Keine Software-Installation nötig:**
   * Das Bildschirmübertragungs-Tool **`scrcpy`** und die Android-Schnittstelle **`adb`** liegen fertig eingerichtet im Ordner `tools\scrcpy\`.
2. **Die fertige Steuerungs-App liegt bereit:**
   * Die Datei **`sdkdemo-debug.apk`** liegt bereits fertig kompiliert im Hauptordner (enthält die Offline-Spracherkennung für Deutsch & Englisch, Roboter-Mimik und Tanz-/Bewegungssteuerungen).
   * Online-Download (falls nötig): https://github.com/gsmzi/AlphaMini2/releases/tag/latest

---

## 🚀 Schritt 1: Roboter anschließen & Bildschirm starten

1. **Rucksack öffnen:** Klappe am Rücken des Roboters öffnen (darunter befindet sich der USB-Anschluss).
2. **Verbinden:** Schließe den Roboter mit dem mitgelieferten USB-Kabel an den PC an.
3. **Einschalten:** Schalte den Roboter ein (Power-Taste gedrückt halten, bis er aufwacht).
4. **Bildschirm spiegeln:** Mache im Hauptordner einen Doppelklick auf:
   ```
   1_BILDSCHIRM_STARTEN.bat
   ```
5. **Ergebnis:** Es öffnet sich ein Fenster mit dem Display des Roboters. Du kannst den Roboter ab jetzt mit der Computermaus bedienen!

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

## 📱 Schritt 3: Die Sprach- & Steuerungs-App installieren

Da die Installationsdatei `sdkdemo-debug.apk` bereits im Ordner liegt, geht die Installation mit einem einzigen Klick:

1. Doppelklick auf:
   ```
   2_APP_INSTALLIEREN.bat
   ```
   *(Das Skript schiebt die App auf den Roboter und erteilt automatisch die Mikrofonberechtigung).*
2. **Alternative:** Du kannst die Datei `sdkdemo-debug.apk` auch einfach mit gedrückter Maustaste direkt in das offene `scrcpy`-Fenster ziehen (Drag & Drop).

---

## 🗣️ Schritt 4: Sprachsteuerung & Befehle

Öffne auf dem Display des Roboters die neu installierte App:
👉 Wähle **"Voice Dialogue V3 (Continuous)"** (Lila Button – die neueste Version)

### Roboter aufwecken:
- Drücke lange auf den **runden Knopf auf der Brust** des Roboters.

### Deutsche Sprachbefehle, die der Roboter versteht:
| Befehl | Was der Roboter macht |
|---|---|
| **"tanz"** oder **"tanzen"** | Roboter tanzt |
| **"winke"** oder **"wink"** | Roboter winkt mit der Hand |
| **"hände hoch"** oder **"arme hoch"** | Roboter hebt beide Arme |
| **"klatsch"** | Roboter klatscht in die Hände |
| **"verbeugen"** | Roboter verbeugt sich höflich |
| **"wie spät ist es"** | Roboter nennt die Uhrzeit |
| **"wie ist das wetter"** | Roboter liest das aktuelle Wetter vor |
| **"wie heißt du"** | Roboter stellt sich vor |
| **"erzähl einen witz"** | Roboter erzählt einen Witz |
| **"tschüss"** oder **"auf wiedersehen"** | Beendet die Unterhaltung |

---

## ❓ Häufige Fragen & Problemlösung

- **Muss der Roboter immer am PC angeschlossen bleiben?**
  Nein! Sobald Sprache, WLAN und die App V3 eingerichtet sind, läuft alles eigenständig auf dem Roboter. Der PC wird nur für die Ersteinrichtung per USB gebraucht.
- **Warum höre ich keinen Ton aus dem PC?**
  Das ist Absicht (`--no-audio`): Der Ton soll aus den Lautsprechern des Alpha Mini kommen und nicht über den PC geleitet werden, da sonst das Mikrofon gestört werden könnte.
- **`1_BILDSCHIRM_STARTEN.bat` sagt "Kein Roboter erkannt":**
  1. Prüfen, ob das USB-Kabel wirklich fest in der Buchse am Rücken sitzt.
  2. Prüfen, ob der Roboter eingeschaltet ist (Augen leuchten).
  3. Einen anderen USB-Port am PC ausprobieren.
