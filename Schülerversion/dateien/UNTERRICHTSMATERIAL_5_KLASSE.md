# 🤖 Unterrichtsmaterial: Alpha Mini programmieren mit AlphaBlock
### Blockbasierte Roboter-Programmierung für die 5. Klasse (wie Scratch)

---

## 👩‍🏫 Teil 1: Handreichung für Lehrkräfte und Betreuer (BuFDi)

### 🚀 Schnellstart in 3 Schritten:
1. **Roboter vorbereiten (optional für Live-Modus):**
   * Alpha Mini per USB-Kabel an den Lehrer-PC anschließen und einschalten.
   * *(Hinweis: Der Unterricht kann auch komplett im virtuellen Simulator auf Schüler-PCs stattfinden!)*
2. **Software starten:**
   * Mache im Hauptordner einen Doppelklick auf:
     ```
     3_SCRATCH_BLOCKLY_STARTEN.bat
     ```
   * Es öffnet sich automatisch dein Webbrowser mit der Programmierumgebung.
3. **Schüler-Geräte verbinden:**
   * Jeder Schüler-PC, Laptop oder Tablet (iPad) im selben WLAN kann einfach die im Konsolenfenster angezeigte IP-Adresse aufrufen (z.B. `http://192.168.1.50:8080`).

### 💡 Didaktischer Ablaufvorschlag (Doppelstunde 90 Minuten):
| Phase | Dauer | Inhalt |
|---|---|---|
| **Einstieg** | 10 Min. | Demonstration: Alpha Mini begrüßt die Klasse live mit einer vorgefertigten Begrüßung (Beispiel 1). Frage an die Schüler: *„Wie bringt man einem Roboter bei, genau das zu tun?“* |
| **Erkundung** | 15 Min. | Schüler öffnen AlphaBlock im Browser. Freies Ausprobieren im Simulator: Blöcke ziehen, auf die grüne Flagge klicken, Mimik und Sprache testen. |
| **Mission 1 & 2** | 30 Min. | Schüler bearbeiten selbstständig **Mission 1 (Begrüßung)** und **Mission 2 (Witze-Erzähler)**. |
| **Mission 3 & 4** | 25 Min. | Eigene Tanz-Choreografie oder Ampelspiel mit Schleifen programmieren. |
| **Präsentation** | 10 Min. | Ausgewählte Schülerprogramme werden auf den echten Alpha Mini übertragen und vor der Klasse aufgeführt! |

---

## 🎒 Teil 2: Schüler-Missionskarten (Kopiervorlage)

---

### 🌟 Mission 1: Die persönliche Begrüßung
> **Schwierigkeit:** ⭐☆☆ (Leicht)  
> **Ziel:** Bringe Alpha Mini dazu, dich und deinen Sitznachbarn mit Namen zu begrüßen!

**Deine Aufgaben:**
1. Ziehe den Block `🚩 Wenn Start angeklickt wird` auf deine Arbeitsfläche.
2. Füge den Block `🗣️ sage [...]` an und schreibe deinen Namen hinein.
3. Wähle ein passendes Gesicht mit `😊 zeige Gesicht [Glücklich]`.
4. Lass Alpha Mini winken mit `🕺 mache Aktion [Freundlich winken]`.
5. Klicke auf die grüne Flagge 🚩, um dein Programm zu testen!

**Zusatz-Knobelaufgabe:**  
Schalte die Lichter des Roboters auf deine persönliche Lieblingsfarbe um!

---

### 😂 Mission 2: Der Witze-Erzähler
> **Schwierigkeit:** ⭐⭐☆ (Mittel)  
> **Ziel:** Alpha Mini soll der Klasse einen lustigen Witz erzählen und dabei die richtige Spannung aufbauen.

**Deine Aufgaben:**
1. Lass Alpha Mini fragen: *„Wollt ihr einen Witz hören?“*
2. Benutze den Block `⏱️ warte [ 1.5 ] Sekunden`, damit das Publikum zuhören kann.
3. Erzähle die Frage des Witzes (z.B.: *„Was macht ein Roboter in der Pause?“*).
4. Baue vor der Pointe eine Pause von `2 Sekunden` ein!
5. Verrate die Pointe (z.B.: *„Er nimmt einen kurzen Byte!“*) und zeige ein lachendes Gesicht.
6. Spiele am Ende das Geräusch `🎵 spiele Geräusch [Kichern & Lachen]` ab und lass ihn klatschen!

---

### 💃 Mission 3: Die Roboter Dance-Party
> **Schwierigkeit:** ⭐⭐☆ (Mittel)  
> **Ziel:** Entwickle eine eigene Tanz-Choreografie für Alpha Mini!

**Deine Aufgaben:**
1. Starte mit einer Fanfare: `🎵 spiele Geräusch [Fanfare / Tusch]`.
2. Schalte die Lichter in den Party-Modus mit `✨ lasse Lichter [blinken] in Farbe [Lila] für [3] Sek.`.
3. Lass Alpha Mini tanzen mit `🕺 mache Aktion [Tanzen (Tai Chi Style)]`.
4. Wechsle zu einer zweiten Bewegung (z.B. `🙌 Beide Arme hoch!`).
5. Zum Schluss soll Alpha Mini sich höflich beim Publikum verbeugen (`🙇 Höflich verbeugen`).

---

### 🚦 Mission 4: Das Ampel-Spiel
> **Schwierigkeit:** ⭐⭐⭐ (Knobelaufgabe)  
> **Ziel:** Programmiere ein Reaktionsspiel für deine Mitschüler!

**Regeln:**
- **Rot:** Alpha Mini sagt *„Stopp!“*, schaltet die Lichter auf **Rot** und zeigt ein ernstes Gesicht. Alle Schüler müssen wie versteinert stillstehen!
- **Gelb:** Alpha Mini sagt *„Achtung... gleich gehts los!“* und schaltet auf **Gelb**.
- **Grün:** Alpha Mini schaltet auf **Grün**, winkt und ruft *„Jetzt dürft ihr euch bewegen!“*.

**Deine Aufgabe:**  
Baue das Programm so, dass zwischen den Ampelphasen immer 2 bis 3 Sekunden gewartet wird.

---

### 🔁 Mission 5: Roboter-Fitness mit Schleifen
> **Schwierigkeit:** 🏆 (Profi-Aufgabe)  
> **Ziel:** Nutze eine Wiederholungs-Schleife für das Morgen-Fitness-Training.

**Deine Aufgabe:**
1. Verwende den Block `🔁 wiederhole [ 3 ] mal`.
2. In der Schleife soll Alpha Mini:
   - Die Arme hochreißen (`🙌 Beide Arme hoch!`)
   - 1 Sekunde warten
   - In die Kniebeuge gehen (`🧘 In die Hocke gehen`)
   - 1 Sekunde warten
3. Nach der Schleife soll Alpha Mini sagen: *„Training beendet! Wir sind fit!“* und sich Applaus spendieren (`👏 Applaus`).

---

### 🐱 Mission 6: Das Roboter-Haustier & Streichel-Sensor
> **Schwierigkeit:** ⭐⭐☆ (Mittel)  
> **Ziel:** Mache Alpha Mini zu einer Roboter-Katze, die auf Berührung am Kopf reagiert!

**Deine Aufgaben:**
1. Lass Alpha Mini sich am Start schlafen legen:
   - Gesicht: `😴 Müde / Einschlafen`
   - Licht: `✨ lasse Lichter [sanft atmen (Pulsieren)] in Farbe [Cyan Blau]`
   - Aktion: `🛏️ Hinlegen (Schlafmodus)`
2. Verwende nun den Kopf-Sensor-Block:
   - `💆 Wenn Kopf [berührt / gestreichelt wird]`
3. Wenn jemand den Kopf berührt oder im Simulator auf **„💆 Kopf berühren“** klickt:
   - Augen mit `😍 Herzchen / Verliebt` leuchten lassen.
   - Das Geräusch `🎵 spiele Geräusch [Kichern & Lachen]` abspielen.
   - Alpha Mini soll sagen: *„Mmmh, das kitzelt! Ich bin wach!“*
   - Er steht wieder auf mit `🧍 Wieder aufstehen`!

---

### 🦾 Mission 7: Der Schutzengel (Umfall-Erkennung & Aufstehen)
> **Schwierigkeit:** ⭐⭐⭐ (Knobelaufgabe)  
> **Ziel:** Wenn der Roboter umgestoßen wird, soll er automatisch warnen und wieder aufstehen!

**Deine Aufgaben:**
1. Nimm den Ereignis-Block `💥 Wenn Roboter umgefallen ist`.
2. Schalte die Lichter sofort auf **Rot** (`🚨 Blinken` oder `💡 Rot`).
3. Zeige ein trauriges Gesicht `😢 Traurig`.
4. Lass ihn rufen: *„Hoppla, ich liege auf dem Boden! Ich rappele mich wieder auf!“*
5. Füge den Block `🧍 Wieder aufstehen` ein.
6. Wenn er steht, lächle wieder (`😊 Glücklich`) und sage: *„Alles gut, mir geht es super!“*
7. Teste es im Simulator mit dem Button **„💥 Umwerfen“**!

---

## 🧩 Teil 3: Block-Übersicht für Schüler

| Block | Kategorie | Was er tut |
|---|---|---|
| `🚩 Wenn Start angeklickt wird` | 🚩 Ereignisse | Startet dein Programm, sobald du oben auf die Flagge klickst. |
| `💆 Wenn Kopf [berührt]` | 🚩 Ereignisse | Reagiert sofort, wenn jemand den Sensor oben auf Alpha Minis Kopf berührt. |
| `🚶 Wenn sich jemand nähert` | 🚩 Ereignisse | Reagiert über den Infrarot-Sensor, wenn eine Person vor den Roboter tritt. |
| `💥 Wenn Roboter umgefallen ist` | 🚩 Ereignisse | Lagesensor: Schlägt Alarm, wenn der Roboter umgeworfen wird oder flach liegt. |
| `🗣️ sage [ Text ]` | 🗣️ Sprache | Alpha Mini spricht deinen Text mit deutscher Sprachausgabe. |
| `🗣️ sage [ Text ] mit Gefühl [ ... ]` | 🗣️ Sprache | Spricht den Text fröhlich, aufgeregt, traurig oder wie ein Roboter. |
| `🎵 spiele Geräusch [ ... ]` | 🗣️ Sprache | Spielt lustige Sounds wie Lachen, Applaus, Tröte, Jubel oder Piepsen ab. |
| `🕺 mache Aktion [ ... ]` | 🚶 Bewegung | Führt Aktionen aus (Aufstehen, Hinlegen, Hocke, Tanzen, Winken, Liegestütze). |
| `🦾 bewege Motor [Kopf/Arm] auf [Winkel]` | 🚶 Bewegung | Bewegt ein einzelnes Gelenk gradgenau (z. B. Kopf nicken, Arm heben). |
| `🍃 entspanne alle Motoren` | 🚶 Bewegung | Schaltet die Motoren weich, damit man die Gelenke vorsichtig per Hand bewegen kann. |
| `😊 zeige Gesicht [ ... ]` | 😊 Aussehen | Schaltet die LCD-Augen um (Lächeln, Herzchen, Wütend, Müde, Zwinkern). |
| `💡 schalte Lichter auf [ Farbe ]` | 💡 Lichter | Bringt die Ohren und die Brust von Alpha Mini in deiner Wunschfarbe zum Leuchten. |
| `✨ lasse Lichter [blinken/atmen/kreisen]` | 💡 Lichter | Tolle Licht-Effekte: Pulsieren wie ein Atem oder bunter Regenbogen-Kreis. |
| `🔋 Akku-Ladestand in %` | 🔍 Sensoren | Liest den echten Akkustand des Roboters ab (0 bis 100%). |
| `🔌 lädt der Akku gerade?` | 🔍 Sensoren | Gibt wahr zurück, wenn das USB-Ladekabel eingesteckt ist. |
| `🔍 liegt Roboter auf dem [Rücken/Bauch]?` | 🔍 Sensoren | Fragt ab, ob der Roboter aufrecht steht, auf dem Rücken oder Bauch liegt. |
| `⏱️ warte [ ... ] Sekunden` | ⏱️ Steuerung | Macht eine Pause, bevor der nächste Block an der Reihe ist. |
| `🔁 wiederhole [ ... ] mal` | ⏱️ Steuerung | Wiederholt alle darin liegenden Blöcke automatisch. |

