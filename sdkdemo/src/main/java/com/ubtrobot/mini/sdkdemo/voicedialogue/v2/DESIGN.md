# Voice Dialogue System v2 - Design Document

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     SpeechOrchestrator                          │
│  (Coordinates all components, manages state machine)            │
└────────────────────────┬────────────────────────────────────────┘
                         │
    ┌────────────────────┼────────────────────┐
    │                    │                    │
    ▼                    ▼                    ▼
┌────────┐        ┌───────────┐        ┌───────────┐
│Wakeup  │        │ Recording │        │ Playback  │
│Manager │        │ Pipeline  │        │ Pipeline  │
└────────┘        └───────────┘        └───────────┘
    │                    │                    │
    │              ┌─────┴─────┐        ┌─────┴─────┐
    │              │           │        │           │
    ▼              ▼           ▼        ▼           ▼
┌────────┐   ┌─────────┐ ┌─────────┐ ┌────────┐ ┌────────┐
│Button  │   │Streaming│ │   VAD   │ │TTS     │ │Audio   │
│Events  │   │Recorder │ │Processor│ │Client  │ │Player  │
│(SysAPI)│   │(Flow)   │ │         │ │        │ │        │
└────────┘   └─────────┘ └─────────┘ └────────┘ └────────┘
                 │                        │           │
                 ▼                        ▼           ▼
            ┌─────────┐            ┌───────────┐ ┌────────┐
            │   LLM   │            │  Speech   │ │Behavior│
            │  Client │            │ Formatter │ │ Mapper │
            └─────────┘            └───────────┘ └────────┘
```

## Design Decisions for Low Latency

### 1. Streaming Architecture
- **20ms audio frames**: Small chunks minimize buffering latency
- **Flow-based recording**: Non-blocking, cancellable, backpressure-aware
- **Bounded channels**: Prevent memory issues while maintaining responsiveness

### 2. VAD-First Endpoint Detection
- **Energy-based VAD**: Fast, low CPU overhead
- **800ms silence timeout**: Quick cutoff after speech ends
- **300ms hangover**: Prevents premature cuts on natural pauses
- **Noise floor calibration**: Adapts to ambient conditions

### 3. Parallel Processing
- **Pre-roll behaviors**: Start expression/light BEFORE audio plays
- **Async TTS preloading**: Cache common phrases on init
- **Filler responses**: Show activity if LLM takes >800ms

### 4. Immediate Response
- **AudioTrack for PCM**: Lowest latency playback
- **VoicePool fallback**: Robot speaker for MP3
- **Barge-in support**: Stop playback instantly on user speech

## State Machine

```
        ┌────────────────────────────────────────────────────────────┐
        │                                                            │
        ▼                                                            │
    ┌───────┐  Wakeup   ┌───────────┐  Cue Done  ┌───────────┐      │
    │ IDLE  │──────────►│ LISTENING │───────────►│ CAPTURING │      │
    └───────┘           └───────────┘            └───────────┘      │
        ▲                     │                        │             │
        │                     │ Cancel                 │ Speech End  │
        │                     ▼                        ▼             │
        │               ┌───────────┐            ┌───────────┐      │
        │               │   ERROR   │◄───────────│ THINKING  │      │
        │               └───────────┘  Fail      └───────────┘      │
        │                     │                        │             │
        │                     │ Reset                  │ Response    │
        │                     ▼                        ▼             │
        │               ┌─────────────────────────────────┐         │
        └───────────────│           SPEAKING              │─────────┘
             Complete   └─────────────────────────────────┘
                              │ Barge-In
                              ▼
                        ┌───────────┐
                        │ LISTENING │ (restart)
                        └───────────┘
```

## Sequence Diagram (Happy Path)

```
User          WakeupMgr    Orchestrator   Recorder    LLMClient    TTSClient    Player    BehaviorMapper
  │               │              │            │            │            │          │            │
  │──Long Press──►│              │            │            │            │          │            │
  │               │──Wakeup─────►│            │            │            │          │            │
  │               │              │──Start────►│            │            │          │            │
  │               │              │            │            │            │          │──Expression─►
  │               │              │            │            │            │          │──Light──────►
  │──Speech──────────────────────────────────►│            │            │          │            │
  │               │              │            │──Frames───►│            │          │            │
  │               │              │            │    (VAD)   │            │          │            │
  │──Silence─────────────────────────────────►│            │            │          │            │
  │               │              │◄──Audio────│            │            │          │            │
  │               │              │──────────────Send──────►│            │          │            │
  │               │              │◄─────────────Response───│            │          │            │
  │               │              │──────────────────────────Synthesize─►│          │            │
  │               │              │◄─────────────────────────Audio───────│          │            │
  │               │              │─────────────────────────────────────────────────►│──Pre-roll─►
  │               │              │                                      │──Play───►│            │
  │◄──────────────────────────────────────────────────────────────────────Audio────│            │
  │               │              │◄────────────────────────────────────────Complete│──Post-roll►
  │               │              │                                                 │            │
```

## Latency Budget (Target: <600ms)

| Phase | Target | Implementation |
|-------|--------|----------------|
| Wakeup → Recording | <50ms | Immediate start, no delays |
| VAD Endpoint | 800ms | Silence timeout |
| Audio Upload | <100ms | Compressed, chunked |
| LLM Processing | <300ms | Local server, simple rules |
| TTS Synthesis | <200ms | Edge TTS, caching |
| Audio Start | <50ms | Pre-roll overlap |
| **Total** | **<600ms** | After speech ends |

## Metrics Tracking

```kotlin
data class LatencyMetrics(
    var tWakeup: Long,        // Wakeup timestamp
    var tSpeechStart: Long,   // User speech detected
    var tSpeechEnd: Long,     // Endpoint detected (key metric)
    var tRequestSent: Long,   // LLM request sent
    var tFirstToken: Long,    // First LLM response
    var tTtsStart: Long,      // TTS generation started
    var tAudioStart: Long,    // Audio playback started (key metric)
    var tAudioEnd: Long       // Playback complete
)

// Key latency: tAudioStart - tSpeechEnd (target: <600ms)
```

## Test Checklist

### Latency Tests
- [ ] Endpoint → Audio latency < 600ms (stopwatch test)
- [ ] LLM response time < 500ms (log timestamps)
- [ ] TTS synthesis time < 300ms (log timestamps)
- [ ] No perceivable delay between wakeup and listening cue

### Audio Quality Tests
- [ ] Clear recording in quiet environment
- [ ] Recording works with background noise
- [ ] No clipping on loud speech (check logs)
- [ ] VAD correctly detects speech start/end
- [ ] Silence timeout works (800ms + 300ms hangover)

### Barge-In Tests
- [ ] Speaking stops immediately on user speech
- [ ] Expression/light reset on barge-in
- [ ] New recording starts after barge-in
- [ ] No audio artifacts on stop

### Behavior Sync Tests
- [ ] Expression starts before audio (pre-roll)
- [ ] Expression continues after audio (post-roll)
- [ ] Light matches emotion
- [ ] Action triggers at appropriate time

### Error Handling Tests
- [ ] Graceful handling of no audio captured
- [ ] LLM timeout shows filler message
- [ ] TTS failure speaks fallback
- [ ] Network error recovery

### Language Tests
- [ ] English greetings work
- [ ] German greetings work (VAD works for German)
- [ ] Correct TTS voice for each language
- [ ] Number/time formatting correct

## Files Structure

```
voicedialogue/v2/
├── DialogueConfig.kt       # Configuration and constants
├── DialogueStateMachine.kt # State management
├── WakeupManager.kt        # Button + voice wakeup
├── StreamingAudioRecorder.kt # AudioRecord + VAD
├── LLMClient.kt            # LLM communication + JSON parsing
├── TTSClient.kt            # TTS synthesis
├── SpeechFormatter.kt      # Text post-processing
├── StreamingAudioPlayer.kt # AudioTrack + MediaPlayer
├── BehaviorMapper.kt       # Expression/action/light mapping
├── SpeechOrchestrator.kt   # Main coordinator
└── DESIGN.md               # This document
```

## Integration Example

```kotlin
class VoiceDialogueActivity : AppCompatActivity() {
    private lateinit var orchestrator: SpeechOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = DialogueConfig.forEnglish()
        orchestrator = SpeechOrchestrator(this, config)

        orchestrator.setListener(object : OrchestratorListener {
            override fun onStateChanged(state: DialogueState) {
                updateUI(state)
            }
            override fun onTranscription(text: String) {
                showUserSpeech(text)
            }
            override fun onResponse(text: String) {
                showRobotResponse(text)
            }
            override fun onError(message: String) {
                showError(message)
            }
            override fun onMetrics(metrics: LatencyMetrics) {
                Log.d("Metrics", "Latency: ${metrics.endpointToAudioLatency()}ms")
            }
        })

        orchestrator.initialize()
        orchestrator.start()
    }

    override fun onDestroy() {
        orchestrator.release()
        super.onDestroy()
    }
}
```
