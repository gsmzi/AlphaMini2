"""
Edge TTS Server for Robot
--------------------------
A simple Flask server that provides free Text-to-Speech using Microsoft Edge TTS.
No API key required!

Usage:
1. Install requirements: pip install flask edge-tts
2. Run server: python server.py
3. The robot app will connect to http://YOUR_PC_IP:5000/tts

Supports multiple languages including English (en-US) and German (de-DE).
"""

from flask import Flask, request, send_file, jsonify
import edge_tts
import asyncio
import tempfile
import os

app = Flask(__name__)

# Default voices for different languages
VOICES = {
    "en-US": "en-US-GuyNeural",      # American English male
    "en-GB": "en-GB-RyanNeural",      # British English male
    "de-DE": "de-DE-ConradNeural",    # German male
    "de-AT": "de-AT-JonasNeural",     # Austrian German male
    "fr-FR": "fr-FR-HenriNeural",     # French male
    "es-ES": "es-ES-AlvaroNeural",    # Spanish male
    "it-IT": "it-IT-DiegoNeural",     # Italian male
    "ja-JP": "ja-JP-KeitaNeural",     # Japanese male
    "zh-CN": "zh-CN-YunxiNeural",     # Chinese male
}

# Female voice alternatives
VOICES_FEMALE = {
    "en-US": "en-US-JennyNeural",
    "en-GB": "en-GB-SoniaNeural",
    "de-DE": "de-DE-KatjaNeural",
    "fr-FR": "fr-FR-DeniseNeural",
    "es-ES": "es-ES-ElviraNeural",
}


async def generate_tts(text: str, voice: str, output_path: str):
    """Generate TTS audio using edge-tts"""
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(output_path)


@app.route('/tts', methods=['POST', 'GET'])
def text_to_speech():
    """
    Convert text to speech

    Parameters (POST JSON or GET query):
        - text: The text to convert to speech
        - language: Language code (default: en-US)
        - voice: Specific voice name (optional)
        - gender: "male" or "female" (default: male)

    Returns:
        MP3 audio file
    """
    # Get parameters from POST JSON or GET query
    if request.method == 'POST':
        data = request.get_json() or {}
        text = data.get('text', '')
        language = data.get('language', 'en-US')
        voice = data.get('voice', None)
        gender = data.get('gender', 'male')
    else:
        text = request.args.get('text', '')
        language = request.args.get('language', 'en-US')
        voice = request.args.get('voice', None)
        gender = request.args.get('gender', 'male')

    if not text:
        return jsonify({"error": "No text provided"}), 400

    # Select voice
    if not voice:
        if gender.lower() == 'female' and language in VOICES_FEMALE:
            voice = VOICES_FEMALE[language]
        else:
            voice = VOICES.get(language, VOICES["en-US"])

    print(f"TTS Request: '{text[:50]}...' | Language: {language} | Voice: {voice}")

    try:
        # Create temp file for audio
        with tempfile.NamedTemporaryFile(suffix='.mp3', delete=False) as tmp:
            tmp_path = tmp.name

        # Generate TTS
        asyncio.run(generate_tts(text, voice, tmp_path))

        # Send file and delete after
        response = send_file(
            tmp_path,
            mimetype='audio/mpeg',
            as_attachment=True,
            download_name='tts_audio.mp3'
        )

        # Clean up temp file after sending
        @response.call_on_close
        def cleanup():
            try:
                os.unlink(tmp_path)
            except:
                pass

        return response

    except Exception as e:
        print(f"TTS Error: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/voices', methods=['GET'])
def list_voices():
    """List available voices"""
    return jsonify({
        "male": VOICES,
        "female": VOICES_FEMALE
    })


@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({"status": "ok", "service": "Edge TTS Server"})


@app.route('/', methods=['GET'])
def home():
    """Home page with usage instructions"""
    return """
    <h1>Edge TTS Server</h1>
    <p>Free Text-to-Speech using Microsoft Edge TTS</p>

    <h2>Endpoints:</h2>
    <ul>
        <li><b>POST /tts</b> - Convert text to speech
            <br>Body: {"text": "Hello", "language": "en-US"}
        </li>
        <li><b>GET /tts?text=Hello&language=en-US</b> - Convert text to speech</li>
        <li><b>GET /voices</b> - List available voices</li>
        <li><b>GET /health</b> - Health check</li>
    </ul>

    <h2>Supported Languages:</h2>
    <ul>
        <li>en-US - American English</li>
        <li>en-GB - British English</li>
        <li>de-DE - German</li>
        <li>fr-FR - French</li>
        <li>es-ES - Spanish</li>
        <li>it-IT - Italian</li>
        <li>ja-JP - Japanese</li>
        <li>zh-CN - Chinese</li>
    </ul>
    """


if __name__ == '__main__':
    print("=" * 50)
    print("Edge TTS Server")
    print("=" * 50)
    print("Free Text-to-Speech - No API key required!")
    print()
    print("Starting server on http://0.0.0.0:5000")
    print("Robot should connect to: http://YOUR_PC_IP:5000/tts")
    print()
    print("Test URL: http://localhost:5000/tts?text=Hello&language=en-US")
    print("=" * 50)

    app.run(host='0.0.0.0', port=5000, debug=False)
