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
    <block type="alpha_stop_action"></block>
    <block type="alpha_head_turn"></block>
  </category>

  <category name="😊 Mimik" colour="${ALPHA_COLORS.LOOKS}">
    <block type="alpha_expression"></block>
  </category>

  <category name="💡 Lichter" colour="${ALPHA_COLORS.LIGHTS}">
    <block type="alpha_set_light"></block>
    <block type="alpha_light_effect"></block>
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
        const res = await fetch('/api/status', { cache: 'no-store' });
        if (res.ok) {
            const data = await res.json();
            if (data.connected) {
                statusDot.className = 'status-dot online';
                statusText.innerText = `Roboter verbunden (${data.device_id || 'USB/WLAN'})`;

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
