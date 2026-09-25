/**
 * AlphaBlock - Custom Blockly Blocks für den Alpha Mini
 * Speziell für die 5. Klasse formuliert (kindgerechtes Deutsch, bunte Farben, klare Icons)
 */

// Farb-Definitionen angelehnt an Scratch 3.0
const ALPHA_COLORS = {
    SPEECH: '#CF63CF',     // Sprache & Töne (Pink/Lila)
    MOTION: '#4C97FF',     // Bewegung & Aktionen (Blau)
    LOOKS: '#00C3E6',      // Mimik & LCD-Augen (Türkis)
    LIGHTS: '#FFB703',     // LEDs & Lichter (Goldgelb)
    CONTROL: '#FF851B',    // Steuerung & Pausen (Orange)
    EVENTS: '#FFD166',     // Ereignisse & Start (Gelb)
    SENSORS: '#2EC4B6'     // Sensoren (Mintgrün)
};

// ==========================================
// 🚩 EREIGNISSE (EVENTS)
// ==========================================

Blockly.Blocks['alpha_when_start'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🚩 Wenn Start angeklickt wird');
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.EVENTS);
        this.setTooltip('Startet das Programm, wenn du auf die grüne Flagge klickst.');
        this.setHelpUrl('');
    }
};

Blockly.Blocks['alpha_when_chest_button'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🔘 Wenn Brustknopf gedrückt wird');
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.EVENTS);
        this.setTooltip('Startet, wenn jemand auf den runden Knopf an Alpha Minis Brust drückt.');
    }
};

Blockly.Blocks['alpha_when_head_touched'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('💆 Wenn Kopf')
            .appendField(new Blockly.FieldDropdown([
                ['berührt / gestreichelt wird', 'touch'],
                ['doppelt angetippt wird', 'double_tap'],
                ['lang berührt wird', 'long_press']
            ]), 'GESTURE');
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.EVENTS);
        this.setTooltip('Startet, wenn jemand den Sensor oben auf dem Kopf berührt oder streichelt.');
    }
};

Blockly.Blocks['alpha_when_person_detected'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🚶 Wenn sich jemand nähert (Bewegungsmelder)');
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.EVENTS);
        this.setTooltip('Startet über den Infrarot-Sensor (PIR), wenn eine Person in die Nähe des Roboters kommt.');
    }
};

Blockly.Blocks['alpha_when_fallen'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('💥 Wenn Roboter umgefallen ist');
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.EVENTS);
        this.setTooltip('Reagiert, wenn der Lagesensor erkennt, dass der Roboter umgestoßen wurde oder liegt.');
    }
};

Blockly.Blocks['alpha_when_heard'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('👂 Wenn Roboter hört')
            .appendField(new Blockly.FieldTextInput('Hallo'), 'WORD');
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.EVENTS);
        this.setTooltip('Reagiert, wenn das angegebene Wort gesprochen wird.');
    }
};

// ==========================================
// 🗣️ SPRACHE & TÖNE (SPEECH & SOUNDS)
// ==========================================

Blockly.Blocks['alpha_say'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🗣️ sage')
            .appendField(new Blockly.FieldTextInput('Hallo, ich bin Alpha Mini!'), 'TEXT');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.SPEECH);
        this.setTooltip('Lässt den Roboter einen Satz auf Deutsch laut aussprechen.');
    }
};

Blockly.Blocks['alpha_say_mood'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🗣️ sage')
            .appendField(new Blockly.FieldTextInput('Das macht so viel Spaß!'), 'TEXT')
            .appendField('mit Gefühl')
            .appendField(new Blockly.FieldDropdown([
                ['😄 Fröhlich', 'happy'],
                ['🤩 Aufgeregt', 'excited'],
                ['🤫 Geheimnisvoll', 'whisper'],
                ['😢 Traurig', 'sad'],
                ['🤖 Roboter', 'robot']
            ]), 'MOOD');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.SPEECH);
        this.setTooltip('Spricht den Text mit einer bestimmten Emotion und Tonhöhe.');
    }
};

Blockly.Blocks['alpha_sound'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🎵 spiele Geräusch')
            .appendField(new Blockly.FieldDropdown([
                ['😂 Kichern & Lachen', 'laugh'],
                ['👏 Applaus / Klatschen', 'applause'],
                ['🎉 Jubel / Cheering', 'cheer'],
                ['🤖 Roboter-Piepsen', 'beep'],
                ['🎺 Fanfare / Tusch', 'fanfare'],
                ['📢 Tröte / Hupe', 'horn'],
                ['💨 Pups / Furz (Lacher)', 'fart'],
                ['🔔 Klingel', 'bell']
            ]), 'SOUND');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.SPEECH);
        this.setTooltip('Spielt einen lustigen Soundeffekt ab.');
    }
};

// ==========================================
// 🕺 BEWEGUNG & AKTIONEN (MOTION)
// ==========================================

Blockly.Blocks['alpha_walk'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🚶 laufe')
            .appendField(new Blockly.FieldDropdown([
                ['⬆️ vorwärts', 'forward'],
                ['⬇️ rückwärts', 'backward']
            ]), 'DIRECTION')
            .appendField(new Blockly.FieldNumber(2, 1, 10, 1), 'STEPS')
            .appendField('Schritte');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Lässt den Roboter eine bestimmte Anzahl an Schritten vorwärts oder rückwärts laufen.');
    }
};

Blockly.Blocks['alpha_turn'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🔄 drehe')
            .appendField(new Blockly.FieldDropdown([
                ['⬅️ nach links', 'left'],
                ['➡️ nach rechts', 'right']
            ]), 'DIRECTION')
            .appendField('um')
            .appendField(new Blockly.FieldNumber(2, 1, 8, 1), 'STEPS')
            .appendField('Schritte');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Dreht den Roboter um eine Anzahl an Schritten nach links oder rechts.');
    }
};

Blockly.Blocks['alpha_action'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🕺 mache Aktion')
            .appendField(new Blockly.FieldDropdown([
                ['👋 Freundlich winken', '010'],
                ['💃 Tanzen (Tai Chi)', '014'],
                ['💪 Liegestütze machen', 'pressup'],
                ['🙌 Beide Arme hoch!', '017'],
                ['👏 In die Hände klatschen', '018'],
                ['🙇 Höflich verbeugen', '016'],
                ['🤔 Nachdenklich am Kinn kratzen', '021'],
                ['🧘 In die Hocke gehen', '031'],
                ['🛌 Hinlegen (Bauchlage / Schlafen)', 'lie_down'],
                ['🧍 Aus dem Liegen aufstehen', 'standup'],
                ['🆙 Aufrecht hinstellen (Grundstellung)', 'reset_stand'],
                ['👍 Zustimmend nicken', '011']
            ]), 'ACTION');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Führt eine coole Roboter-Bewegung aus.');
    }
};

Blockly.Blocks['alpha_motor_move'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🦾 bewege Gelenk')
            .appendField(new Blockly.FieldDropdown([
                ['Rechter Arm (Schulter)', '1'],
                ['Rechter Arm (Heben)', '2'],
                ['Linker Arm (Schulter)', '3'],
                ['Linker Arm (Heben)', '4'],
                ['Kopf (Heben/Senken)', '11'],
                ['Kopf (Drehen)', '13'],
                ['Taille (Drehen)', '14']
            ]), 'MOTOR')
            .appendField('auf')
            .appendField(new Blockly.FieldNumber(90, 0, 240), 'ANGLE')
            .appendField('Grad in')
            .appendField(new Blockly.FieldNumber(1, 0.2, 5), 'DURATION')
            .appendField('Sek.');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Dreht einen einzelnen Servomotor des Roboters auf einen exakten Winkel (0 bis 240 Grad).');
    }
};

Blockly.Blocks['alpha_motor_relax'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🪶 schalte Gelenke')
            .appendField(new Blockly.FieldDropdown([
                ['weich (per Hand bewegen / Teach-In)', 'relax'],
                ['fest (Roboter hält Position)', 'lock']
            ]), 'MODE');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Entspannt die Motoren, damit du die Arme per Hand biegen kannst, oder spannt sie wieder fest an.');
    }
};

Blockly.Blocks['alpha_stop_action'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🛑 stoppe alle Bewegungen');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Hält laufende Roboter-Bewegungen sofort an.');
    }
};

Blockly.Blocks['alpha_head_turn'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('👀 drehe Kopf')
            .appendField(new Blockly.FieldDropdown([
                ['⬅️ nach links', 'left'],
                ['➡️ nach rechts', 'right'],
                ['⬆️ nach oben', 'up'],
                ['⏺️ geradeaus (Mitte)', 'center']
            ]), 'DIRECTION');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.MOTION);
        this.setTooltip('Dreht den Kopf des Roboters in eine Richtung.');
    }
};

// ==========================================
// 😊 MIMIK & AUGEN (LOOKS / EXPRESSIONS)
// ==========================================

Blockly.Blocks['alpha_expression'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('😊 zeige Gesicht')
            .appendField(new Blockly.FieldDropdown([
                ['😊 Glücklich (Lächeln)', 'emo_007'],
                ['🤩 Super begeistert', 'emo_008'],
                ['😉 Zwinkern', 'wink'],
                ['😲 Überrascht / Staunen', 'codemao8'],
                ['😢 Traurig', 'emo_014'],
                ['😍 Herzchen-Augen', 'emo_006'],
                ['🤔 Nachdenklich', 'emo_010'],
                ['😠 Schmollend / Wütend', 'emo_002'],
                ['😴 Schläfrig / Müde', 'emo_004'],
                ['🙂 Normal (Bereit)', 'normal_1']
            ]), 'EXPRESSION');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.LOOKS);
        this.setTooltip('Zeigt einen emotionalen Gesichtsausdruck auf den beiden LCD-Augendisplays.');
    }
};

// ==========================================
// 💡 LICHTER & LEDS (LIGHTS)
// ==========================================

Blockly.Blocks['alpha_set_light'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('💡 schalte Lichter auf')
            .appendField(new Blockly.FieldDropdown([
                ['🟢 Grün', 'green'],
                ['🔵 Blau', 'blue'],
                ['🔴 Rot', 'red'],
                ['🟡 Gelb', 'yellow'],
                ['🟣 Lila / Magenta', 'purple'],
                ['⚪ Weiß', 'white'],
                ['⚫ Aus (Dunkel)', 'off']
            ]), 'COLOR');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.LIGHTS);
        this.setTooltip('Setzt die Farbe der Brust- und Ohren-LEDs.');
    }
};

Blockly.Blocks['alpha_light_effect'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('✨ lasse Lichter')
            .appendField(new Blockly.FieldDropdown([
                ['🌊 sanft pulsieren (Atmen)', 'breath'],
                ['🔄 im Kreis laufen (Lauflicht)', 'cycle'],
                ['⚡ blinken', 'blink'],
                ['👄 Mund-Licht an', 'mouth_on'],
                ['👄 Mund-Licht aus', 'mouth_off']
            ]), 'EFFECT')
            .appendField('in Farbe')
            .appendField(new Blockly.FieldDropdown([
                ['🟢 Grün', 'green'],
                ['🔵 Blau', 'blue'],
                ['🔴 Rot', 'red'],
                ['🟡 Gelb', 'yellow'],
                ['🟣 Lila', 'purple'],
                ['⚪ Weiß', 'white']
            ]), 'COLOR')
            .appendField('für')
            .appendField(new Blockly.FieldNumber(3, 1, 10), 'SECONDS')
            .appendField('Sek.');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.LIGHTS);
        this.setTooltip('Startet einen lebendigen Lichteffekt (z.B. Atmen oder rotierendes Kreislicht) an Brust und Mund.');
    }
};

// ==========================================
// ⏱️ STEUERUNG & PAUSEN (CONTROL)
// ==========================================

Blockly.Blocks['alpha_wait'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('⏱️ warte')
            .appendField(new Blockly.FieldNumber(1, 0.1, 60), 'SECONDS')
            .appendField('Sekunden');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.CONTROL);
        this.setTooltip('Hält das Programm für die angegebene Anzahl an Sekunden an.');
    }
};

Blockly.Blocks['alpha_repeat_times'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🔁 wiederhole')
            .appendField(new Blockly.FieldNumber(3, 1, 100), 'TIMES')
            .appendField('mal');
        this.appendStatementInput('DO')
            .appendField('mache');
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(ALPHA_COLORS.CONTROL);
        this.setTooltip('Führt die Blöcke darin mehrmals hintereinander aus.');
    }
};

Blockly.Blocks['alpha_forever'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('♾️ wiederhole fortlaufend');
        this.appendStatementInput('DO')
            .appendField('mache');
        this.setPreviousStatement(true, null);
        this.setColour(ALPHA_COLORS.CONTROL);
        this.setTooltip('Wiederholt die Blöcke endlos, bis das rote Stopp-Schild gedrückt wird.');
    }
};


// ==========================================
// 🔍 SENSOREN (SENSORS)
// ==========================================

Blockly.Blocks['alpha_sensor_battery'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🔋 Akkustand in %');
        this.setOutput(true, 'Number');
        this.setColour(ALPHA_COLORS.SENSORS);
        this.setTooltip('Gibt den aktuellen Akkustand des Roboters in Prozent zurück (0 bis 100).');
    }
};

Blockly.Blocks['alpha_sensor_charging'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('⚡ hängt am Ladekabel?');
        this.setOutput(true, 'Boolean');
        this.setColour(ALPHA_COLORS.SENSORS);
        this.setTooltip('Wahr (true), wenn der Roboter an das Ladekabel angeschlossen ist.');
    }
};

Blockly.Blocks['alpha_sensor_posture'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🧘 Körperhaltung ist')
            .appendField(new Blockly.FieldDropdown([
                ['stehend', 'standing'],
                ['liegend', 'lying'],
                ['in der Hocke', 'squatting']
            ]), 'POSTURE');
        this.setOutput(true, 'Boolean');
        this.setColour(ALPHA_COLORS.SENSORS);
        this.setTooltip('Prüft über den Lagesensor, ob der Roboter aufrecht steht, liegt oder hockt.');
    }
};

Blockly.Blocks['alpha_sensor_person'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('🚶 Mensch in der Nähe?');
        this.setOutput(true, 'Boolean');
        this.setColour(ALPHA_COLORS.SENSORS);
        this.setTooltip('Wahr, wenn der Infrarot-Sensor (PIR) eine Person vor dem Roboter bemerkt.');
    }
};

Blockly.Blocks['alpha_sensor_head'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('💆 Kopf wird berührt?');
        this.setOutput(true, 'Boolean');
        this.setColour(ALPHA_COLORS.SENSORS);
        this.setTooltip('Wahr, wenn eine Hand den Touch-Sensor oben auf dem Kopf berührt.');
    }
};
