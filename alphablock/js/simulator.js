/**
 * AlphaBlock Simulator - Der virtuelle Alpha Mini im Browser
 * 
 * Ermöglicht es 5.-Klässlern, ihre Programme direkt am Bildschirm auszuprobieren:
 * - Animierte LCD-Augen (Lächeln, Zwinkern, Herzen, Traurig, etc.)
 * - Bunte, leuchtende LED-Ringe an Ohren und Brust (Atmen, Blinken)
 * - Sprechblase & echte deutsche Sprachausgabe über Web Speech API
 * - Prozedurale Soundeffekte über Web Audio API (keine externen MP3-Dateien nötig!)
 * - Animierte Bewegungen des Roboters (Winken, Tanzen, Hände hoch, Klatschen)
 */

class AlphaSimulator {
    constructor() {
        this.currentExpression = 'normal_1';
        this.currentLightColor = 'green';
        this.currentAction = 'idle';
        this.isSpeaking = false;
        this.audioCtx = null;
        this.synth = window.speechSynthesis;
        this.germanVoice = null;

        this.initElements();
        this.initVoice();
        this.setExpression('normal_1');
        this.setLight('green');
    }

    initElements() {
        this.robotContainer = document.getElementById('robotContainer');
        this.robotAvatar = document.getElementById('robotAvatar');
        this.robotShadow = document.getElementById('robotShadow');
        this.leftEye = document.getElementById('simLeftEye');
        this.rightEye = document.getElementById('simRightEye');
        this.leftEarLed = document.getElementById('simLeftEar');
        this.rightEarLed = document.getElementById('simRightEar');
        this.chestLed = document.getElementById('simChestLed');
        this.speechBubble = document.getElementById('simSpeechBubble');
        this.speechText = document.getElementById('simSpeechText');
        this.actionBadge = document.getElementById('simActionBadge');
    }

    initVoice() {
        if (!this.synth) return;
        const updateVoices = () => {
            const voices = this.synth.getVoices();
            // Finde eine deutsche Stimme (z.B. Google Deutsch, Microsoft Stefan/Hedda, etc.)
            this.germanVoice = voices.find(v => v.lang.startsWith('de')) || voices[0];
        };
        updateVoices();
        if (this.synth.onvoiceschanged !== undefined) {
            this.synth.onvoiceschanged = updateVoices;
        }
    }

    getAudioContext() {
        if (!this.audioCtx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            this.audioCtx = new AudioContext();
        }
        if (this.audioCtx.state === 'suspended') {
            this.audioCtx.resume();
        }
        return this.audioCtx;
    }

    // ==========================================
    // 👁️ LCD-AUGEN & MIMIK
    // ==========================================
    setExpression(expr) {
        this.currentExpression = expr;
        if (!this.leftEye || !this.rightEye) return;

        // Reset classes
        this.leftEye.className = 'eye';
        this.rightEye.className = 'eye';

        const eyeClasses = {
            'emo_007': 'eye-happy',       // Lächeln
            'emo_008': 'eye-excited',     // Begeistert
            'wink': 'eye-wink',           // Zwinkern
            'codemao8': 'eye-surprised',  // Überrascht
            'emo_014': 'eye-sad',         // Traurig
            'emo_006': 'eye-love',        // Herzchen
            'emo_010': 'eye-thinking',    // Nachdenklich
            'emo_002': 'eye-angry',       // Wütend
            'emo_004': 'eye-sleepy',      // Müde
            'normal_1': 'eye-normal'      // Normal
        };

        const cssClass = eyeClasses[expr] || 'eye-normal';
        this.leftEye.classList.add(cssClass);
        this.rightEye.classList.add(cssClass);

        if (expr === 'wink') {
            this.leftEye.classList.add('winking');
        }
    }

    // ==========================================
    // 💡 LEDS & LICHTER
    // ==========================================
    setLight(color) {
        this.currentLightColor = color;
        const colorMap = {
            'green': '#00FF66',
            'blue': '#00B4D8',
            'red': '#FF3366',
            'yellow': '#FFD166',
            'purple': '#B5179E',
            'white': '#FFFFFF',
            'off': '#333333'
        };

        const hex = colorMap[color] || '#00FF66';
        const isOff = color === 'off';

        [this.leftEarLed, this.rightEarLed, this.chestLed].forEach(el => {
            if (!el) return;
            el.className = 'led-indicator';
            if (isOff) {
                el.style.backgroundColor = '#222';
                el.style.boxShadow = 'none';
            } else {
                el.style.backgroundColor = hex;
                el.style.boxShadow = `0 0 14px ${hex}, 0 0 4px ${hex}`;
            }
        });
    }

    setLightEffect(effect, color, seconds) {
        this.setLight(color);
        const effectClass = effect === 'blink' ? 'led-blink' : 'led-breath';
        [this.leftEarLed, this.rightEarLed, this.chestLed].forEach(el => {
            if (el) el.classList.add(effectClass);
        });

        setTimeout(() => {
            [this.leftEarLed, this.rightEarLed, this.chestLed].forEach(el => {
                if (el) el.classList.remove('led-blink', 'led-breath');
            });
        }, seconds * 1000);
    }

    // ==========================================
    // 🗣️ SPRACHAUSGABE & SPRECHBLASE
    // ==========================================
    async speak(text, mood = 'normal', playAudio = true) {
        return new Promise(resolve => {
            if (this.speechBubble && this.speechText) {
                this.speechText.innerText = text;
                this.speechBubble.classList.add('visible');
            }

            if (this.robotAvatar) {
                this.robotAvatar.classList.add('speaking');
            }

            if (!playAudio || !this.synth) {
                setTimeout(() => {
                    this.stopSpeaking();
                    resolve();
                }, Math.max(1500, text.length * 80));
                return;
            }

            this.synth.cancel(); // Stop any pending utterance
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = 'de-DE';
            if (this.germanVoice) utterance.voice = this.germanVoice;

            // Mood-Anpassung
            if (mood === 'excited') {
                utterance.pitch = 1.3;
                utterance.rate = 1.15;
            } else if (mood === 'happy') {
                utterance.pitch = 1.15;
                utterance.rate = 1.05;
            } else if (mood === 'whisper') {
                utterance.pitch = 0.9;
                utterance.rate = 0.85;
                utterance.volume = 0.6;
            } else if (mood === 'sad') {
                utterance.pitch = 0.75;
                utterance.rate = 0.8;
            } else if (mood === 'robot') {
                utterance.pitch = 0.5;
                utterance.rate = 0.95;
            } else {
                utterance.pitch = 1.0;
                utterance.rate = 1.0;
            }

            utterance.onend = () => {
                this.stopSpeaking();
                resolve();
            };

            utterance.onerror = () => {
                this.stopSpeaking();
                resolve();
            };

            this.synth.speak(utterance);
        });
    }

    stopSpeaking() {
        if (this.speechBubble) {
            this.speechBubble.classList.remove('visible');
        }
        if (this.robotAvatar) {
            this.robotAvatar.classList.remove('speaking');
        }
    }

    // ==========================================
    // 🕺 ROBOTER-AKTIONEN & FORTBEWEGUNG
    // ==========================================
    async walk(direction = 'forward', steps = 2) {
        this.currentAction = `walk_${direction}`;
        if (!this.robotAvatar) return;

        this.stopAction();
        const isForward = direction === 'forward';
        const animClass = isForward ? 'anim-walk-forward' : 'anim-walk-backward';
        this.robotAvatar.classList.add(animClass);

        const dirText = isForward ? 'vorwärts' : 'rückwärts';
        if (this.actionBadge) {
            this.actionBadge.innerText = `🚶 Laufe ${steps} ${steps === 1 ? 'Schritt' : 'Schritte'} ${dirText}`;
            this.actionBadge.style.opacity = '1';
        }

        const stepMs = 700;
        for (let i = 0; i < steps; i++) {
            this.playSound('step');
            if (this.robotContainer) {
                const shiftY = isForward ? -6 * Math.min(i + 1, 3) : 6 * Math.min(i + 1, 3);
                const scaleVal = isForward ? 1 + 0.015 * Math.min(i + 1, 3) : 1 - 0.012 * Math.min(i + 1, 3);
                this.robotContainer.style.transform = `translateY(${shiftY}px) scale(${scaleVal})`;
            }
            await new Promise(r => setTimeout(r, stepMs));
        }

        // Sanft wieder auf Standposition
        await new Promise(r => setTimeout(r, 200));
        if (this.robotContainer) {
            this.robotContainer.style.transform = 'translateY(0) scale(1)';
        }
        this.stopAction();
    }

    async turn(direction = 'left', steps = 2) {
        this.currentAction = `turn_${direction}`;
        if (!this.robotAvatar) return;

        this.stopAction();
        const isLeft = direction === 'left';
        const animClass = isLeft ? 'anim-turn-left' : 'anim-turn-right';
        this.robotAvatar.classList.add(animClass);

        const dirText = isLeft ? 'links' : 'rechts';
        if (this.actionBadge) {
            this.actionBadge.innerText = `🔄 Drehe ${steps} ${steps === 1 ? 'Schritt' : 'Schritte'} ${dirText}`;
            this.actionBadge.style.opacity = '1';
        }

        const stepMs = 600;
        for (let i = 0; i < steps; i++) {
            this.playSound('step');
            if (this.robotContainer) {
                const rot = isLeft ? -8 : 8;
                this.robotContainer.style.transform = `rotate(${rot}deg)`;
            }
            await new Promise(r => setTimeout(r, stepMs));
        }

        await new Promise(r => setTimeout(r, 200));
        if (this.robotContainer) {
            this.robotContainer.style.transform = 'rotate(0deg)';
        }
        this.stopAction();
    }

    playAction(actionId) {
        this.currentAction = actionId;
        if (!this.robotAvatar) return;

        // Reset previous animations
        this.robotAvatar.className = 'robot-body';

        const actionNames = {
            '010': { name: '👋 Winken', anim: 'anim-wave' },
            '014': { name: '💃 Tai Chi Tanz', anim: 'anim-dance' },
            'pressup': { name: '💪 Liegestütze', anim: 'anim-pressup' },
            '017': { name: '🙌 Beide Arme hoch', anim: 'anim-arms-up' },
            '018': { name: '👏 Klatschen', anim: 'anim-clap' },
            '016': { name: '🙇 Verbeugen', anim: 'anim-bow' },
            '021': { name: '🤔 Nachdenken', anim: 'anim-think' },
            '031': { name: '🧘 Kniebeuge', anim: 'anim-squat' },
            'standup': { name: '🧍 Aufstehen', anim: 'anim-standup' },
            '011': { name: '👍 Nicken', anim: 'anim-nod' }
        };

        const action = actionNames[actionId] || { name: 'Bewegung...', anim: 'anim-dance' };
        if (this.actionBadge) {
            this.actionBadge.innerText = action.name;
            this.actionBadge.style.opacity = '1';
        }

        this.robotAvatar.classList.add(action.anim);
    }

    stopAction() {
        if (this.robotAvatar) {
            this.robotAvatar.className = 'robot-body';
        }
        if (this.robotContainer) {
            this.robotContainer.style.transform = 'translateY(0) scale(1) rotate(0deg)';
        }
        if (this.actionBadge) {
            this.actionBadge.style.opacity = '0';
        }
    }

    turnHead(direction) {
        if (!this.robotAvatar) return;
        const head = this.robotAvatar.querySelector('.robot-head');
        if (!head) return;

        head.classList.remove('head-left', 'head-right', 'head-up');
        if (direction === 'left') head.classList.add('head-left');
        else if (direction === 'right') head.classList.add('head-right');
        else if (direction === 'up') head.classList.add('head-up');
    }

    // ==========================================
    // 🎵 SOUNDEFFEKTE (Web Audio API Synthesizer)
    // ==========================================
    playSound(sound) {
        try {
            const ctx = this.getAudioContext();
            const now = ctx.currentTime;

            if (sound === 'step') {
                // Leiser sanfter Roboter-Schrittklang (Servo-Tap)
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(190, now);
                osc.frequency.exponentialRampToValueAtTime(70, now + 0.08);
                gain.gain.setValueAtTime(0.18, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.09);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.1);
            } else if (sound === 'beep') {
                // Niedliches Roboter-Piepsen
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(600, now);
                osc.frequency.exponentialRampToValueAtTime(1200, now + 0.15);
                gain.gain.setValueAtTime(0.3, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.2);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.25);
            } else if (sound === 'fanfare' || sound === 'cheer') {
                // Freudige Fanfare (3 aufsteigende Töne)
                [523.25, 659.25, 783.99, 1046.50].forEach((freq, i) => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(freq, now + i * 0.12);
                    gain.gain.setValueAtTime(0.25, now + i * 0.12);
                    gain.gain.exponentialRampToValueAtTime(0.01, now + i * 0.12 + 0.35);
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(now + i * 0.12);
                    osc.stop(now + i * 0.12 + 0.4);
                });
            } else if (sound === 'laugh') {
                // Kichern: Auf- und absteigende kurze Töne
                for (let i = 0; i < 6; i++) {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    const freq = 450 + (i % 2 === 0 ? 80 : 0);
                    osc.frequency.setValueAtTime(freq, now + i * 0.08);
                    gain.gain.setValueAtTime(0.2, now + i * 0.08);
                    gain.gain.linearRampToValueAtTime(0.01, now + i * 0.08 + 0.06);
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(now + i * 0.08);
                    osc.stop(now + i * 0.08 + 0.07);
                }
            } else if (sound === 'applause') {
                // Weißes Rauschen für Klatschen/Applaus
                const bufferSize = ctx.sampleRate * 1.5;
                const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
                const data = buffer.getChannelData(0);
                for (let i = 0; i < bufferSize; i++) {
                    data[i] = Math.random() * 2 - 1;
                }
                const noise = ctx.createBufferSource();
                noise.buffer = buffer;
                const filter = ctx.createBiquadFilter();
                filter.type = 'bandpass';
                filter.frequency.value = 1000;
                const gain = ctx.createGain();
                gain.gain.setValueAtTime(0.3, now);
                gain.gain.exponentialRampToValueAtTime(0.01, now + 1.4);
                noise.connect(filter);
                filter.connect(gain);
                gain.connect(ctx.destination);
                noise.start(now);
                noise.stop(now + 1.5);
            } else if (sound === 'horn') {
                // Tröte / Hupe
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(320, now);
                gain.gain.setValueAtTime(0.3, now);
                gain.gain.exponentialRampToValueAtTime(0.01, now + 0.4);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.45);
            } else if (sound === 'fart') {
                // Lustiger Furz-Sound (ein Schüler-Hit)
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(110, now);
                osc.frequency.exponentialRampToValueAtTime(50, now + 0.4);
                gain.gain.setValueAtTime(0.4, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.45);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.5);
            } else if (sound === 'bell') {
                // Helle Klingel
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(1200, now);
                gain.gain.setValueAtTime(0.3, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 1.2);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 1.3);
            }
        } catch (e) {
            console.warn('Sound synthesis failed:', e);
        }
    }
}

if (typeof window !== 'undefined') {
    window.AlphaSimulator = AlphaSimulator;
}
