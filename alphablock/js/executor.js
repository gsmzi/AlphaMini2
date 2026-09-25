/**
 * AlphaBlock Execution Engine (Ausführungs-Steuerung)
 * 
 * Steuert die schrittweise Ausführung:
 * - Hebt den aktiven Block gelb hervor (wie in Scratch!)
 * - Unterstützt Pausen (await wait) und Schleifen
 * - Reagiert sofort auf das rote Stopp-Schild 🛑
 * - Sendet Befehle wahlweise an den Simulator und/oder den echten Roboter
 */

class AlphaExecutor {
    constructor(workspace, simulator) {
        this.workspace = workspace;
        this.simulator = simulator;
        this.isRunning = false;
        this.shouldStop = false;
        this.mode = 'simulator'; // 'simulator' oder 'robot'
        this.apiBase = (typeof window !== 'undefined' && window.location && window.location.origin) ? window.location.origin : 'http://localhost:8080';
        this.currentHighlightedBlock = null;
    }

    setMode(mode) {
        this.mode = mode; // 'simulator' | 'robot'
        console.log(`[Executor] Modus gewechselt zu: ${mode}`);
    }

    async highlightBlock(blockId) {
        if (this.shouldStop) return;
        this.currentHighlightedBlock = blockId;
        if (this.workspace) {
            try {
                if (typeof this.workspace.highlightBlock === 'function') {
                    this.workspace.highlightBlock(blockId);
                } else if (typeof this.workspace.getBlockById === 'function') {
                    const block = this.workspace.getBlockById(blockId);
                    if (block && typeof block.setHighlighted === 'function') {
                        block.setHighlighted(true);
                    }
                }
            } catch (e) {
                // Ignore highlight errors gracefully
            }
        }
        // Kurze Mikropause für die visuelle Verfolgung
        await this.delay(60);
    }

    clearHighlight() {
        if (this.workspace) {
            try {
                if (typeof this.workspace.highlightBlock === 'function') {
                    this.workspace.highlightBlock(null);
                } else if (this.currentHighlightedBlock && typeof this.workspace.getBlockById === 'function') {
                    const block = this.workspace.getBlockById(this.currentHighlightedBlock);
                    if (block && typeof block.setHighlighted === 'function') {
                        block.setHighlighted(false);
                    }
                }
            } catch (e) {
                // Ignore
            }
        }
        this.currentHighlightedBlock = null;
    }

    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async wait(seconds) {
        const totalMs = seconds * 1000;
        const stepMs = 50;
        let elapsed = 0;
        while (elapsed < totalMs && !this.shouldStop) {
            await this.delay(stepMs);
            elapsed += stepMs;
        }
    }

    // ==========================================
    // BEFEHLE (SIMULATOR & ECHTER ROBOTER)
    // ==========================================

    async say(text, mood = 'normal') {
        if (this.shouldStop) return;

        const isRobotMode = (this.mode === 'robot');

        // 1. Im Simulator anzeigen (Audio nur am PC abspielen, wenn nicht im Roboter-Modus)
        const simPromise = this.simulator.speak(text, mood, !isRobotMode);

        // 2. Falls echter Roboter aktiv: HTTP-Befehl senden
        if (isRobotMode) {
            try {
                await fetch(`${this.apiBase}/api/robot/say`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ text, mood })
                });
            } catch (err) {
                console.warn('[Executor] Roboter-Sprachbefehl fehlgeschlagen:', err);
            }
        }

        await simPromise;
    }

    async playSound(sound) {
        if (this.shouldStop) return;
        this.simulator.playSound(sound);
        await this.delay(400);
    }

    async walk(direction = 'forward', steps = 2) {
        if (this.shouldStop) return;

        // 1. Im Simulator animieren & Sound abspielen
        const simPromise = this.simulator.walk(direction, steps);

        // 2. Falls echter Roboter aktiv: HTTP-Befehl senden
        if (this.mode === 'robot') {
            try {
                await fetch(`${this.apiBase}/api/robot/walk`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ direction, steps })
                });
            } catch (err) {
                console.warn('[Executor] Roboter-Laufbefehl fehlgeschlagen:', err);
            }
        }

        await simPromise;
    }

    async turn(direction = 'left', steps = 2) {
        if (this.shouldStop) return;

        // 1. Im Simulator animieren & Sound abspielen
        const simPromise = this.simulator.turn(direction, steps);

        // 2. Falls echter Roboter aktiv: HTTP-Befehl senden
        if (this.mode === 'robot') {
            try {
                await fetch(`${this.apiBase}/api/robot/turn`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ direction, steps })
                });
            } catch (err) {
                console.warn('[Executor] Roboter-Drehbefehl fehlgeschlagen:', err);
            }
        }

        await simPromise;
    }

    async playAction(actionId) {
        if (this.shouldStop) return;

        this.simulator.playAction(actionId);

        if (this.mode === 'robot') {
            try {
                await fetch(`${this.apiBase}/api/robot/action`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: actionId })
                });
            } catch (err) {
                console.warn('[Executor] Roboter-Aktionsbefehl fehlgeschlagen:', err);
            }
        }

        // Warte je nach Aktion eine sinnvolle Zeit
        const actionDurations = {
            '010': 3000, // Winken
            '014': 5500, // Tanzen
            'pressup': 4500, // Liegestütze
            '017': 2500, // Hände hoch
            '018': 3000, // Klatschen
            '016': 2800, // Verbeugen
            '021': 3000, // Nachdenken
            '031': 3500, // Kniebeuge
            'standup': 4000
        };
        const dur = actionDurations[actionId] || 2500;
        await this.wait(dur / 1000);
    }

    async stopAction() {
        this.simulator.stopAction();
        if (this.mode === 'robot') {
            try {
                await fetch(`${this.apiBase}/api/robot/stop`, { method: 'POST' });
            } catch (err) {
                console.warn('[Executor] Roboter-Stopp fehlgeschlagen:', err);
            }
        }
    }

    async turnHead(direction) {
        if (this.shouldStop) return;
        this.simulator.turnHead(direction);
        await this.delay(500);
    }

    async setExpression(expr) {
        if (this.shouldStop) return;
        this.simulator.setExpression(expr);

        if (this.mode === 'robot') {
            try {
                await fetch(`${this.apiBase}/api/robot/express`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ expression: expr })
                });
            } catch (err) {
                console.warn('[Executor] Roboter-Mimik fehlgeschlagen:', err);
            }
        }
        await this.delay(200);
    }

    async setLight(color) {
        if (this.shouldStop) return;
        this.simulator.setLight(color);

        if (this.mode === 'robot') {
            try {
                await fetch(`${this.apiBase}/api/robot/light`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ color })
                });
            } catch (err) {
                console.warn('[Executor] Roboter-Licht fehlgeschlagen:', err);
            }
        }
    }

    async lightEffect(effect, color, seconds) {
        if (this.shouldStop) return;
        this.simulator.setLightEffect(effect, color, seconds);
        await this.wait(seconds);
    }

    // ==========================================
    // PROGRAMM-STEUERUNG
    // ==========================================

    async run() {
        if (this.isRunning) return;
        this.isRunning = true;
        this.shouldStop = false;

        document.body.classList.add('program-running');
        console.log('[Executor] Starte Programm...');

        // Generiere JavaScript-Code aus den Blöcken
        const jsGen = (window.javascript && window.javascript.javascriptGenerator) || Blockly.JavaScript;
        if (!jsGen || typeof jsGen.workspaceToCode !== 'function') {
            console.error('[Executor] JavaScript Generator nicht gefunden!');
            alert('Fehler: Der Code-Generator konnte nicht initialisiert werden.');
            this.stop();
            return;
        }

        const code = jsGen.workspaceToCode(this.workspace);
        console.log('[Executor] Generierter Code:\n', code);

        if (!code || !code.trim()) {
            console.warn('[Executor] Keine ausführbaren Blöcke im Workspace.');
            this.stop();
            return;
        }

        try {
            // Ausführen als async function mit runner als Kontext
            const runner = this;
            const asyncFunction = new Function('runner', `return (async () => {\n${code}\n})();`);
            await asyncFunction(runner);
        } catch (err) {
            console.error('[Executor] Fehler bei der Ausführung:', err);
            alert(`Hoppla! Da ist ein kleiner Fehler aufgetreten:\n${err.message}`);
        } finally {
            this.stop();
        }
    }

    stop() {
        this.shouldStop = true;
        this.isRunning = false;
        this.clearHighlight();
        document.body.classList.remove('program-running');

        // Simulator stoppen
        this.simulator.stopSpeaking();
        this.simulator.stopAction();

        // Roboter stoppen
        if (this.mode === 'robot') {
            fetch(`${this.apiBase}/api/robot/stop`, { method: 'POST' }).catch(() => {});
        }

        console.log('[Executor] Programm gestoppt.');
    }
}

if (typeof window !== 'undefined') {
    window.AlphaExecutor = AlphaExecutor;
}
