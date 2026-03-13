# Alpha Mini 2 - Voice Dialogue App

An Android application for the **UBTECH Alpha Mini 2** robot that enables fully on-device,
multi-turn voice conversation in English and German. The app uses offline speech recognition
(Vosk) and free online text-to-speech (Google Translate TTS), with no API keys required
for the default mode.

---

## Table of Contents

1. [Features](#features)
2. [Architecture Overview](#architecture-overview)
3. [Prerequisites](#prerequisites)
4. [Project Structure](#project-structure)
5. [Building with Android Studio](#building-with-android-studio)
6. [Deploying to the Robot](#deploying-to-the-robot)
7. [Running the App](#running-the-app)
8. [Configuration](#configuration)
9. [Optional: Python Servers (Remote Mode)](#optional-python-servers-remote-mode)
10. [Voice Commands](#voice-commands)
11. [Debugging and Logs](#debugging-and-logs)
12. [Troubleshooting](#troubleshooting)

---

## Features

- **Fully on-device STT** - Vosk offline models for English and German (no cloud, no API key)
- **Natural TTS** - Google Translate TTS endpoint, returns MP3, plays via MediaPlayer
- **Multi-turn continuous dialogue** - full conversational loop with session context and rolling memory
- **Bilingual** - English / German, switchable by voice (`switch` / `wechseln`) or UI spinner
- **Wake-up triggers** - keyword detection via Vosk + physical chest button press
- **Barge-in** - interrupt the robot while it is speaking
- **Robot behaviour sync** - facial expressions, motion actions, and LED lights triggered by conversation context
- **Boot auto-start** - the app launches automatically when the robot powers on
- **Three dialogue versions** in one APK:
  - **V3 (Continuous)** - recommended; full multi-turn, fully on-device
  - **V2 (Low Latency)** - single-turn, low latency, requires Python servers
  - **V1 (Original)** - legacy reference implementation

---

## Architecture Overview

```
+---------------------------------------------------------+
|                 Alpha Mini 2 (Android)                  |
|                                                         |
|  +-----------------------------------------------------+|
|  |          VoiceDialogueActivityV3 (UI)               ||
|  |  +------------------------------------------------+ ||
|  |  |       ContinuousSpeechOrchestrator             | ||
|  |  |                                                | ||
|  |  |  State machine:                                | ||
|  |  |  IDLE -> WAKEUP -> LISTENING -> CAPTURING      | ||
|  |  |       -> THINKING -> SPEAKING                  | ||
|  |  |       -> LISTENING_FOR_FOLLOWUP -> (loop)      | ||
|  |  |                                                | ||
|  |  |  +----------------+  +---------------------+  | ||
|  |  |  | LocalSpeech    |  | GoogleTranslateTts   |  | ||
|  |  |  | Recognizer     |  | (HTTP->MP3->Player)  |  | ||
|  |  |  | (Vosk EN+DE)   |  +---------------------+  | ||
|  |  |  +----------------+                           | ||
|  |  |  +--------------------+  +-----------------+  | ||
|  |  |  | LocalResponseGen   |  | BehaviorMapper  |  | ||
|  |  |  | (rule-based)       |  | Express/Action/ |  | ||
|  |  |  +--------------------+  | LED via SDK     |  | ||
|  |  |                          +-----------------+  | ||
|  |  +------------------------------------------------+ ||
|  +-----------------------------------------------------+|
|                                                         |
|  +------------------+  +-----------------------------+  |
|  | BootReceiver     |  |   VoiceDialogueService      |  |
|  | (auto-start on   |->|   (ForegroundService        |  |
|  |  boot)           |  |    watchdog)                |  |
|  +------------------+  +-----------------------------+  |
+---------------------------------------------------------+
```

**Default mode (`useLocalProcessing = true`):** everything runs on the robot - no PC required.
The robot only needs Wi-Fi for TTS audio synthesis (Google Translate HTTP call).

**Remote mode (`useLocalProcessing = false`):** the robot connects to Python servers running
on your PC via ADB reverse port forwarding.

---

## Prerequisites

### Development Machine

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Hedgehog (2023.1.1) or newer | Includes bundled JDK (JBR) |
| Android SDK | API 34 | Install via SDK Manager |
| Android NDK | **26.3.11579264** | Must match exactly - install via SDK Manager |
| CMake | **3.22.1** | Install via SDK Manager -> SDK Tools |
| ADB | Latest | Bundled with Android SDK platform-tools |
| Python | 3.9+ | Only needed for optional remote server mode |

> **JDK Note:** Android Studio ships with its own JDK (JBR). Gradle uses it automatically.
> Do **not** set `JAVA_HOME` to a different JDK version; this causes build failures.

### Hardware

- UBTECH Alpha Mini 2 robot
- USB-A to USB cable (for ADB deployment)
- Wi-Fi access point (robot needs Wi-Fi for Google Translate TTS)

---

## Project Structure

```
mini_libs_robot_third_sdk/
|-- build.gradle                  # Root Gradle config (AGP 8.5.1, Kotlin 2.1.0)
|-- settings.gradle               # Module declaration: sdkdemo
|-- gradle.properties             # JVM args, AndroidX flags
|-- gradlew / gradlew.bat         # Gradle wrapper scripts
|
|-- sdkdemo/                      # Main Android application module
|   |-- build.gradle              # App config (minSdk 30, targetSdk 34, NDK 26.3)
|   |-- wukong_sdk.keystore       # Debug + release signing keystore
|   |-- libs/                     # Pre-built AAR/JAR libraries
|   |   |-- opensdk-v1.0.6.aar       # UBTECH Alpha Mini 2 robot SDK
|   |   |-- vosk-android-0.3.47.aar  # Vosk offline STT engine
|   |   +-- jna-5.13.0.aar           # JNA native dispatch (required by Vosk)
|   +-- src/main/
|       |-- AndroidManifest.xml
|       |-- assets/
|       |   |-- model-en-us/     # Vosk English STT model (~50 MB)
|       |   |-- model-de/        # Vosk German STT model (~30 MB)
|       |   +-- espeak-ng-data/  # eSpeak-ng voice data (offline TTS fallback)
|       |-- cpp/
|       |   |-- CMakeLists.txt   # CMake build for eSpeak JNI bridge
|       |   +-- espeak_jni.cpp
|       |-- jni/
|       |   +-- Android.mk       # NDK build rules
|       |-- jniLibs/arm64-v8a/
|       |   +-- libttsespeak.so  # Pre-built eSpeak native library (arm64)
|       +-- java/com/ubtrobot/mini/sdkdemo/
|           |-- MainActivity.kt                    # App entry point / SDK demo menu
|           |-- BootReceiver.kt                    # BOOT_COMPLETED handler
|           |-- DemoApplication.kt
|           +-- voicedialogue/
|               |-- VoiceDialogueActivityV3.kt     # Main UI (V3 - recommended)
|               |-- VoiceDialogueActivityV2.kt     # V2 low-latency UI
|               |-- VoiceDialogueActivity.kt       # V1 original UI
|               |-- VoiceDialogueService.kt        # Foreground watchdog service
|               +-- v2/
|                   |-- ContinuousSpeechOrchestrator.kt  # Core state machine
|                   |-- ContinuousDialogueConfig.kt      # All tunable parameters
|                   |-- LocalSpeechRecognizer.kt         # Vosk STT (EN + DE)
|                   |-- GoogleTranslateTts.kt            # Free HTTP TTS -> MP3
|                   |-- LocalResponseGenerator.kt        # Rule-based responses
|                   |-- LanguagePrefs.kt                 # Persistent language setting
|                   |-- BehaviorMapper.kt                # Expressions / actions / LEDs
|                   |-- WakeupManager.kt                 # Wake-word + button wakeup
|                   +-- PcmAudioPlayer.kt                # AudioTrack playback
|
|-- llm_server/                   # Optional Python LLM server (remote mode)
|   |-- server.py                 # Flask: /v1/chat, /v1/audio/chat, /health
|   +-- requirements.txt          # flask, openai, requests
|
|-- tts_server/                   # Optional Python TTS server (remote mode)
|   |-- server.py                 # Flask: Edge TTS endpoint
|   +-- requirements.txt          # flask, edge-tts
|
|-- build_app.bat                 # Quick build (assembleDebug)
|-- build_and_install.bat         # Build + ADB install
|-- launch_voice_dialogue.bat     # One-click: servers + port-forward + launch app
+-- stop_servers.bat              # Kill Python server processes
```

---

## Building with Android Studio

### Step 1 - Open the project

1. Launch Android Studio.
2. Choose **File -> Open**.
3. Select the `mini_libs_robot_third_sdk/` root folder.
4. Android Studio detects the Gradle project and syncs automatically. Wait for it to finish.

### Step 2 - Install required SDK components

Go to **Tools -> SDK Manager**.

**SDK Platforms tab:**
- Install **Android 14 (API 34)**.

**SDK Tools tab** - enable "Show Package Details" first:
- **NDK (Side by side):** install version **26.3.11579264**
- **CMake:** install version **3.22.1**

Click **Apply** and wait for all downloads to complete.

### Step 3 - Verify `local.properties`

Android Studio creates `local.properties` in the project root automatically. It should contain:

```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

If the file is missing, create it manually. **Never commit this file** - it is listed in `.gitignore`.

To enable OpenAI-powered responses (remote mode only), add your API key here:

```properties
openai.api.key=sk-...your-key-here...
```

### Step 4 - Build the APK

**From Android Studio:**
- **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**
- Or press **Ctrl+F9** to make the module

**From the command line (Windows):**

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat :sdkdemo:assembleDebug
```

Or use the provided batch file:

```bat
build_app.bat
```

Output APK location:

```
sdkdemo/build/outputs/apk/debug/sdkdemo-debug.apk
```

---

## Deploying to the Robot

### Step 1 - Connect the robot via USB

Connect the Alpha Mini 2 to your PC with a USB cable.

```bat
adb devices
```

Expected output:

```
List of devices attached
XXXXXXXXXXXXXXXX    device
```

If the status shows `unauthorized`, accept the USB debugging prompt on the robot's screen.

### Step 2 - Install the APK

**Option A - Android Studio (easiest):**
Select the robot from the device dropdown and press **Shift+F10**.
Android Studio builds, installs, and launches in one step.

**Option B - Command line:**

```bat
adb install -r sdkdemo\build\outputs\apk\debug\sdkdemo-debug.apk
```

**Option C - Batch file:**

```bat
build_and_install.bat
```

### Step 3 - Grant audio permission

On first launch, accept the `RECORD_AUDIO` permission dialog on the robot's screen.
Or pre-grant via ADB:

```bat
adb shell pm grant com.ubtrobot.mini.sdkdemo android.permission.RECORD_AUDIO
```

---

## Running the App

### Launch via ADB

```bat
adb shell am start -n com.ubtrobot.mini.sdkdemo/.MainActivity
```

### Navigate to Voice Dialogue V3

From the main SDK demo menu, tap the **purple "Voice Dialogue V3 (Continuous)"** button.
This is the recommended version with full on-device processing.

### First launch - model initialisation

On the very first run, Vosk unpacks its language models (~80 MB total) from the APK assets
to device storage. This takes approximately **10-20 seconds**. A progress message is shown
in the UI. Subsequent launches are fast (under 2 seconds).

### Starting a conversation

1. The state indicator turns **green** and the robot announces it is ready.
   Default greeting (German): *"System bereit. Sag Wechseln fur Englisch."*
2. **Say a wake word** ("Wukong", "Hello Wukong") **or press the robot's chest button**.
3. Speak naturally. The robot responds and the conversation continues without repeating the wake word.
4. Say **"Goodbye"** (English) or **"Tschuss"** (German) to end the session.

### Switching language

- **By voice:** say `switch` (to switch to German) or `wechseln` (to switch to English)
- **By UI:** use the language spinner at the top of the screen

The selected language is persisted in SharedPreferences and restored on next boot.

### Boot auto-start

The app starts automatically on every boot:

1. `BootReceiver` receives `BOOT_COMPLETED` and launches `VoiceDialogueActivityV3` directly.
2. `VoiceDialogueService` runs as a foreground service and relaunches the activity if it ever stops.
3. The robot speaks the boot greeting and waits for a wake word.

---

## Configuration

All tunable parameters are in `ContinuousDialogueConfig.kt`:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `useLocalProcessing` | `true` | `true` = on-device; `false` = Python servers |
| `language` | `DE` (from SharedPreferences) | Starting language |
| `enableVoiceWakeup` | `true` | Vosk wake-word detection |
| `enableButtonWakeup` | `true` | Chest button wakeup |
| `wakeWords` | `["hello wukong", "hi wukong", "wukong"]` | Recognised wake phrases |
| `silenceEndOfUtteranceMs` | `900` | Silence (ms) before STT processing |
| `silenceLongMs` | `5000` | Silence before a gentle follow-up prompt |
| `silenceVeryLongMs` | `10000` | Silence before ending the session |
| `maxTurnsPerSession` | `20` | Max turns per conversation session |
| `bargeInEnabled` | `true` | Allow interrupting robot speech |
| `llmServerUrl` | `http://127.0.0.1:8080` | Python LLM server URL (remote mode only) |
| `ttsServerUrl` | `http://127.0.0.1:5000` | Python TTS server URL (remote mode only) |

---

## Optional: Python Servers (Remote Mode)

V2 and the remote mode of V3 (`useLocalProcessing = false`) require two Python Flask servers
running on your PC.

### Install dependencies

```bat
cd llm_server
pip install -r requirements.txt

cd ..\tts_server
pip install -r requirements.txt
```

Vosk language models (~40 MB each) are downloaded automatically by `llm_server/server.py` on first run.

### Start the servers

**TTS server (port 5000):**

```bat
cd tts_server
python -u server.py
```

**LLM server (port 8080):**

```bat
cd llm_server
python -u server.py
```

Or use the one-click launcher (also sets up port forwarding and launches the app):

```bat
launch_voice_dialogue.bat
```

### ADB reverse port forwarding

With the robot connected via USB:

```bat
adb reverse tcp:5000 tcp:5000
adb reverse tcp:8080 tcp:8080
```

Verify:

```bat
adb reverse --list
```

### Health check

```bat
curl http://127.0.0.1:5000/health
curl http://127.0.0.1:8080/health
```

Both should return `{"status": "ok"}`.

### Stop servers

```bat
stop_servers.bat
```

---

## Voice Commands

### English

| Phrase | Action |
|--------|--------|
| `dance` | Performs a dance |
| `wave` | Waves |
| `hands up` / `raise hands` | Raises both arms |
| `clap` | Claps hands |
| `bow` | Bows |
| `what time is it` | Announces the current time |
| `what is the weather` | Reports weather via wttr.in |
| `what is your name` | Introduces itself |
| `tell me a joke` | Tells a joke |
| `switch` | Switches to German |
| `goodbye` / `bye` / `stop` | Ends the conversation |

### German

| Phrase | Action |
|--------|--------|
| `tanz` / `tanzen` | Performs a dance |
| `winke` | Waves |
| `hande hoch` / `arme hoch` | Raises both arms |
| `klatsch` | Claps hands |
| `verbeugen` | Bows |
| `wie spat ist es` | Announces the current time |
| `wie ist das Wetter` | Reports weather via wttr.in |
| `wie heisst du` | Introduces itself |
| `erzahl einen Witz` | Tells a joke |
| `wechseln` | Switches to English |
| `tschuss` / `auf Wiedersehen` | Ends the conversation |

---

## Debugging and Logs

### Logcat filter

```bat
adb logcat -s ContinuousOrchestrator:D EmbeddedTtsEngine:D VoiceDialogueV3:D VoiceDialogueService:D LanguagePrefs:D LocalSpeechRecognizer:D GoogleTts:D
```

### Test TTS without the UI

```bat
adb shell am broadcast -a com.ubtrobot.mini.sdkdemo.SPEAK_TEST --es text "Hello, I am Alpha Mini."
```

### Check installed APK version

```bat
adb shell dumpsys package com.ubtrobot.mini.sdkdemo | grep versionCode
```

### Verify process is running

```bat
adb shell ps | grep sdkdemo
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| App crashes on launch | Missing RECORD_AUDIO permission | `adb shell pm grant com.ubtrobot.mini.sdkdemo android.permission.RECORD_AUDIO` |
| STT never fires | Vosk model still loading | Wait ~20 s on first launch; look for "READY" in logcat |
| Robot is silent | No Wi-Fi for Google TTS | Connect robot to Wi-Fi |
| TTS connection refused (remote mode) | ADB port forwarding not active | `adb reverse tcp:5000 tcp:5000` |
| LLM server not responding | Python server not started | `python -u server.py` in `llm_server/` |
| NDK build error | Wrong NDK version | Install NDK **26.3.11579264** in SDK Manager |
| CMake error | Wrong or missing CMake | Install CMake **3.22.1** via SDK Manager -> SDK Tools |
| `local.properties` missing | Fresh clone | Let Android Studio create it, or add `sdk.dir=...` manually |
| Language reverts after reboot | SharedPreferences cleared | Check logcat for LanguagePrefs tag |
| Chest button has no effect | SysEventApi not initialised | Check logcat for WakeupManager errors |
| Old code running on robot | Stale APK cached | Reinstall: `adb install -r sdkdemo-debug.apk` |

---

## SDK API Reference

The app uses the UBTECH Alpha Mini 2 Open SDK (`opensdk-v1.0.6.aar`). Correct API signatures:

```kotlin
// Facial expression
ExpressApi.get().doExpress(name, loopCount, tweenable, ResourcePolicy, listener)

// Motion action
ActionApi.get().playAction(actionId, ResourcePolicy, ResponseListener)
ActionApi.get().stopAction()

// LED - solid colour
LightApi.getInstance().normalEffect(ledList, ColorUtil.rgbColor(r, g, b), delay, effect)

// LED - breathing effect
LightApi.getInstance().breathEffect(ledList, ColorUtil.rgbColor(r, g, b), period, duration, effect)

// Physical button events
// Package: com.ubtrobot.mini.sysevent
// Classes: SysEventApi, ChestEvent, KeyEventReceiver
```

Working usage examples: `ExpressActivity.kt`, `ActionActivity.kt`, `MainActivity.kt`.

---

## License

See [LICENSE](LICENSE).
