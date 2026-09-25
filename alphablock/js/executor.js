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
        this.mode = 'robot'; // 'robot' als Standard!
        this.apiBase = (typeof window !== 'undefined' && window.location && (window.location.protocol === 'http:' || window.location.protocol === 'https:')) ? window.location.origin : 'http://127.0.0.1:8080';
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

    async sendRobotCommand(endpoint, data = {}) {
        if (this.mode !== 'robot') return;
        console.log(`[Executor] 🤖 Sende Roboter-Befehl an ${endpoint}:`, data);
        try {
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 6000);
            const res = await fetch(`${this.apiBase}${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
                signal: controller.signal
            });
            clearTimeout(timer);
            if (!res.ok) {
                console.warn(`[Executor] Roboter-Antwort Status: ${res.status}`);
            }
        } catch (err) {
            console.warn(`[Executor] Roboter-Befehl an ${endpoint} fehlgeschlagen:`, err);
        }
    }

    // ==========================================
    // BEFEHLE (SIMULATOR & ECHTER ROBOTER)
    // ==========================================

    async say(text, mood = 'normal') {
        if (this.shouldStop) return;

        const isRobotMode = (this.mode === 'robot');

        // 1. PC Sprachausgabe (damit Schüler die deutsche Sprache immer klar und deutlich hören)
        // und Anzeige im Simulator
        const simPromise = this.simulator.speak(text, mood, true);

        // 2. Falls echter Roboter aktiv: Zeige auch auf dem echten Roboter LCD-Augenmimik
        if (isRobotMode) {
            await this.sendRobotCommand('/api/robot/say', { text, mood });
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
        await this.sendRobotCommand('/api/robot/walk', { direction, steps });

        await simPromise;
    }

    async turn(direction = 'left', steps = 2) {
        if (this.shouldStop) return;

        // 1. Im Simulator animieren & Sound abspielen
        const simPromise = this.simulator.turn(direction, steps);

        // 2. Falls echter Roboter aktiv: HTTP-Befehl senden
        await this.sendRobotCommand('/api/robot/turn', { direction, steps });

        await simPromise;
    }

    async playAction(actionId) {
        if (this.shouldStop) return;

        this.simulator.playAction(actionId);

        await this.sendRobotCommand('/api/robot/action', { action: actionId });

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
            'lie_down': 3500, // Hinlegen
            'standup': 4000, // Aufstehen
            'reset_stand': 2500 // Grundstellung
        };
        const dur = actionDurations[actionId] || 2500;
        await this.wait(dur / 1000);
    }

    async stopAction() {
        this.simulator.stopAction();
        await this.sendRobotCommand('/api/robot/stop');
    }

    async turnHead(direction) {
        if (this.shouldStop) return;
        this.simulator.turnHead(direction);
        await this.delay(500);
    }

    async moveMotor(motorId, angle, durationSec = 1) {
        if (this.shouldStop) return;
        this.simulator.moveMotor(motorId, angle, durationSec);
        await this.sendRobotCommand('/api/robot/motor', {
            motor_id: motorId,
            angle: angle,
            duration: Math.round(durationSec * 1000)
        });
        await this.wait(durationSec);
    }

    async relaxMotors(unlock = true) {
        if (this.shouldStop) return;
        this.simulator.relaxMotors(unlock);
        await this.sendRobotCommand('/api/robot/motor_relax', { unlock });
        await this.delay(300);
    }

    async setExpression(expr) {
        if (this.shouldStop) return;
        this.simulator.setExpression(expr);

        await this.sendRobotCommand('/api/robot/express', { expression: expr });
        await this.delay(200);
    }

    async setLight(color) {
        if (this.shouldStop) return;
        this.simulator.setLight(color);

        await this.sendRobotCommand('/api/robot/light', { color });
        await this.delay(200);
    }

    async lightEffect(effect, color, seconds) {
        if (this.shouldStop) return;
        this.simulator.setLightEffect(effect, color, seconds);
        await this.sendRobotCommand('/api/robot/light', {
            color: color,
            effect: effect,
            duration: Math.round(seconds * 1000)
        });
        await this.wait(seconds);
    }

    // ==========================================
    // SENSOREN
    // ==========================================

    async fetchSensors() {
        const now = Date.now();
        if (this._lastSensorFetch && now - this._lastSensorFetch < 1000 && this._cachedSensors) {
            return this._cachedSensors;
        }
        try {
            const res = await fetch(`${this.apiBase}/api/robot/sensors`);
            if (res.ok) {
                this._cachedSensors = await res.json();
                this._lastSensorFetch = now;
                return this._cachedSensors;
            }
        } catch (e) {
            // Simulator Fallback
        }
        return {
            battery: this.simulator.batteryLevel !== undefined ? this.simulator.batteryLevel : 90,
            charging: Boolean(this.simulator.isCharging),
            posture: this.simulator.posture || 'standing',
            person_detected: Boolean(this.simulator.personDetected),
            head_touch: Boolean(this.simulator.headTouched)
        };
    }

    async getBattery() {
        const s = await this.fetchSensors();
        return s.battery !== undefined ? s.battery : 90;
    }

    async isCharging() {
        const s = await this.fetchSensors();
        return Boolean(s.charging);
    }

    async getPosture() {
        const s = await this.fetchSensors();
        return s.posture || this.simulator.posture || 'standing';
    }

    async isPersonNear() {
        const s = await this.fetchSensors();
        return Boolean(s.person_detected || this.simulator.personDetected);
    }

    async isHeadTouched() {
        const s = await this.fetchSensors();
        return Boolean(s.head_touch || this.simulator.headTouched);
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
