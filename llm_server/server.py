"""
LLM Server for Robot Voice Dialogue - CONTINUOUS CONVERSATION
==============================================================
A Flask server that provides natural language responses with:
- Multi-turn conversation support with session management
- Context tracking across turns
- Stop phrase detection
- Structured JSON responses with session control fields

Uses Vosk for FREE offline speech recognition.
Uses OpenAI GPT for natural responses (optional).

Usage:
1. Run: python server.py (will auto-download Vosk model)
2. Optionally set OPENAI_API_KEY for GPT responses
3. Robot app connects to http://YOUR_PC_IP:8080
"""

from flask import Flask, request, jsonify
import os
import json
import re
import base64
import tempfile
import wave
import struct
from datetime import datetime
from pathlib import Path
from collections import defaultdict
import time

app = Flask(__name__)

# ============== SESSION MANAGEMENT ==============
# Store conversation sessions in memory
sessions = defaultdict(lambda: {
    "turns": [],
    "summary": "",
    "last_activity": time.time(),
    "turn_count": 0,
    "last_followup_turn": -3  # Allow followup from start
})

SESSION_TIMEOUT = 300  # 5 minutes

def cleanup_old_sessions():
    """Remove sessions older than timeout"""
    now = time.time()
    expired = [sid for sid, sess in sessions.items()
               if now - sess["last_activity"] > SESSION_TIMEOUT]
    for sid in expired:
        del sessions[sid]

def get_session(session_id):
    """Get or create a session"""
    cleanup_old_sessions()
    session = sessions[session_id]
    session["last_activity"] = time.time()
    return session

def update_session(session_id, user_input, robot_response):
    """Update session with a new turn"""
    session = get_session(session_id)
    session["turns"].append({
        "user": user_input,
        "robot": robot_response,
        "timestamp": time.time()
    })
    session["turn_count"] += 1

    # Compress history if too long (keep last 2 turns verbatim)
    if len(session["turns"]) > 5:
        old_turns = session["turns"][:-2]
        # Create summary from old turns
        summary_parts = []
        for turn in old_turns:
            intent = extract_intent(turn["user"])
            summary_parts.append(f"User asked about {intent}")
        session["summary"] = ". ".join(summary_parts[-3:])
        session["turns"] = session["turns"][-2:]

def should_ask_followup(session_id):
    """Check if we should ask a follow-up question (max once per 2-3 turns)"""
    session = get_session(session_id)
    return session["turn_count"] - session["last_followup_turn"] >= 3

def mark_followup_asked(session_id):
    """Mark that we asked a follow-up"""
    session = get_session(session_id)
    session["last_followup_turn"] = session["turn_count"]

def extract_intent(text):
    """Extract simple intent from user input"""
    text = text.lower()
    if "weather" in text or "wetter" in text:
        return "weather"
    elif "time" in text or "uhr" in text or "zeit" in text:
        return "time"
    elif "name" in text or "heißt" in text:
        return "name"
    elif "dance" in text or "tanz" in text:
        return "dance"
    elif "joke" in text or "witz" in text:
        return "joke"
    elif "bye" in text or "tschüss" in text or "goodbye" in text:
        return "farewell"
    else:
        return "general"

# ============== STOP PHRASE DETECTION ==============
STOP_PHRASES_EN = [
    "goodbye", "bye", "that's all", "thats all", "stop", "go to sleep",
    "i'm done", "im done", "thank you bye", "end conversation", "quit",
    "that is all", "no more", "nothing else", "i'm finished", "enough"
]

STOP_PHRASES_DE = [
    "tschüss", "tschuss", "süss", "süß", "tschuess",  # Common misrecognitions of Tschüss
    "auf wiedersehen", "wiedersehen", "das war's", "das wars", "stopp",
    "schlaf", "ich bin fertig", "danke tschüss", "beenden",
    "ende", "nichts mehr", "das reicht", "genug", "fertig",
    "ciao", "bye", "tschau", "servus"  # Additional German goodbyes
]

def is_stop_phrase(text):
    """Check if text contains a stop phrase"""
    text_lower = text.lower().strip()
    all_phrases = STOP_PHRASES_EN + STOP_PHRASES_DE
    return any(phrase in text_lower for phrase in all_phrases)

# ============== VOSK SPEECH RECOGNITION ==============
VOSK_AVAILABLE = False
vosk_model = None

try:
    from vosk import Model, KaldiRecognizer
    VOSK_AVAILABLE = True
except ImportError:
    print("WARNING: vosk not installed. Run: pip install vosk")

vosk_model_en = None
vosk_model_de = None

def init_vosk():
    """Initialize Vosk models for English and German"""
    global vosk_model, vosk_model_en, vosk_model_de

    if not VOSK_AVAILABLE:
        return False

    import urllib.request
    import zipfile

    # English model
    en_path = Path(__file__).parent / "vosk-model-small-en-us-0.15"
    if not en_path.exists():
        print("Downloading English Vosk model (~40MB)...")
        try:
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            zip_path = Path(__file__).parent / "vosk-en.zip"
            urllib.request.urlretrieve(url, zip_path)
            with zipfile.ZipFile(zip_path, 'r') as z:
                z.extractall(Path(__file__).parent)
            zip_path.unlink()
        except Exception as e:
            print(f"Failed to download English model: {e}")

    # German model
    de_path = Path(__file__).parent / "vosk-model-small-de-0.15"
    if not de_path.exists():
        print("Downloading German Vosk model (~40MB)...")
        try:
            url = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
            zip_path = Path(__file__).parent / "vosk-de.zip"
            urllib.request.urlretrieve(url, zip_path)
            with zipfile.ZipFile(zip_path, 'r') as z:
                z.extractall(Path(__file__).parent)
            zip_path.unlink()
        except Exception as e:
            print(f"Failed to download German model: {e}")

    # Load models
    try:
        if en_path.exists():
            vosk_model_en = Model(str(en_path))
            vosk_model = vosk_model_en  # Default
            print("English Vosk model loaded!")
        if de_path.exists():
            vosk_model_de = Model(str(de_path))
            print("German Vosk model loaded!")
        return vosk_model is not None
    except Exception as e:
        print(f"Failed to load Vosk model: {e}")
        return False

def transcribe_with_vosk(audio_bytes, language="en"):
    """Transcribe audio using Vosk with appropriate language model"""
    model = vosk_model_de if language.startswith("de") and vosk_model_de else vosk_model_en

    if not model:
        print("No Vosk model available!")
        return None

    try:
        print(f"Transcribing [{language}] {len(audio_bytes)} bytes...", flush=True)

        rec = KaldiRecognizer(model, 16000)

        chunk_size = 4000
        for i in range(0, len(audio_bytes), chunk_size):
            rec.AcceptWaveform(audio_bytes[i:i + chunk_size])

        result = json.loads(rec.FinalResult())
        text = result.get('text', '').strip()

        print(f"Heard: '{text}'", flush=True)
        return text if text else None

    except Exception as e:
        print(f"Transcription error: {e}")
        return None

# ============== OPENAI (OPTIONAL) ==============
OPENAI_AVAILABLE = False
client = None

try:
    from openai import OpenAI
    OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
    if OPENAI_API_KEY:
        client = OpenAI(api_key=OPENAI_API_KEY)
        OPENAI_AVAILABLE = True
except ImportError:
    pass

# ============== WEATHER API (FREE) ==============
REQUESTS_AVAILABLE = False
try:
    import requests
    REQUESTS_AVAILABLE = True
except ImportError:
    pass

def get_weather(city="Munich"):
    """Get weather using free wttr.in API (no key needed)"""
    if not REQUESTS_AVAILABLE:
        return None
    try:
        url = f"https://wttr.in/{city}?format=j1"
        response = requests.get(url, timeout=5, headers={"User-Agent": "curl"})
        if response.status_code == 200:
            data = response.json()
            current = data.get("current_condition", [{}])[0]
            temp = current.get("temp_C", "?")
            desc = current.get("weatherDesc", [{}])[0].get("value", "unknown")
            return {
                "city": city,
                "temp": float(temp) if temp != "?" else 0,
                "desc": desc.lower()
            }
    except Exception as e:
        print(f"Weather API error: {e}")
    return None

# ============== RESPONSE GENERATION ==============

EMOTION_MAP = {
    "happy": {"expression": "emo_007", "action": "010", "light": "green", "chinese": "开心"},
    "sad": {"expression": "emo_014", "action": "016", "light": "blue", "chinese": "抱歉"},
    "thinking": {"expression": "emo_010", "action": "021", "light": "blue", "chinese": "思考"},
    "excited": {"expression": "emo_008", "action": "014", "light": "purple", "chinese": "兴奋"},
    "surprised": {"expression": "codemao8", "action": "018", "light": "yellow", "chinese": "惊讶"},
    "neutral": {"expression": "normal_1", "action": "011", "light": "normal", "chinese": "中性"},
    "comfort": {"expression": "emo_006", "action": "011", "light": "normal", "chinese": "安慰"},
}

# Explicit action overrides (for action commands like "dance", "wave", "hands up")
# IMPORTANT: Use plain numeric IDs that match the robot SDK (NOT "action_XXX" format)
# Action IDs from ActionActivity.kt:
# 010 = wave/greeting, 011 = nod, 014 = Tai Chi dance, 016 = bow
# 017 = hands up, 018 = clap, 021 = think, 031 = squat
ACTION_OVERRIDE_MAP = {
    "dance": "014",      # Tai Chi dance
    "wave": "010",       # Wave/greeting
    "hands_up": "017",   # Hands up
    "clap": "018",       # Clap
    "bow": "016",        # Bow
    "nod": "011",        # Nod
    "think": "021",      # Think
    "idle": "011",       # Idle/nod
    "greeting": "010"    # Greeting/wave
}

def generate_response(user_text, language="en", session_context=None):
    """
    Generate response with CONTINUOUS CONVERSATION support.

    Returns: (speech, emotion, action, keep_session, conversation_end, ask_followup)
    """
    text = user_text.lower().strip()
    is_german = language.startswith("de")

    # Check for stop phrases
    if is_stop_phrase(text):
        goodbye = "Tschüss! Es war schön mit dir zu sprechen." if is_german else "Goodbye! It was nice talking with you."
        return goodbye, "happy", "wave", False, True, False

    # Use OpenAI if available (with context)
    if OPENAI_AVAILABLE and client:
        try:
            return generate_openai_response(user_text, language, session_context)
        except Exception as e:
            print(f"OpenAI error: {e}")

    # Rule-based responses (with session awareness)
    return generate_rule_based_response(text, is_german)

def generate_openai_response(user_text, language, session_context):
    """Generate response using OpenAI with context"""
    is_german = language.startswith("de")

    # Build context messages
    messages = [
        {"role": "system", "content": get_system_prompt(language)}
    ]

    # Add session context
    if session_context:
        if session_context.get("summary"):
            messages.append({
                "role": "system",
                "content": f"Previous conversation summary: {session_context['summary']}"
            })

        # Add recent turns
        for turn in session_context.get("recent_turns", [])[-2:]:
            if turn.get("role") == "user":
                messages.append({"role": "user", "content": turn["content"]})
            else:
                messages.append({"role": "assistant", "content": turn["content"]})

    messages.append({"role": "user", "content": user_text})

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        max_tokens=150,
        temperature=0.7
    )

    resp_text = response.choices[0].message.content

    # Try to parse as JSON
    try:
        # Find JSON in response
        json_match = re.search(r'\{[^}]+\}', resp_text, re.DOTALL)
        if json_match:
            parsed = json.loads(json_match.group())
            return (
                parsed.get("speech", resp_text),
                parsed.get("emotion", "neutral").lower(),
                parsed.get("action", "nod").lower(),
                parsed.get("keep_session", True),
                parsed.get("conversation_end", False),
                parsed.get("ask_followup", False)
            )
    except:
        pass

    # Parse emotion/action from text
    emotion = "happy"
    action = "nod"

    em = re.search(r'\[EMOTION:\s*(\w+)\]', resp_text, re.I)
    if em:
        emotion = em.group(1).lower()

    ac = re.search(r'\[ACTION:\s*(\w+)\]', resp_text, re.I)
    if ac:
        action = ac.group(1).lower()

    # Clean text
    clean = re.sub(r'\[EMOTION:\s*\w+\]', '', resp_text)
    clean = re.sub(r'\[ACTION:\s*\w+\]', '', clean).strip()

    return clean, emotion, action, True, False, False

def generate_rule_based_response(text, is_german):
    """Generate rule-based response with fuzzy matching for action commands"""

    # Empty input - be silent instead of saying "please repeat"
    if not text or len(text.strip()) < 2:
        return ("", "neutral", "idle", True, False, False)

    # ═══════════════════════════════════════════════════════════════
    # ACTION COMMANDS - Check these FIRST with extensive fuzzy matching
    # ═══════════════════════════════════════════════════════════════

    # Dance - extensive fuzzy matching for Vosk misrecognitions
    dance_words = ["dance", "dances", "dancing", "dans", "danc", "tanz", "tanzen", "tanzt", "dancer", "danced",
                   "thus", "then", "stance", "chance", "dense", "tense", "dent", "danz", "dunce",
                   "can you dance", "let's dance", "do a dance", "show me a dance", "tai chi",
                   "tents", "tens", "den", "tan", "hands", "pants", "ants", "lance", "glance",
                   "france", "advance", "enhance", "romance", "prance", "dance for me"]
    if any(w in text for w in dance_words):
        print(f"[ACTION] Dance detected in: '{text}'", flush=True)
        responses = [
            ("Yeehaw! Tanzzeit!" if is_german else "Woohoo! Dance time!"),
            ("Lass uns grooven!" if is_german else "Let's groove!"),
            ("Schau mir zu!" if is_german else "Watch me move!"),
        ]
        import random
        return random.choice(responses), "excited", "dance", True, False, False

    # Wave - extensive fuzzy matching
    wave_words = ["wave", "waves", "waving", "weve", "we've", "weave", "waive", "wav", "waved",
                  "wink", "winke", "winken", "winkt", "wait", "wade", "away", "rave", "gave",
                  "save", "brave", "grave", "cave", "pave", "shave", "wave at me", "say hi",
                  "wave hello", "wave your hand", "way", "weighs", "ways", "wake", "make"]
    if any(w in text for w in wave_words):
        print(f"[ACTION] Wave detected in: '{text}'", flush=True)
        responses = [
            ("Hey hey hey!" if is_german else "Hey there, friend!"),
            ("Hallo Freund!" if is_german else "Hi hi hi!"),
            ("Grüß dich!" if is_german else "Hello hello!"),
        ]
        import random
        return random.choice(responses), "happy", "wave", True, False, False

    # Hands up - extensive fuzzy matching
    hands_up_words = ["hands up", "hand up", "hands-up", "handsup", "raise hands", "raise your hands",
                      "put your hands up", "arms up", "ends up", "hands app", "hans up", "and up",
                      "hände hoch", "hande hoch", "arme hoch", "haende hoch", "ende hoch",
                      "hands", "hand", "raise", "up up", "reach up", "hands in the air",
                      "put them up", "stick em up", "reach for the sky", "high five",
                      "ans up", "ands up", "and zap", "hands out", "hands op"]
    if any(w in text for w in hands_up_words):
        print(f"[ACTION] Hands up detected in: '{text}'", flush=True)
        responses = [
            ("Hände hoch, nicht schießen!" if is_german else "Hands up, don't shoot!"),
            ("Juhu! Hände hoch!" if is_german else "Woohoo! Hands up!"),
            ("Yeah! So macht man das!" if is_german else "Yeah! That's how we do it!"),
        ]
        import random
        return random.choice(responses), "excited", "hands_up", True, False, False

    # Clap - with variations
    clap_words = ["clap", "claps", "clapping", "klap", "klatsch", "klatschen", "applaud", "applause", "clapped",
                  "clap your hands", "give me a clap", "cap", "crap", "flap", "slap", "lap", "map", "tap"]
    if any(w in text for w in clap_words):
        print(f"[ACTION] Clap detected in: '{text}'", flush=True)
        responses = [
            ("Bravo! Bravo!" if is_german else "Awesome! Clap clap!"),
            ("Applaus!" if is_german else "Give it up!"),
        ]
        import random
        return random.choice(responses), "excited", "clap", True, False, False

    # Bow - with variations
    bow_words = ["bow", "bows", "bowing", "verbeugen", "verbeug", "verbeugung", "bowed",
                 "take a bow", "show respect", "how", "now", "wow", "vow", "cow", "row"]
    if any(w in text for w in bow_words):
        print(f"[ACTION] Bow detected in: '{text}'", flush=True)
        responses = [
            ("Zu Ihren Diensten!" if is_german else "At your service!"),
            ("Es ist mir eine Ehre!" if is_german else "The pleasure is mine!"),
        ]
        import random
        return random.choice(responses), "happy", "bow", True, False, False

    # ═══════════════════════════════════════════════════════════════
    # INFORMATION QUERIES
    # ═══════════════════════════════════════════════════════════════

    # Weather
    if "weather" in text or "wetter" in text:
        w = get_weather()
        if w:
            speech = f"{w['desc']}, {w['temp']:.0f} Grad." if is_german else f"{w['desc']}, {w['temp']:.0f} degrees."
            return speech, "happy", "nod", True, False, False
        return ("Wetter nicht verfügbar." if is_german else "Weather unavailable."), "sad", "idle", True, False, False

    # Time
    if "time" in text or "uhr" in text or "zeit" in text or "spät" in text:
        t = datetime.now().strftime("%H:%M")
        return (f"Es ist {t} Uhr." if is_german else f"It's {t}."), "neutral", "nod", True, False, False

    # Date
    if "date" in text or "day" in text or "heute" in text or "datum" in text:
        d = datetime.now().strftime("%A, %B %d")
        return (f"Heute ist {d}." if is_german else f"Today is {d}."), "neutral", "nod", True, False, False

    # ═══════════════════════════════════════════════════════════════
    # CONVERSATION - Fun and engaging responses!
    # ═══════════════════════════════════════════════════════════════
    import random

    # How are you
    if "how are you" in text or "wie geht" in text or "how do you feel" in text:
        responses_en = ["I'm fantastic! Ready to party!", "Super duper! Wanna dance?", "Feeling awesome today!"]
        responses_de = ["Mir geht's fantastisch!", "Super duper! Willst du tanzen?", "Ich fühl mich toll!"]
        return (random.choice(responses_de) if is_german else random.choice(responses_en)), "excited", "wave", True, False, False

    # Name
    if "name" in text or "wer bist" in text or "heißt" in text or "heisst" in text or "who are you" in text:
        responses_en = ["I'm Alpha Mini, your robot buddy!", "Call me Alpha Mini! Nice to meet you!", "Alpha Mini at your service!"]
        responses_de = ["Ich bin Alpha Mini, dein Roboter-Kumpel!", "Nenn mich Alpha Mini!", "Alpha Mini zu deinen Diensten!"]
        return (random.choice(responses_de) if is_german else random.choice(responses_en)), "happy", "wave", True, False, False

    # Thanks
    if "thank" in text or "danke" in text:
        responses_en = ["You're awesome!", "Anytime, friend!", "Happy to help!"]
        responses_de = ["Du bist toll!", "Immer gerne!", "Freut mich zu helfen!"]
        return (random.choice(responses_de) if is_german else random.choice(responses_en)), "happy", "bow", True, False, False

    # Help - tell user what robot can do
    if "help" in text or "hilfe" in text or "can you" in text or "kannst" in text or "what can you" in text:
        responses_en = ["I can dance, wave, raise my hands, and clap! Try me!", "Say dance, wave, or hands up! I'm ready!"]
        responses_de = ["Ich kann tanzen, winken, Hände hoch und klatschen!", "Sag Tanz, Winke oder Hände hoch!"]
        return (random.choice(responses_de) if is_german else random.choice(responses_en)), "excited", "wave", True, False, False

    # Joke
    if "joke" in text or "witz" in text or "funny" in text or "lustig" in text:
        jokes_en = ["Why did the robot go on vacation? To recharge its batteries!", "I told a joke to my CPU. It didn't compute!", "Robots don't get tired. We just need a quick byte!"]
        jokes_de = ["Warum macht der Roboter Urlaub? Batterien laden!", "Ich erzählte meiner CPU einen Witz. Hat nicht gerechnet!", "Roboter werden nicht müde. Nur ein kurzer Byte!"]
        return (random.choice(jokes_de) if is_german else random.choice(jokes_en)), "excited", "clap", True, False, False

    # Greetings
    greetings_en = ["hello", "hi", "hey", "good morning", "good afternoon", "good evening"]
    greetings_de = ["hallo", "guten tag", "guten morgen", "guten abend", "servus", "grüß", "moin"]
    if any(w in text for w in greetings_en + greetings_de):
        responses_en = ["Hey there! What's up?", "Hello friend! Ready to have fun?", "Hi hi hi! Nice to see you!"]
        responses_de = ["Hey! Was geht?", "Hallo Freund! Bereit für Spaß?", "Hi hi hi! Schön dich zu sehen!"]
        return (random.choice(responses_de) if is_german else random.choice(responses_en)), "excited", "wave", True, False, False

    # ═══════════════════════════════════════════════════════════════
    # DEFAULT - Stay silent for unrecognized input
    # This avoids the robot saying "Interesting!" randomly
    # ═══════════════════════════════════════════════════════════════

    # Log what we didn't understand
    print(f"[UNRECOGNIZED] '{text}' - staying silent", flush=True)

    # Return empty speech - robot won't speak, will continue listening
    return ("", "neutral", "idle", True, False, False)

def get_system_prompt(language):
    """Get system prompt for continuous conversation"""
    lang_note = "Antworte auf Deutsch." if language.startswith("de") else "Respond in English."

    return f"""You are Alpha Mini, a friendly robot assistant. {lang_note}

CRITICAL: Return valid JSON with this structure:
{{
  "speech": "What you say (1-2 sentences MAX)",
  "emotion": "happy|neutral|thinking|excited|surprised|sad|comfort",
  "action": "wave|nod|dance|bow|clap|think|idle",
  "keep_session": true,
  "conversation_end": false,
  "ask_followup": false
}}

SESSION RULES:
- keep_session: true = continue conversation (default)
- conversation_end: true = ONLY if user says goodbye/stop
- ask_followup: true = ONLY occasionally (once per 2-3 turns)

STOP PHRASE DETECTION - Set conversation_end=true when user says:
English: "goodbye", "bye", "that's all", "stop", "i'm done"
German: "tschüss", "auf wiedersehen", "das war's", "fertig", "ende"

Keep responses SHORT (1-2 sentences). Be natural and conversational."""

def build_response_json(speech, emotion, action, keep_session=True,
                        conversation_end=False, ask_followup=False,
                        transcription=None, language="en-US"):
    """Build structured JSON response for continuous conversation"""

    emotion_data = EMOTION_MAP.get(emotion, EMOTION_MAP["neutral"])

    # Use explicit action if provided, otherwise use emotion's default action
    final_action = ACTION_OVERRIDE_MAP.get(action, emotion_data["action"])

    return {
        "response": speech,
        "speech": speech,
        "emotion": emotion_data["chinese"],
        "action": final_action,
        "expression": emotion_data["expression"],
        "light": emotion_data["light"],
        "earcon": "none",
        "allow_barge_in": True,
        "keep_session": keep_session,
        "conversation_end": conversation_end,
        "ask_followup": ask_followup,
        "timing": {
            "pre_roll_ms": 50,
            "post_roll_ms": 100
        },
        "transcription": transcription,
        "language": language,
        "intents": [
            {"type": "emotion", "value": emotion, "timing": "start"},
            {"type": "action", "value": action, "timing": "end"},
            {"type": "light", "value": emotion_data["light"], "timing": "start"}
        ]
    }

# ============== API ENDPOINTS ==============

@app.route('/v1/chat', methods=['POST'])
def chat():
    """Text chat endpoint with session support"""
    try:
        data = request.get_json() or {}
        message = data.get('message', '')
        language_instruction = data.get('language_instruction', '')
        session_id = data.get('session_id', 'default')
        turn_index = data.get('turn_index', 0)
        context_summary = data.get('context_summary', '')

        if not message:
            return jsonify({"error": "No message"}), 400

        language = "de" if "deutsch" in language_instruction.lower() else "en"

        print(f"\n{'='*50}", flush=True)
        print(f"Chat [{language}] Turn {turn_index}: '{message}'", flush=True)
        print(f"Session: {session_id}", flush=True)
        if context_summary:
            print(f"Context: {context_summary}", flush=True)

        # Build session context
        session = get_session(session_id)
        session_context = {
            "summary": context_summary or session.get("summary", ""),
            "recent_turns": [
                {"role": "user" if i % 2 == 0 else "assistant",
                 "content": t["user"] if i % 2 == 0 else t["robot"]}
                for i, t in enumerate(session.get("turns", [])[-2:])
            ]
        }

        # Generate response
        speech, emotion, action, keep_session, conversation_end, ask_followup = generate_response(
            message, language, session_context
        )

        # Update session
        if not conversation_end:
            update_session(session_id, message, speech)

        # Build response
        response = build_response_json(
            speech=speech,
            emotion=emotion,
            action=action,
            keep_session=keep_session,
            conversation_end=conversation_end,
            ask_followup=ask_followup and should_ask_followup(session_id),
            transcription=message,
            language="de-DE" if language == "de" else "en-US"
        )

        if ask_followup and should_ask_followup(session_id):
            mark_followup_asked(session_id)

        print(f"Response: '{speech}' [keep={keep_session}, end={conversation_end}]", flush=True)

        return jsonify(response)

    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route('/v1/audio/chat', methods=['POST'])
def audio_chat():
    """Audio chat endpoint with session support"""
    try:
        data = request.get_json() or {}
        audio_base64 = data.get('audio_base64', '')
        session_id = data.get('session_id', 'default')
        turn_index = data.get('turn_index', 0)
        context_summary = data.get('context_summary', '')

        if not audio_base64:
            return jsonify({"error": "No audio"}), 400

        print(f"\n{'='*50}", flush=True)
        print(f"Audio chat request - Turn {turn_index}", flush=True)
        print(f"Session: {session_id}", flush=True)

        # Detect language
        language_instruction = data.get('language_instruction', '')
        language = "de" if "deutsch" in language_instruction.lower() else "en"

        # Decode audio
        audio_bytes = base64.b64decode(audio_base64)
        print(f"Audio: {len(audio_bytes)} bytes [{language}]", flush=True)

        # Transcribe
        transcription = transcribe_with_vosk(audio_bytes, language)

        if not transcription:
            transcription = ""

        print(f">>> Transcription: '{transcription}'", flush=True)

        # Build session context
        session = get_session(session_id)
        session_context = {
            "summary": context_summary or session.get("summary", ""),
            "recent_turns": []
        }
        for turn in session.get("turns", [])[-2:]:
            session_context["recent_turns"].append({"role": "user", "content": turn["user"]})
            session_context["recent_turns"].append({"role": "assistant", "content": turn["robot"]})

        # Generate response
        speech, emotion, action, keep_session, conversation_end, ask_followup = generate_response(
            transcription, language, session_context
        )

        # Update session
        if not conversation_end and transcription:
            update_session(session_id, transcription, speech)

        # Build response
        response = build_response_json(
            speech=speech,
            emotion=emotion,
            action=action,
            keep_session=keep_session,
            conversation_end=conversation_end,
            ask_followup=ask_followup and should_ask_followup(session_id),
            transcription=transcription,
            language="de-DE" if language == "de" else "en-US"
        )

        if ask_followup and should_ask_followup(session_id):
            mark_followup_asked(session_id)

        print(f"Response: '{speech}' [{emotion}/{action}]", flush=True)
        print(f"Session: keep={keep_session}, end={conversation_end}", flush=True)

        return jsonify(response)

    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        "status": "ok",
        "vosk": vosk_model is not None,
        "openai": OPENAI_AVAILABLE,
        "active_sessions": len(sessions),
        "features": [
            "continuous_conversation",
            "session_management",
            "stop_phrase_detection",
            "context_tracking"
        ]
    })


@app.route('/', methods=['GET'])
def home():
    return f"""
    <h1>Robot LLM Server - Continuous Conversation</h1>
    <p>Speech Recognition: {'Vosk (ready)' if vosk_model else 'Not available'}</p>
    <p>GPT Responses: {'OpenAI (ready)' if OPENAI_AVAILABLE else 'Rule-based'}</p>
    <p>Active Sessions: {len(sessions)}</p>
    <h3>Features:</h3>
    <ul>
        <li>Multi-turn conversation with context</li>
        <li>Session management</li>
        <li>Stop phrase detection</li>
        <li>Structured JSON responses</li>
        <li>Barge-in support</li>
    </ul>
    """


if __name__ == '__main__':
    print("=" * 60)
    print("Robot LLM Server - CONTINUOUS CONVERSATION")
    print("=" * 60)

    # Initialize Vosk
    if not init_vosk():
        print("WARNING: Speech recognition not available")

    print(f"OpenAI GPT: {'ENABLED' if OPENAI_AVAILABLE else 'DISABLED (using rules)'}")
    print()
    print("Features: Multi-turn, Session Management, Stop Detection")
    print("Server: http://0.0.0.0:8080")
    print("=" * 60)

    app.run(host='0.0.0.0', port=8080, debug=False, threaded=True)
