/**
 * AlphaBlock - Hauptanwendung & UI-Koordination
 */

let workspace = null;
let simulator = null;
let executor = null;

// ==========================================
// BLOCKLY TOOLBOX KONFIGURATION (Kategorien)
// ==========================================
const TOOLBOX_XML = `
<xml id="toolbox" style="display: none">
  <category name="🚩 Ereignisse" colour="${ALPHA_COLORS.EVENTS}">
    <block type="alpha_when_start"></block>
    <block type="alpha_when_chest_button"></block>
    <block type="alpha_when_head_touched"></block>
    <block type="alpha_when_person_detected"></block>
    <block type="alpha_when_fallen"></block>
    <block type="alpha_when_heard"></block>
  </category>

  <category name="🗣️ Sprache" colour="${ALPHA_COLORS.SPEECH}">
    <block type="alpha_say"></block>
    <block type="alpha_say_mood"></block>
    <block type="alpha_sound"></block>
  </category>

  <category name="🕺 Bewegung" colour="${ALPHA_COLORS.MOTION}">
    <block type="alpha_walk"></block>
    <block type="alpha_turn"></block>
    <block type="alpha_action"></block>
    <block type="alpha_motor_move"></block>
    <block type="alpha_motor_relax"></block>
    <block type="alpha_head_turn"></block>
    <block type="alpha_stop_action"></block>
  </category>

  <category name="😊 Mimik" colour="${ALPHA_COLORS.LOOKS}">
    <block type="alpha_expression"></block>
  </category>

  <category name="💡 Lichter" colour="${ALPHA_COLORS.LIGHTS}">
    <block type="alpha_set_light"></block>
    <block type="alpha_light_effect"></block>
  </category>

  <category name="🔍 Sensoren" colour="${ALPHA_COLORS.SENSORS}">
    <block type="alpha_sensor_battery"></block>
    <block type="alpha_sensor_charging"></block>
    <block type="alpha_sensor_posture"></block>
    <block type="alpha_sensor_person"></block>
    <block type="alpha_sensor_head"></block>
  </category>

  <category name="⏱️ Steuerung" colour="${ALPHA_COLORS.CONTROL}">
    <block type="alpha_wait"></block>
    <block type="alpha_repeat_times"></block>
    <block type="alpha_forever"></block>
    <block type="controls_if"></block>
    <block type="controls_if">
      <mutation else="1"></mutation>
    </block>
  </category>

  <category name="🔢 Mathe & Logik" colour="#59C059">
    <block type="logic_compare"></block>
    <block type="logic_boolean"></block>
    <block type="math_number"></block>
    <block type="math_arithmetic"></block>
    <block type="math_random_int">
      <value name="FROM"><block type="math_number"><field name="NUM">1</field></block></value>
      <value name="TO"><block type="math_number"><field name="NUM">10</field></block></value>
    </block>
  </category>

  <category name="📦 Variablen" colour="#FF6680" custom="VARIABLE"></category>
</xml>
`;

// ==========================================
// INITIALISIERUNG
// ==========================================
window.addEventListener('DOMContentLoaded', () => {
    // 1. Simulator initialisieren
    simulator = new AlphaSimulator();

    // 2. Blockly Workspace initialisieren
    const blocklyArea = document.getElementById('blocklyArea');
    const blocklyDiv = document.getElementById('blocklyDiv');

    workspace = Blockly.inject(blocklyDiv, {
        toolbox: TOOLBOX_XML,
        collapse: true,
        comments: true,
        disable: false,
        maxBlocks: Infinity,
        trashcan: true,
        horizontalLayout: false,
        toolboxPosition: 'start',
        css: true,
        media: 'https://unpkg.com/blockly/media/',
        rtl: false,
        scrollbars: true,
        sounds: true,
        oneBasedIndex: true,
        grid: {
            spacing: 25,
            length: 3,
            colour: '#E2E8F0',
            snap: true
        },
        zoom: {
            controls: true,
            wheel: true,
            startScale: 1.05,
            maxScale: 2.5,
            minScale: 0.5,
            scaleSpeed: 1.15
        }
    });

    // 3. Executor initialisieren
    executor = new AlphaExecutor(workspace, simulator);

    // 4. Responsive Resize für Blockly
    const onResize = () => {
        let element = blocklyArea;
        let x = 0;
        let y = 0;
        do {
            x += element.offsetLeft;
            y += element.offsetTop;
            element = element.offsetParent;
        } while (element);
        blocklyDiv.style.left = x + 'px';
        blocklyDiv.style.top = y + 'px';
        blocklyDiv.style.width = blocklyArea.offsetWidth + 'px';
        blocklyDiv.style.height = blocklyArea.offsetHeight + 'px';
        Blockly.svgResize(workspace);
    };
    window.addEventListener('resize', onResize, false);
    onResize();

    // 5. Automatisch gespeichertes Projekt oder Beispiel laden
    const saved = localStorage.getItem('alphablock_autosave');
    if (saved) {
        try {
            const xml = Blockly.utils.xml.textToDom(saved);
            Blockly.Xml.domToWorkspace(xml, workspace);
        } catch (e) {
            loadExample('greeting');
        }
    } else {
        loadExample('greeting');
    }

    // Auto-Save bei Änderungen
    workspace.addChangeListener(() => {
        const xml = Blockly.Xml.workspaceToDom(workspace);
        const text = Blockly.Xml.domToText(xml);
        localStorage.setItem('alphablock_autosave', text);
        updatePythonPreview();
    });

    // Klick auf Start-Block oder Block-Stapel startet das Programm (wie in Scratch!)
    workspace.addChangeListener((event) => {
        if (event && event.type === 'click' && event.blockId) {
            const block = workspace.getBlockById(event.blockId);
            if (block && typeof block.getRootBlock === 'function') {
                const root = block.getRootBlock();
                if (root && root.type === 'alpha_when_start') {
                    console.log('[App] Start-Block oder Blockstapel angeklickt -> Starte Programm!');
                    if (executor && !executor.isRunning) {
                        executor.run();
                    }
                }
            }
        }
    });

    // 6. Event-Listener für UI-Buttons
    setupUIListeners();

    // 7. Regelmäßige Roboter-Verbindungsprüfung
    checkRobotConnection();
    setInterval(checkRobotConnection, 4000);
});

// ==========================================
// UI-LISTENER & BUTTONS
// ==========================================
function setupUIListeners() {
    // 🖥️ Modus Switch (Simulator vs Roboter)
    const modeSelect = document.getElementById('modeSelect');
    const notice = document.getElementById('robotModeNotice');

    const updateMode = (mode) => {
        executor.setMode(mode);
        if (notice) {
            notice.style.display = mode === 'robot' ? 'block' : 'none';
        }
        console.log('[App] Aktiver Ausführungsmodus:', mode);
    };

    if (modeSelect) {
        // Sofort beim Start mit dem Dropdown synchronisieren
        updateMode(modeSelect.value);

        modeSelect.addEventListener('change', (e) => {
            const mode = e.target.value;
            if (mode === 'simulator') {
                window._userManualSimulator = true;
            }
            updateMode(mode);
        });
    }

    // 👋 Sofort-Test: Winken (Testet sofort die USB-Verbindung und Servomotoren)
    const btnTestRobot = document.getElementById('btnTestRobot');
    if (btnTestRobot) {
        btnTestRobot.addEventListener('click', async () => {
            const origText = btnTestRobot.innerText;
            btnTestRobot.innerText = '⏳ Winkt...';
            btnTestRobot.disabled = true;
            try {
                const apiBase = (window.location && (window.location.protocol === 'http:' || window.location.protocol === 'https:'))
                    ? window.location.origin
                    : 'http://127.0.0.1:8080';
                await fetch(`${apiBase}/api/robot/action`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: '010' })
                });
                if (simulator) simulator.playAction('010');
            } catch (err) {
                console.error('[App] Test fehlgeschlagen:', err);
            } finally {
                setTimeout(() => {
                    btnTestRobot.innerText = origText;
                    btnTestRobot.disabled = false;
                }, 3000);
            }
        });
    }

    // 🚩 Start
    document.getElementById('btnStart').addEventListener('click', () => {
        if (modeSelect) {
            updateMode(modeSelect.value);
        }
        executor.run();
    });

    // 🛑 Stopp
    document.getElementById('btnStop').addEventListener('click', () => {
        executor.stop();
    });

    // 🎮 Sensor-Simulation Buttons
    const btnSimHead = document.getElementById('btnSimHead');
    if (btnSimHead) {
        btnSimHead.addEventListener('click', () => {
            if (simulator) simulator.triggerHeadTouch('touch');
        });
    }

    const btnSimPerson = document.getElementById('btnSimPerson');
    if (btnSimPerson) {
        btnSimPerson.addEventListener('click', () => {
            if (simulator) simulator.triggerPersonDetected();
        });
    }

    const btnSimFall = document.getElementById('btnSimFall');
    if (btnSimFall) {
        btnSimFall.addEventListener('click', () => {
            if (simulator) simulator.triggerFallen();
        });
    }

    const btnSimStand = document.getElementById('btnSimStand');
    if (btnSimStand) {
        btnSimStand.addEventListener('click', () => {
            if (simulator) simulator.playAction('reset_stand');
        });
    }

    // 💾 Speichern
    document.getElementById('btnSave').addEventListener('click', () => {
        const xml = Blockly.Xml.workspaceToDom(workspace);
        const text = Blockly.Xml.domToText(xml);
        const blob = new Blob([text], { type: 'application/xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `mein_alpha_mini_${new Date().toISOString().slice(0, 10)}.alphablock`;
        a.click();
        URL.revokeObjectURL(url);
    });

    // 📂 Öffnen
    const fileInput = document.getElementById('fileInput');
    document.getElementById('btnOpen').addEventListener('click', () => {
        fileInput.click();
    });
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                workspace.clear();
                const xml = Blockly.utils.xml.textToDom(event.target.result);
                Blockly.Xml.domToWorkspace(xml, workspace);
            } catch (err) {
                alert('Konnte die Datei leider nicht öffnen: ' + err.message);
            }
        };
        reader.readAsText(file);
    });

    // 📄 Neu
    document.getElementById('btnNew').addEventListener('click', () => {
        if (confirm('Möchtest du wirklich eine neue Arbeitsfläche starten? Nicht gespeicherte Blöcke gehen verloren.')) {
            workspace.clear();
            loadExample('greeting');
        }
    });

    // 💡 Beispiele Dropdown
    const exampleSelect = document.getElementById('exampleSelect');
    if (exampleSelect) {
        exampleSelect.addEventListener('change', (e) => {
            const key = e.target.value;
            if (key) {
                loadExample(key);
                e.target.value = ''; // Reset dropdown
            }
        });
    }

    // 🐍 Python Code Vorschau Toggle
    const btnViewCode = document.getElementById('btnViewCode');
    const codeModal = document.getElementById('codeModal');
    const btnCloseCode = document.getElementById('btnCloseCode');
    if (btnViewCode && codeModal) {
        btnViewCode.addEventListener('click', () => {
            updatePythonPreview();
            codeModal.classList.add('visible');
        });
        btnCloseCode.addEventListener('click', () => {
            codeModal.classList.remove('visible');
        });
    }

    // 📶 WLAN Modal & Steuerung
    const btnWifiConnect = document.getElementById('btnWifiConnect');
    const wifiModal = document.getElementById('wifiModal');
    const btnCloseWifi = document.getElementById('btnCloseWifi');
    const btnAutoWifiConnect = document.getElementById('btnAutoWifiConnect');
    const btnManualWifiConnect = document.getElementById('btnManualWifiConnect');
    const manualIpInput = document.getElementById('manualIpInput');
    const wifiStatusMsg = document.getElementById('wifiStatusMsg');

    if (btnWifiConnect && wifiModal) {
        btnWifiConnect.addEventListener('click', () => {
            if (wifiStatusMsg) wifiStatusMsg.innerText = '';
            wifiModal.classList.add('visible');
        });
        if (btnCloseWifi) {
            btnCloseWifi.addEventListener('click', () => {
                wifiModal.classList.remove('visible');
            });
        }
    }

    const triggerWifiConnect = async (targetIp = '') => {
        if (!wifiStatusMsg) return;
        wifiStatusMsg.style.color = '#3a86ff';
        wifiStatusMsg.innerText = '⏳ Verbinde mit Roboter über WLAN... Bitte kurz warten...';
        if (btnAutoWifiConnect) btnAutoWifiConnect.disabled = true;
        if (btnManualWifiConnect) btnManualWifiConnect.disabled = true;

        try {
            const apiBase = (window.location && (window.location.protocol === 'http:' || window.location.protocol === 'https:'))
                ? window.location.origin
                : 'http://127.0.0.1:8080';
            const res = await fetch(`${apiBase}/api/robot/connect_wifi`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ip: targetIp })
            });
            const data = await res.json();
            if (data.success) {
                wifiStatusMsg.style.color = '#2a9d8f';
                wifiStatusMsg.innerText = data.message || '🎉 Erfolgreich kabellos verbunden! USB-Kabel kann jetzt abgezogen werden!';
                await checkRobotConnection();
            } else {
                wifiStatusMsg.style.color = '#e63946';
                wifiStatusMsg.innerText = '❌ ' + (data.message || 'Verbindung fehlgeschlagen');
            }
        } catch (err) {
            wifiStatusMsg.style.color = '#e63946';
            wifiStatusMsg.innerText = '❌ Fehler: ' + err.message;
        } finally {
            if (btnAutoWifiConnect) btnAutoWifiConnect.disabled = false;
            if (btnManualWifiConnect) btnManualWifiConnect.disabled = false;
        }
    };

    if (btnAutoWifiConnect) {
        btnAutoWifiConnect.addEventListener('click', () => triggerWifiConnect(''));
    }
    if (btnManualWifiConnect && manualIpInput) {
        btnManualWifiConnect.addEventListener('click', () => {
            const ip = manualIpInput.value.trim();
            if (!ip) {
                alert('Bitte gib eine gültige IP-Adresse ein!');
                return;
            }
            triggerWifiConnect(ip);
        });
    }
}

// ==========================================
// HILFSFUNKTIONEN
// ==========================================

function loadExample(key) {
    const ex = ALPHA_EXAMPLES[key];
    if (!ex) return;
    workspace.clear();
    try {
        const xml = Blockly.utils.xml.textToDom(ex.xml);
        Blockly.Xml.domToWorkspace(xml, workspace);
        console.log(`[App] Beispiel geladen: ${ex.name}`);
    } catch (e) {
        console.error('Fehler beim Laden des Beispiels:', e);
    }
}

function updatePythonPreview() {
    const codeArea = document.getElementById('pythonCode');
    if (!codeArea || !workspace) return;
    try {
        const pyGen = (window.python && window.python.pythonGenerator) || Blockly.Python;
        const code = pyGen && typeof pyGen.workspaceToCode === 'function' ? pyGen.workspaceToCode(workspace) : '';
        codeArea.textContent = `# ===========================================\n# 🤖 Alpha Mini 2 - Python Programm\n# Generiert aus deinen AlphaBlock Blöcken!\n# ===========================================\nimport time\nfrom alphamini import robot\n\n${code || '# Ziehe Blöcke auf die Arbeitsfläche!'}\n\nif __name__ == '__main__':\n    start()\n`;
    } catch (e) {
        codeArea.textContent = '# Vorschau wird geladen...';
    }
}

async function checkRobotConnection() {
    const statusDot = document.getElementById('robotStatusDot');
    const statusText = document.getElementById('robotStatusText');
    if (!statusDot || !statusText) return;

    try {
        const apiBase = (window.location && (window.location.protocol === 'http:' || window.location.protocol === 'https:'))
            ? window.location.origin
            : 'http://127.0.0.1:8080';
        const res = await fetch(`${apiBase}/api/status`, { cache: 'no-store' });
        if (res.ok) {
            const data = await res.json();
            if (data.connected) {
                statusDot.className = 'status-dot online';
                statusText.innerText = `Roboter verbunden (${data.device_id || 'USB'})`;

                const modeSelect = document.getElementById('modeSelect');
                const notice = document.getElementById('robotModeNotice');
                if (modeSelect && !window._userManualSimulator && modeSelect.value !== 'robot') {
                    modeSelect.value = 'robot';
                    if (executor) executor.setMode('robot');
                    if (notice) notice.style.display = 'block';
                    console.log('[App] Roboter verbunden -> Automatisch auf Roboter-Modus gewechselt');
                }
                return;
            }
        }
    } catch (e) {
        // Offline / Standalone Simulator Mode
    }

    statusDot.className = 'status-dot offline';
    statusText.innerText = 'Simulator aktiv (Kein Roboter)';
}

// Globales Fehler-Logging zur Fehlersuche
window.addEventListener('error', (event) => {
    try {
        const apiBase = (window.location && (window.location.protocol === 'http:' || window.location.protocol === 'https:'))
            ? window.location.origin
            : 'http://127.0.0.1:8080';
        fetch(`${apiBase}/api/log`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: `Client Error: ${event.message} at ${event.filename}:${event.lineno}` })
        }).catch(() => {});
    } catch (e) {}
});
