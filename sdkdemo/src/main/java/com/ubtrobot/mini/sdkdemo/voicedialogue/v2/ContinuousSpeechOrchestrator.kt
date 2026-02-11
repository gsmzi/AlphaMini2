package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.ubtrobot.mini.sdkdemo.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.resume

/**
 * â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
 * CONTINUOUS SPEECH ORCHESTRATOR
 * â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
 *
 * Implements FULL CONTINUOUS CONVERSATION with the UBTECH Alpha Mini robot.
 *
 * KEY FEATURES:
 * - Multi-turn dialogue without re-wakeup between turns
 * - Session-based context management (ConversationSession)
 * - Rolling summary memory for LLM context
 * - Barge-in support during robot speech
 * - Multi-level silence handling (short pause, long silence, session end)
 * - AudioRecord/AudioTrack reused across turns (no reinitialization)
 * - Synchronized behavior execution (expression, action, light)
 *
 * STATE MACHINE:
 * IDLE â†’ WAKEUP â†’ LISTENING â†’ CAPTURING â†’ THINKING â†’ SPEAKING
 *      â†’ LISTENING_FOR_FOLLOWUP â†’ (repeat) â†’ CONVERSATION_END â†’ IDLE
 */
class ContinuousSpeechOrchestrator(
    private val context: Context,
    private val config: ContinuousDialogueConfig
) {
    companion object {
        private const val TAG = "ContinuousOrchestrator"
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STATE MANAGEMENT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private val stateMachine = ContinuousStateMachine { oldState, newState ->
        Log.d(TAG, "State: $oldState â†’ $newState")
        listener?.onStateChanged(newState)
        handleStateTransition(oldState, newState)
    }

    val state: StateFlow<ContinuousState> = stateMachine.state

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SESSION MANAGEMENT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private var currentSession: ConversationSession? = null
    private val turnCounter = AtomicInteger(0)

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // COMPONENTS (Reused across turns)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private val wakeupManager = WakeupManager(
        context = context,
        voiceConfig = VoiceWakeupConfig(
            wakeWords = config.wakeWords,
            language = if (config.language == DialogueConfig.Language.DE) "de" else "en",
            sampleRate = config.sampleRate,
            channelConfig = config.channelConfig,
            use4MicArray = config.channelConfig == 60
        ),
        enableVoiceWakeup = config.enableVoiceWakeup,
        enableButtonWakeup = config.enableButtonWakeup
    )

    // Audio components - kept alive across turns
    private var audioRecorder: StreamingAudioRecorder? = null
    private var audioPlayer: StreamingAudioPlayer? = null

    // Remote server clients (used when useLocalProcessing = false)
    private val llmClient = LLMClient(
        DialogueConfig(
            llmServerUrl = config.llmServerUrl,
            ttsServerUrl = config.ttsServerUrl,
            language = config.language
        )
    )

    private val remoteTtsClient = CachedTTSClient(
        TTSClient(
            DialogueConfig(
                llmServerUrl = config.llmServerUrl,
                ttsServerUrl = config.ttsServerUrl,
                language = config.language
            )
        )
    )

    // Local on-device components (used when useLocalProcessing = true)
    private var localRecognizer: LocalSpeechRecognizer? = null
    private var googleStt: GoogleSpeechRecognizer? = null
    private var localResponseGenerator: LocalResponseGenerator? = null
    private var openAiClient: OpenAiClient? = null
    private var embeddedTtsEngine: EmbeddedTtsEngine? = null
    private var pcmPlayer: PcmAudioPlayer? = null
    private val localReady = CompletableDeferred<Boolean>() // true when local models loaded

    // Remote TTS client (used when useLocalProcessing = false)
    // When useLocalProcessing = true, embedded TTS synthesizes PCM and plays via AudioTrack
    private val ttsClient: ITTSClient get() = remoteTtsClient

    private val behaviorMapper = BehaviorMapper()
    private var currentLanguage = config.language
    private var speechFormatter = SpeechFormatter(currentLanguage)
    @Volatile
    private var allowMicCapture = false

    // Edge TTS (online, young male voice)
    private val edgeTts = EdgeTts()
    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    /** Callback invoked when the user switches language via voice command. */
    var onLanguageChanged: ((DialogueConfig.Language) -> Unit)? = null

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // COROUTINE MANAGEMENT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var conversationJob: Job? = null
    private var silenceMonitorJob: Job? = null
    private var sessionTimeoutJob: Job? = null

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CONTROL FLAGS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private val bargeInRequested = AtomicBoolean(false)
    private val sessionActive = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)

    // Track consecutive empty responses to detect when no one is speaking
    private val consecutiveEmptyResponses = AtomicInteger(0)
    private val MAX_CONSECUTIVE_EMPTY = 3 // End session after 3 empty responses in a row

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // METRICS & LISTENER
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private val metrics = LatencyMetrics()
    private var listener: ContinuousOrchestratorListener? = null

    // Last LLM response for session management
    private var lastLLMResponse: LLMResponse? = null

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // INITIALIZATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    fun initialize() {
        Log.d(TAG, "Initializing ContinuousSpeechOrchestrator (local=${config.useLocalProcessing}) gitHash=${BuildConfig.GIT_HASH} STT=GoogleSTT TTS=EdgeTts")

        // Initialize reusable audio components
        initializeAudioComponents()

        // Initialize behavior mapper
        behaviorMapper.initialize(
            expressApi = RobotApiFactory.createExpressApi(),
            actionApi = RobotApiFactory.createActionApi(),
            lightApi = RobotApiFactory.createLightApi()
        )

        // Set up wakeup handling
        scope.launch {
            wakeupManager.wakeupEvents.collect { event ->
                handleWakeup(event)
            }
        }

        // Set up audio player callbacks
        audioPlayer?.setListener(object : PlaybackListener {
            override fun onPlaybackStarted() {
                metrics.tAudioStart = System.currentTimeMillis()
            }

            override fun onPlaybackComplete() {
                metrics.tAudioEnd = System.currentTimeMillis()
                handleSpeakingComplete()
            }

            override fun onPlaybackError(error: String) {
                Log.e(TAG, "Playback error: $error")
                handleError("Playback error: $error")
            }

            override fun onPlaybackProgress(positionMs: Long, durationMs: Long) {
                // Check for barge-in during playback
                if (config.bargeInEnabled && bargeInRequested.get()) {
                    audioPlayer?.stop()
                }
            }
        })

        // Initialize local or remote components
        if (config.useLocalProcessing) {
            initializeLocalComponents()
        } else {
            // Preload TTS cache for remote server
            scope.launch {
                remoteTtsClient.preloadCommonPhrases(
                    if (config.language == DialogueConfig.Language.DE) "de-DE" else "en-US"
                )
            }
        }

        Log.d(TAG, "ContinuousSpeechOrchestrator initialized")
    }

    private fun initializeLocalComponents() {
        localResponseGenerator = LocalResponseGenerator()
        embeddedTtsEngine = EmbeddedTtsEngine(context)
        pcmPlayer = PcmAudioPlayer()

        // Initialize OpenAI LLM fallback (for questions rule-based can't answer)
        if (config.openAiApiKey.isNotBlank()) {
            openAiClient = OpenAiClient(config.openAiApiKey)
            Log.d(TAG, "OpenAI client initialized (GPT fallback enabled)")
        } else {
            Log.d(TAG, "No OpenAI API key — GPT fallback disabled")
        }

        // Try Google STT first (online, better accuracy); fall back to Vosk (offline)
        googleStt = GoogleSpeechRecognizer(context).also { it.initialize() }

        if (googleStt?.isReady == true) {
            Log.d(TAG, "Using Google STT (online)")
        } else {
            Log.w(TAG, "Google STT not available — falling back to Vosk (offline)")
            localRecognizer = LocalSpeechRecognizer(context)
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Load Vosk models if Google STT is not available
                if (localRecognizer != null) {
                    withContext(Dispatchers.Main) {
                        listener?.onStateChanged(ContinuousState.IDLE)
                    }
                    Log.d(TAG, "Loading Vosk speech recognition models...")
                    localRecognizer?.initialize(
                        loadEnglish = true,
                        loadGerman = true
                    ) { progress ->
                        Log.d(TAG, "Vosk: $progress")
                    }
                }

                Log.d(TAG, "Initializing embedded TTS engine (offline fallback)...")
                val ttsOk = embeddedTtsEngine?.initialize(currentLanguage) == true
                if (!ttsOk) {
                    Log.w(TAG, "Embedded TTS not available (EdgeTts is primary)")
                }

                val ready = (googleStt?.isReady == true) || (localRecognizer?.isReady == true)
                val sttEngine = when {
                    googleStt?.isReady == true -> "GoogleSTT"
                    localRecognizer?.isReady == true -> "Vosk"
                    else -> "NONE"
                }
                Log.d(TAG, "Local components ready: stt=$sttEngine, tts=EdgeTts")
                localReady.complete(ready)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize local components", e)
                localReady.complete(false)
            }
        }
    }

    private fun initializeAudioComponents() {
        val dialogueConfig = DialogueConfig(
            sampleRate = config.sampleRate,
            frameSizeMs = config.frameSizeMs,
            language = config.language
        )

        audioRecorder = StreamingAudioRecorder(dialogueConfig)
        audioPlayer = StreamingAudioPlayer(context, dialogueConfig)
    }

    fun setListener(listener: ContinuousOrchestratorListener?) {
        this.listener = listener
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PUBLIC CONTROL METHODS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Start the dialogue system (begin listening for wakeup)
     */
    fun start() {
        if (stateMachine.currentState != ContinuousState.IDLE) {
            Log.w(TAG, "Cannot start: not in IDLE state")
            return
        }

        wakeupManager.start()
        Log.d(TAG, "Dialogue system started - waiting for wakeup")
    }

    /**
     * Stop the dialogue system completely
     */
    fun stop() {
        endSession("User stopped")

        conversationJob?.cancel()
        silenceMonitorJob?.cancel()
        sessionTimeoutJob?.cancel()

        wakeupManager.stop()
        googleStt?.cancel()
        audioRecorder?.stopRecording()
        audioPlayer?.stop()
        stopActiveTts()
        behaviorMapper.stopAllBehaviors()

        stateMachine.forceState(ContinuousState.IDLE, "stop() called")
        Log.d(TAG, "Dialogue system stopped")
    }

    /**
     * Manual wakeup trigger (for testing or alternative input)
     */
    fun triggerManualWakeup() {
        if (stateMachine.currentState == ContinuousState.IDLE) {
            wakeupManager.triggerManualWakeup()
        } else if (stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
            // Resume listening in follow-up mode
            scope.launch {
                startCapturing()
            }
        }
    }

    /**
     * Trigger barge-in (user interruption during robot speech)
     */
    fun triggerBargeIn() {
        if (!config.bargeInEnabled) return

        if (stateMachine.currentState != ContinuousState.SPEAKING) {
            Log.d(TAG, "Barge-in ignored: not speaking")
            return
        }

        Log.d(TAG, "Barge-in triggered!")
        bargeInRequested.set(true)

        // Stop playback immediately (all TTS paths)
        audioPlayer?.stop()
        pcmPlayer?.stop()
        stopActiveTts()
        googleStt?.cancel()
        behaviorMapper.stopAllBehaviors()

        // Transition to listening (within same session)
        stateMachine.transition(ContinuousState.LISTENING, "barge-in")

        // Start new recording
        scope.launch {
            delay(100) // Brief pause
            startCapturing()
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // WAKEUP HANDLING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private fun handleWakeup(event: WakeupEvent) {
        if (!allowMicCapture) {
            Log.w(TAG, "Wakeup received but mic capture is gated; waiting for foreground")
        }
        when (stateMachine.currentState) {
            ContinuousState.IDLE -> {
                Log.d(TAG, "Wakeup detected: ${event.source}")
                startNewSession()
            }
            ContinuousState.LISTENING_FOR_FOLLOWUP -> {
                // Continue existing session
                Log.d(TAG, "Resuming session from wakeup")
                scope.launch { startCapturing() }
            }
            else -> {
                Log.w(TAG, "Ignoring wakeup in state: ${stateMachine.currentState}")
            }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SESSION MANAGEMENT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private fun startNewSession() {
        Log.d(TAG, "Starting new conversation session")

        // Create new session
        currentSession = ConversationSessionManager.getOrCreateSession(config.language)
        turnCounter.set(0)
        consecutiveEmptyResponses.set(0) // Reset empty response counter
        sessionActive.set(true)
        metrics.reset()
        openAiClient?.clearHistory()

        // Reset audio recorder calibration for new session
        audioRecorder?.resetCalibration()

        // Transition to WAKEUP then LISTENING
        stateMachine.transition(ContinuousState.WAKEUP, "wakeup detected")

        // Start session timeout monitor
        startSessionTimeoutMonitor()

        // Begin conversation
        conversationJob = scope.launch {
            try {
                // Brief wakeup acknowledgment
                playWakeupCue()

                stateMachine.transition(ContinuousState.LISTENING, "wakeup complete")

                // Start first turn
                startCapturing()
            } catch (e: CancellationException) {
                Log.d(TAG, "Session cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Session error", e)
                handleError(e.message ?: "Unknown error")
            }
        }
    }

    private fun endSession(reason: String) {
        if (!sessionActive.getAndSet(false)) return

        Log.d(TAG, "Ending session: $reason")

        silenceMonitorJob?.cancel()
        sessionTimeoutJob?.cancel()

        // Say goodbye if appropriate
        if (stateMachine.currentState != ContinuousState.ERROR) {
            scope.launch {
                sayGoodbye()
            }
        }

        // End session in manager
        currentSession?.endSession(reason)
        ConversationSessionManager.endCurrentSession(reason)
        currentSession = null

        listener?.onSessionEnded(reason, turnCounter.get())
    }

    private fun startSessionTimeoutMonitor() {
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = scope.launch {
            delay(config.maxSessionDurationMs)
            if (sessionActive.get()) {
                Log.d(TAG, "Session timeout reached")
                endSession("Max session duration reached")
            }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TURN EXECUTION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private suspend fun startCapturing() {
        if (!allowMicCapture) {
            Log.w(TAG, "Mic capture deferred: app not in foreground")
            return
        }
        if (!sessionActive.get()) {
            Log.w(TAG, "Cannot capture: session not active")
            return
        }

        bargeInRequested.set(false)
        isRecording.set(true)

        stateMachine.transition(ContinuousState.CAPTURING, "start capturing")

        try {
            if (config.useLocalProcessing && googleStt?.isReady == true) {
                // Google STT path: recognizer manages its own mic
                captureWithGoogleStt()
            } else if (config.useLocalProcessing) {
                // Vosk fallback path: record audio, transcribe locally
                val audioData = recordUserSpeech()
                if (audioData == null || audioData.isEmpty()) {
                    handleNoSpeech()
                    return
                }
                captureWithVoskFallback(audioData)
            } else {
                // Remote path: record audio, send to server
                val audioData = recordUserSpeech()
                if (audioData == null || audioData.isEmpty()) {
                    handleNoSpeech()
                    return
                }
                processAudioRemotely(audioData)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Capturing cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Capturing error", e)
            handleError(e.message ?: "Recording failed")
        } finally {
            isRecording.set(false)
        }
    }

    /**
     * Capture speech using Google's online SpeechRecognizer.
     * Pauses voice wakeup to release mic, then resumes after.
     */
    private suspend fun captureWithGoogleStt() {
        val languageCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"

        // Pause voice wakeup to free the mic for Google STT
        wakeupManager.pauseVoiceWakeup()

        val transcription = try {
            googleStt?.listen(languageCode)
        } catch (e: Exception) {
            Log.e(TAG, "Google STT error", e)
            null
        } finally {
            // Resume voice wakeup after STT is done
            wakeupManager.resumeVoiceWakeup()
        }

        Log.d(TAG, "Google STT transcription: '$transcription'")
        transcription?.let { listener?.onTranscription(it) }

        if (transcription.isNullOrBlank()) {
            handleNoSpeech()
            return
        }

        // Process the transcribed text locally
        processTranscriptionLocally(transcription)
    }

    /**
     * Vosk fallback: transcribe recorded audio locally, then process the text.
     */
    private suspend fun captureWithVoskFallback(audioData: ByteArray) {
        stateMachine.transition(ContinuousState.THINKING, "processing locally (Vosk)")
        metrics.tRequestSent = System.currentTimeMillis()

        behaviorMapper.setStateExpression(DialogueState.THINKING)
        behaviorMapper.setStateLight(DialogueState.THINKING)

        // Wait for Vosk models to finish loading
        val ready = localReady.await()
        if (!ready) {
            Log.e(TAG, "Local components failed to initialize")
            listener?.onError("Speech recognition models failed to load")
            handleProcessingError()
            return
        }

        val languageCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"

        val transcription = try {
            localRecognizer?.transcribe(audioData, languageCode)
        } catch (e: Exception) {
            Log.e(TAG, "Vosk transcription error", e)
            null
        }

        metrics.tFirstToken = System.currentTimeMillis()
        Log.d(TAG, "Vosk transcription: '$transcription'")
        transcription?.let { listener?.onTranscription(it) }

        if (transcription.isNullOrBlank()) {
            // Vosk returned empty — use fun fallback instead of silently looping
            handleNoSpeech()
            return
        }

        // Got valid text — process through response generator (with OpenAI fallback)
        val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
        val generator = localResponseGenerator ?: run {
            handleProcessingError()
            return
        }

        val localResponse = generator.generateResponse(transcription, languageCode)
        val finalResponse = enhanceWithOpenAi(localResponse, transcription, languageCode, generator)
        val response = generator.toLLMResponse(finalResponse, transcription, ttsCode)

        Log.d(TAG, "Vosk transcription: '$transcription' → response: '${response.speech.take(60)}'")
        handleProcessedResponse(response)
    }

    /**
     * Process already-transcribed text through the local response generator.
     * If rule-based returns empty, tries OpenAI GPT as fallback.
     */
    private suspend fun processTranscriptionLocally(transcription: String) {
        stateMachine.transition(ContinuousState.THINKING, "processing locally")
        metrics.tRequestSent = System.currentTimeMillis()

        behaviorMapper.setStateExpression(DialogueState.THINKING)
        behaviorMapper.setStateLight(DialogueState.THINKING)

        val languageCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"
        val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"

        val generator = localResponseGenerator
        if (generator == null) {
            handleProcessingError()
            return
        }

        val localResponse = generator.generateResponse(transcription, languageCode)
        val finalResponse = enhanceWithOpenAi(localResponse, transcription, languageCode, generator)
        val response = generator.toLLMResponse(finalResponse, transcription, ttsCode)

        metrics.tFirstToken = System.currentTimeMillis()
        Log.d(TAG, "Local transcription: '$transcription' → response: '${response.speech.take(60)}'")

        handleProcessedResponse(response)
    }

    /**
     * If rule-based returned empty speech, try OpenAI GPT for a smart answer.
     * Falls back to fun "I don't know" response if OpenAI also fails.
     */
    private suspend fun enhanceWithOpenAi(
        localResponse: LocalResponseGenerator.LocalResponse,
        transcription: String,
        languageCode: String,
        generator: LocalResponseGenerator
    ): LocalResponseGenerator.LocalResponse {
        // Rule-based had an answer — use it
        if (localResponse.speech.isNotBlank()) return localResponse

        // Try OpenAI GPT
        val ai = openAiClient
        if (ai != null) {
            Log.d(TAG, "Rule-based empty — trying OpenAI for: '$transcription'")
            val aiText = try {
                ai.chat(transcription, languageCode)
            } catch (e: Exception) {
                Log.e(TAG, "OpenAI request failed", e)
                null
            }

            if (!aiText.isNullOrBlank()) {
                Log.d(TAG, "OpenAI response: '${aiText.take(80)}'")
                return LocalResponseGenerator.LocalResponse(
                    speech = aiText,
                    emotion = "happy",
                    action = "nod"
                )
            }
            Log.w(TAG, "OpenAI returned null/blank — falling back to fun response")
        }

        // Ultimate fallback: fun "I don't know" response
        return generator.generateUnrecognizedResponse(languageCode)
    }

    private suspend fun recordUserSpeech(): ByteArray? {
        val recorder = audioRecorder ?: return null

        recorder.clearAccumulatedAudio()
        metrics.tWakeup = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                recorder.recordWithVAD().collect { result ->
                    when (result) {
                        is VADResult.SpeechStarted -> {
                            metrics.tSpeechStart = System.currentTimeMillis()
                            Log.d(TAG, "Speech started")
                        }
                        is VADResult.SpeechEnded -> {
                            metrics.tSpeechEnd = System.currentTimeMillis()
                            Log.d(TAG, "Speech ended: ${result.audioData.size} bytes")
                            return@collect
                        }
                        is VADResult.Timeout -> {
                            Log.d(TAG, "Recording timeout")
                            return@collect
                        }
                        else -> {}
                    }
                }

                recorder.getAccumulatedAudio()
            } catch (e: CancellationException) {
                null
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
                null
            }
        }
    }

    private suspend fun processAudioRemotely(audioData: ByteArray) {
        stateMachine.transition(ContinuousState.THINKING, "processing")
        metrics.tRequestSent = System.currentTimeMillis()

        behaviorMapper.setStateExpression(DialogueState.THINKING)
        behaviorMapper.setStateLight(DialogueState.THINKING)

        val llmContext = currentSession?.buildLLMContext()
        val languageCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"

        val response = try {
            (llmClient as LLMClient).sendAudioWithContext(audioData, languageCode, llmContext)
        } catch (e: Exception) {
            Log.e(TAG, "LLM error", e)
            null
        }

        metrics.tFirstToken = System.currentTimeMillis()

        if (response == null) {
            handleProcessingError()
            return
        }

        handleProcessedResponse(response)
    }

    private suspend fun handleProcessedResponse(response: LLMResponse) {
        lastLLMResponse = response

        // Notify transcription
        response.transcription?.let { transcription ->
            listener?.onTranscription(transcription)

            // Check for stop phrases
            if (isStopPhrase(transcription)) {
                Log.d(TAG, "Stop phrase detected")
                endSession("User said stop phrase")
                return
            }

            // Check for language switch command
            if (handleLanguageSwitch(transcription)) {
                return
            }
        }

        // Update session with this turn
        val responseTextForSession = if (response.speech.isNotBlank()) response.speech else "[audio]"
        currentSession?.addTurn(
            userInput = response.transcription ?: "",
            robotResponse = responseTextForSession,
            emotion = response.emotion.name,
            action = response.action
        )

        turnCounter.incrementAndGet()
        stateMachine.setTurnIndex(turnCounter.get())

        // Check turn limit
        if (turnCounter.get() >= config.maxTurnsPerSession) {
            Log.d(TAG, "Max turns reached")
            speakAndEnd(response)
            return
        }

        val hasAudio = response.audioData != null && response.audioData.isNotEmpty()

        // Skip speaking if response is empty (no valid transcription) and no audio
        if (response.speech.isBlank() && !hasAudio) {
            val emptyCount = consecutiveEmptyResponses.incrementAndGet()
            Log.d(TAG, "Empty response ($emptyCount/$MAX_CONSECUTIVE_EMPTY)")

            // Check if we've had too many empty responses (no one is speaking)
            if (emptyCount >= MAX_CONSECUTIVE_EMPTY) {
                Log.d(TAG, "Too many empty responses - ending session")
                consecutiveEmptyResponses.set(0)
                endSession("No speech detected")
                return
            }

            // In local mode: speak a fun fallback + do a movement instead of silently looping
            if (config.useLocalProcessing) {
                val generator = localResponseGenerator
                if (generator != null) {
                    val languageCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"
                    val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
                    val fallback = generator.generateFallbackResponse(languageCode)
                    val fallbackResponse = generator.toLLMResponse(fallback, response.transcription, ttsCode)
                    Log.d(TAG, "Fallback response: '${fallback.speech.take(60)}'")
                    speakResponse(fallbackResponse)
                    return
                }
            }

            // Remote mode: silently loop back to listening
            behaviorMapper.setStateExpression(DialogueState.LISTENING)
            behaviorMapper.setStateLight(DialogueState.LISTENING)
            stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "empty response")
            startSilenceMonitor()
            scope.launch {
                delay(1500)
                if (stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
                    startCapturing()
                }
            }
            return
        }

        // Valid response received - reset empty counter
        consecutiveEmptyResponses.set(0)

        // Speak response (text or direct audio)
        if (hasAudio) {
            playAudioResponse(response)
        } else {
            speakResponse(response)
        }
    }

    private suspend fun playAudioResponse(response: LLMResponse) {
        val audioData = response.audioData ?: return

        // Stop any existing playback before starting new audio
        audioPlayer?.stop()
        pcmPlayer?.stop()
        stopActiveTts()

        stateMachine.transition(ContinuousState.SPEAKING, "playing audio")

        // Update UI with response text if present
        val displayText = if (response.speech.isNotBlank()) response.speech else "[audio]"
        listener?.onResponse(displayText)

        // Create behavior plan
        val behaviorPlan = behaviorMapper.createBehaviorPlan(response)

        // Estimate audio duration
        val durationMs = if (AudioFormatDetector.isMP3(audioData)) {
            (audioData.size.toLong() * 10 * 1000) / (config.sampleRate * 2)
        } else {
            audioPlayer?.estimateDurationMs(audioData.size) ?: 1000L
        }

        metrics.tTtsStart = System.currentTimeMillis()

        // Execute behavior with synchronized playback
        withContext(Dispatchers.Main) {
            behaviorMapper.executeBehaviorPlan(behaviorPlan, durationMs) {
                scope.launch {
                    playAudioData(audioData)
                }
            }
        }

        // Wait for playback to complete
        delay(durationMs + config.behaviorPostRollMs)

        // FALLBACK: Ensure we transition to listening even if callback doesn't fire
        if (stateMachine.currentState == ContinuousState.SPEAKING && sessionActive.get()) {
            Log.d(TAG, "Fallback: forcing transition to listening after audio delay")
            handleSpeakingComplete()
        }
    }

    private suspend fun speakResponse(response: LLMResponse) {
        // CRITICAL: Stop any existing playback before starting new speech
        audioPlayer?.stop()
        pcmPlayer?.stop()
        stopActiveTts()

        stateMachine.transition(ContinuousState.SPEAKING, "speaking")

        // Format speech with natural pauses
        val formattedSpeech = speechFormatter.formatWithNaturalPauses(response.speech)
        listener?.onResponse(formattedSpeech)

        // Create behavior plan
        val behaviorPlan = behaviorMapper.createBehaviorPlan(response)

        if (config.useLocalProcessing) {
            // LOCAL PATH: EdgeTTS (online, natural voice) + synchronized movement
            metrics.tTtsStart = System.currentTimeMillis()

            // Synthesize MP3 first so we can play action + speech concurrently
            val langCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"
            val mp3 = try {
                edgeTts.synthesize(formattedSpeech, langCode)
            } catch (e: Exception) {
                Log.e(TAG, "EdgeTTS synthesis failed", e)
                null
            }
            if (mp3 == null || mp3.isEmpty()) {
                Log.e(TAG, "EdgeTTS returned null/empty for speakResponse")
                handleTTSError()
                return
            }

            // Launch behavior (expression, action, light) concurrently with speech
            // Action runs in background via scope.launch inside executeBehaviorPlan
            val estimatedDurationMs = (mp3.size.toLong() * 8 * 1000) / 48000
            scope.launch(Dispatchers.Main) {
                behaviorMapper.executeBehaviorPlan(behaviorPlan, estimatedDurationMs) {}
            }

            // Play MP3 (blocks until complete) — action runs concurrently
            val ok = playMp3AndWait(mp3)

            // DON'T stop actions here — let them finish naturally (dance, wave, etc.)
            // Actions only get stopped on barge-in or session stop

            if (!ok) {
                Log.e(TAG, "MP3 playback failed for speakResponse")
                handleTTSError()
                return
            }

            metrics.tAudioEnd = System.currentTimeMillis()

            // Transition to listening
            if (stateMachine.currentState == ContinuousState.SPEAKING && sessionActive.get()) {
                handleSpeakingComplete()
            }
        } else {
            // REMOTE PATH: Synthesize to bytes â†’ play via audio player
            val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
            val ttsData = ttsClient.synthesize(formattedSpeech, ttsCode)

            if (ttsData == null) {
                Log.e(TAG, "TTS synthesis failed")
                handleTTSError()
                return
            }

            metrics.tTtsStart = System.currentTimeMillis()

            // Estimate audio duration based on format
            val durationMs = if (AudioFormatDetector.isMP3(ttsData)) {
                (ttsData.size.toLong() * 10 * 1000) / (config.sampleRate * 2)
            } else {
                audioPlayer?.estimateDurationMs(ttsData.size) ?: 1000L
            }

            // Execute behavior with synchronized playback
            withContext(Dispatchers.Main) {
                behaviorMapper.executeBehaviorPlan(behaviorPlan, durationMs) {
                    scope.launch {
                        if (AudioFormatDetector.isMP3(ttsData)) {
                            audioPlayer?.playMP3(ttsData)
                        } else {
                            audioPlayer?.playPCM(ttsData)
                        }
                    }
                }
            }

            // Wait for playback to complete
            delay(durationMs + config.behaviorPostRollMs)

            // FALLBACK: Ensure we transition to listening even if callback doesn't fire
            if (stateMachine.currentState == ContinuousState.SPEAKING && sessionActive.get()) {
                Log.d(TAG, "Fallback: forcing transition to listening after speech delay")
                handleSpeakingComplete()
            }
        }
    }

    private suspend fun speakAndEnd(response: LLMResponse) {
        speakResponse(response)
        endSession("Conversation complete")
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SPEAKING COMPLETE & FOLLOW-UP HANDLING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private fun handleSpeakingComplete() {
        // Guard: Only process if we're actually in SPEAKING state
        // This prevents duplicate calls (from callback + fallback)
        if (stateMachine.currentState != ContinuousState.SPEAKING) {
            Log.d(TAG, "handleSpeakingComplete skipped - not in SPEAKING state (current: ${stateMachine.currentState})")
            return
        }

        if (!sessionActive.get()) {
            Log.d(TAG, "handleSpeakingComplete skipped - session not active")
            return
        }

        val response = lastLLMResponse

        // Check if conversation should end
        if (response?.shouldEndConversation() == true) {
            scope.launch {
                endSession("LLM ended conversation")
            }
            return
        }

        // Report metrics
        metrics.log(TAG)
        listener?.onMetrics(metrics.copy())

        // Transition to ListeningForFollowUp
        Log.d(TAG, "Speaking complete - transitioning to LISTENING_FOR_FOLLOWUP")
        stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "speaking complete")

        // Start silence monitoring
        startSilenceMonitor()

        // Touch session to update activity time
        currentSession?.touch()

        // Auto-start listening for next turn
        // Keep delay short - actions run asynchronously and won't be interrupted
        scope.launch {
            delay(500L) // Brief natural pause
            if (stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
                Log.d(TAG, "Starting capture for next turn")
                startCapturing()
            }
        }
    }

    private fun startSilenceMonitor() {
        silenceMonitorJob?.cancel()
        silenceMonitorJob = scope.launch {
            val startTime = System.currentTimeMillis()

            while (isActive && stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
                val silenceDuration = System.currentTimeMillis() - startTime

                when {
                    // Very long silence - end session
                    silenceDuration >= config.silenceVeryLongMs -> {
                        Log.d(TAG, "Very long silence - ending session")
                        endSession("Silence timeout")
                        break
                    }

                    // Long silence - prompt user
                    silenceDuration >= config.silenceLongMs -> {
                        Log.d(TAG, "Long silence - prompting user")
                        promptUser()
                        break
                    }
                }

                delay(500) // Check every 500ms
            }
        }
    }

    private suspend fun promptUser() {
        if (!sessionActive.get()) return

        val session = currentSession ?: return

        // Check if we should ask a follow-up (limit frequency)
        if (!session.shouldAskFollowUp()) {
            Log.d(TAG, "Skipping follow-up (asked recently)")
            return
        }

        stateMachine.transition(ContinuousState.PROMPTING, "silence prompt")

        // Choose a random prompt in the current language
        val prompt = config.getSilencePrompts(currentLanguage).random()
        listener?.onResponse(prompt)

        if (config.useLocalProcessing) {
            speakLocalText(prompt)
        } else {
            val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
            val ttsData = ttsClient.synthesize(prompt, ttsCode)
            if (ttsData != null) {
                playAudioData(ttsData)
            }
        }
        session.markFollowUpAsked()

        // Return to listening for follow-up
        delay(1500)
        stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "prompt complete")

        // Restart silence monitor
        startSilenceMonitor()
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // AUDIO CUES & FEEDBACK
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private suspend fun playWakeupCue() {
        behaviorMapper.setStateExpression(DialogueState.LISTENING)
        behaviorMapper.setStateLight(DialogueState.LISTENING)
        delay(50) // Minimal pause for expression to start
    }

    private suspend fun sayGoodbye() {
        val phrase = config.getSessionEndPhrases(currentLanguage).random()
        listener?.onResponse(phrase)

        // Set happy expression for goodbye
        behaviorMapper.setStateExpression(DialogueState.SPEAKING)

        if (config.useLocalProcessing) {
            speakLocalText(phrase)
        } else {
            val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
            val ttsData = ttsClient.synthesize(phrase, ttsCode)
            if (ttsData != null) {
                playAudioData(ttsData)
                delay(2000)
            }
        }

        // Return to neutral
        behaviorMapper.setStateExpression(DialogueState.IDLE)
        behaviorMapper.setStateLight(DialogueState.IDLE)

        stateMachine.forceState(ContinuousState.IDLE, "session ended")
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ERROR HANDLING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private suspend fun handleNoSpeech() {
        val emptyCount = consecutiveEmptyResponses.incrementAndGet()
        Log.d(TAG, "No speech detected (turn ${turnCounter.get()}, empty $emptyCount/$MAX_CONSECUTIVE_EMPTY)")

        // Check if we've had too many no-speech events
        if (emptyCount >= MAX_CONSECUTIVE_EMPTY) {
            Log.d(TAG, "Too many no-speech events - ending session")
            consecutiveEmptyResponses.set(0)
            endSession("No speech detected")
            return
        }

        // In local mode: speak a fun fallback + do a movement
        if (config.useLocalProcessing && sessionActive.get()) {
            val generator = localResponseGenerator
            if (generator != null) {
                val languageCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"
                val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
                val fallback = generator.generateFallbackResponse(languageCode)
                val response = generator.toLLMResponse(fallback, null, ttsCode)
                Log.d(TAG, "No-speech fallback: '${fallback.speech.take(60)}'")

                // Transition to THINKING so speakResponse() can go to SPEAKING
                stateMachine.transition(ContinuousState.THINKING, "no-speech fallback")
                speakResponse(response)
                return
            }
        }

        // Remote mode: stay silent and continue listening
        if (sessionActive.get()) {
            behaviorMapper.setStateExpression(DialogueState.LISTENING)
            behaviorMapper.setStateLight(DialogueState.LISTENING)

            stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "no speech - continue silently")
            startSilenceMonitor()
            scope.launch {
                delay(1500)
                if (stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
                    startCapturing()
                }
            }
        }
    }

    private suspend fun handleProcessingError() {
        val emptyCount = consecutiveEmptyResponses.incrementAndGet()
        Log.d(TAG, "Processing error (empty $emptyCount/$MAX_CONSECUTIVE_EMPTY) - resetting to listening state")

        // Reset expression immediately (exit thinking state visually)
        behaviorMapper.setStateExpression(DialogueState.LISTENING)
        behaviorMapper.setStateLight(DialogueState.LISTENING)

        // Check if we've had too many errors
        if (emptyCount >= MAX_CONSECUTIVE_EMPTY) {
            Log.d(TAG, "Too many processing errors - ending session")
            consecutiveEmptyResponses.set(0)
            endSession("Processing errors")
            return
        }

        // Try to continue session
        if (sessionActive.get()) {
            stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "error - retry")
            startSilenceMonitor()
            // Launch capturing in separate coroutine to avoid blocking
            scope.launch {
                delay(1500) // Wait 1.5 seconds before retry
                if (stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
                    startCapturing()
                }
            }
        }
    }

    private suspend fun handleTTSError() {
        listener?.onError("TTS synthesis failed")

        // Try to continue without speaking
        if (sessionActive.get()) {
            stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "tts error - continue")
            startSilenceMonitor()
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, "Error: $message")
        listener?.onError(message)

        stateMachine.forceState(ContinuousState.ERROR, message)

        scope.launch {
            delay(1000)
            if (sessionActive.get()) {
                stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "error recovery")
            } else {
                stateMachine.forceState(ContinuousState.IDLE, "error recovery - idle")
                wakeupManager.start()
            }
        }
    }

    private suspend fun speakError(message: String) {
        listener?.onError(message)

        if (config.useLocalProcessing) {
            speakLocalText(message)
        } else {
            val ttsCode = if (currentLanguage == DialogueConfig.Language.DE) "de-DE" else "en-US"
            val ttsData = ttsClient.synthesize(message, ttsCode)
            if (ttsData != null) {
                playAudioData(ttsData)
                delay(1500)
            }
        }
    }

    /**
     * Play audio data in the correct format (MP3, WAV, or raw PCM).
     */
    private suspend fun playAudioData(data: ByteArray) {
        when {
            AudioFormatDetector.isMP3(data) -> audioPlayer?.playMP3(data)
            AudioFormatDetector.isWAV(data) -> audioPlayer?.playMP3(data, useVoicePool = false)
            else -> audioPlayer?.playPCM(data)
        }
    }

    /**
     * Play MP3 bytes via MediaPlayer and suspend until complete. Returns true on success.
     */
    private suspend fun playMp3AndWait(mp3Bytes: ByteArray): Boolean {
        return suspendCancellableCoroutine { cont ->
            try {
                val tmpFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                tmpFile.writeBytes(mp3Bytes)

                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tmpFile.absolutePath)
                    setOnCompletionListener {
                        Log.d(TAG, "MediaPlayer playback completed")
                        activeMediaPlayer = null
                        tmpFile.delete()
                        it.release()
                        if (cont.isActive) cont.resume(true)
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                        activeMediaPlayer = null
                        tmpFile.delete()
                        mp.release()
                        if (cont.isActive) cont.resume(false)
                        true
                    }
                    prepare()
                }
                activeMediaPlayer = mp
                mp.start()
                Log.d(TAG, "MediaPlayer started (${mp3Bytes.size} bytes, ${mp.duration}ms)")

                cont.invokeOnCancellation {
                    Log.d(TAG, "MediaPlayer cancelled")
                    activeMediaPlayer = null
                    try { mp.stop(); mp.release() } catch (_: Exception) {}
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer setup failed", e)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    /**
     * Speak text using Google Translate TTS (online, natural voice).
     */
    private suspend fun speakLocalText(text: String): Boolean {
        if (text.isBlank()) return true

        val langCode = if (currentLanguage == DialogueConfig.Language.DE) "de" else "en"
        Log.d(TAG, "Google TTS speaking: '${text.take(60)}...' [$langCode]")

        val mp3 = try {
            edgeTts.synthesize(text, langCode)
        } catch (e: Exception) {
            Log.e(TAG, "Google TTS synthesis failed", e)
            null
        }
        if (mp3 == null || mp3.isEmpty()) {
            Log.e(TAG, "Google TTS returned null/empty for: '${text.take(60)}'")
            return false
        }

        return playMp3AndWait(mp3)
    }

    private fun estimatePcmDurationMs(byteCount: Int, sampleRate: Int = 16000): Long {
        if (byteCount <= 0) return 0L
        val bytesPerSecond = sampleRate * 2 // 16-bit mono
        return (byteCount.toLong() * 1000L) / bytesPerSecond
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // STOP ACTIVE TTS

    /**
     * Stop active Google TTS MediaPlayer playback (for barge-in / stop / cancel).
     */
    private fun stopActiveTts() {
        activeMediaPlayer?.let { mp ->
            try {
                mp.stop()
                mp.release()
            } catch (_: Exception) {}
            activeMediaPlayer = null
        }
    }

    // HELPERS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private fun isStopPhrase(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return config.getStopPhrases(currentLanguage).any { phrase ->
            val p = phrase.lowercase()
            lowerText == p || lowerText.startsWith("$p ") ||
                lowerText.endsWith(" $p") || " $p " in lowerText
        }
    }

    /**
     * Detect "switch" / "wechseln" as exact words (word-boundary safe).
     * Toggles language, persists, notifies UI, speaks confirmation, resumes listening.
     */
    private suspend fun handleLanguageSwitch(transcription: String): Boolean {
        val words = transcription.lowercase().trim().split("\\s+".toRegex())
        val hasTrigger = words.any { it == "switch" || it == "wechseln" }
        if (!hasTrigger) return false

        Log.d(TAG, "Language switch command detected in: '$transcription'")

        // Stop any ongoing TTS before switching
        pcmPlayer?.stop()
        stopActiveTts()

        // Toggle language
        currentLanguage = if (currentLanguage == DialogueConfig.Language.EN) {
            DialogueConfig.Language.DE
        } else {
            DialogueConfig.Language.EN
        }

        // Update language-dependent components
        speechFormatter = SpeechFormatter(currentLanguage)
        LanguagePrefs.set(context, currentLanguage)

        val confirmation = if (currentLanguage == DialogueConfig.Language.DE) {
            "Wechsel zu Deutsch."
        } else {
            "Switched to English."
        }

        Log.d(TAG, "Language switched to ${currentLanguage.name}")

        // Notify UI
        listener?.onResponse(confirmation)
        onLanguageChanged?.invoke(currentLanguage)

        // Speak confirmation
        stateMachine.transition(ContinuousState.SPEAKING, "language switch")
        speakLocalText(confirmation)

        // Resume listening for follow-up
        if (sessionActive.get()) {
            stateMachine.transition(ContinuousState.LISTENING_FOR_FOLLOWUP, "language switch done")
            startSilenceMonitor()
            scope.launch {
                delay(500L)
                if (stateMachine.currentState == ContinuousState.LISTENING_FOR_FOLLOWUP) {
                    startCapturing()
                }
            }
        }

        return true
    }

    /**
     * Switch language programmatically (from UI spinner). Fast - no model reload needed.
     */
    fun switchLanguage(newLanguage: DialogueConfig.Language) {
        if (newLanguage == currentLanguage) return
        Log.d(TAG, "switchLanguage: ${currentLanguage.name} -> ${newLanguage.name}")

        currentLanguage = newLanguage
        speechFormatter = SpeechFormatter(currentLanguage)
        LanguagePrefs.set(context, currentLanguage)

        val confirmation = if (currentLanguage == DialogueConfig.Language.DE) {
            "Wechsel zu Deutsch."
        } else {
            "Switched to English."
        }
        listener?.onResponse(confirmation)

        scope.launch {
            speakLocalText(confirmation)
        }
    }

    fun setMicCaptureAllowed(allowed: Boolean) {
        allowMicCapture = allowed
        Log.d(TAG, "Mic capture allowed = $allowed")
    }

    /**
     * Speak the boot greeting once. Call after start().
     * Uses Google Translate TTS (online).
     */
    fun speakBootGreeting() {
        val greeting = if (currentLanguage == DialogueConfig.Language.DE) {
            "System bereit. Sag Wechseln für Englisch."
        } else {
            "System ready. Say switch for German."
        }
        Log.d(TAG, "Boot greeting (${currentLanguage.name}): '$greeting'")
        listener?.onResponse(greeting)

        scope.launch {
            val ok = speakLocalText(greeting)
            if (ok) {
                Log.d(TAG, "Boot greeting spoken via Google TTS")
            } else {
                Log.e(TAG, "Boot greeting: Google TTS failed")
            }
        }
    }

    /**
     * Speak arbitrary text for debug/testing. Uses Google Translate TTS.
     */
    fun speakTest(text: String) {
        Log.d(TAG, "speakTest: '$text'")
        scope.launch {
            try {
                val ok = speakLocalText(text)
                Log.d(TAG, "speakTest: ${if (ok) "success" else "failed"}")
            } catch (e: Exception) {
                Log.e(TAG, "speakTest: exception", e)
            }
        }
    }

    private fun handleStateTransition(oldState: ContinuousState, newState: ContinuousState) {
        // Update behavior based on state (wrapped in coroutine since behavior methods are suspend)
        scope.launch {
            when (newState) {
                ContinuousState.LISTENING, ContinuousState.LISTENING_FOR_FOLLOWUP -> {
                    behaviorMapper.setStateExpression(DialogueState.LISTENING)
                    behaviorMapper.setStateLight(DialogueState.LISTENING)
                }
                ContinuousState.CAPTURING -> {
                    behaviorMapper.setStateExpression(DialogueState.CAPTURING)
                }
                ContinuousState.THINKING -> {
                    behaviorMapper.setStateExpression(DialogueState.THINKING)
                    behaviorMapper.setStateLight(DialogueState.THINKING)
                }
                ContinuousState.IDLE -> {
                    behaviorMapper.setStateExpression(DialogueState.IDLE)
                    behaviorMapper.setStateLight(DialogueState.IDLE)
                }
                else -> {}
            }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // LIFECYCLE
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    fun release() {
        stop()
        stopActiveTts()
        scope.cancel()

        audioRecorder?.release()
        audioRecorder = null

        audioPlayer?.release()
        audioPlayer = null

        // Release local components
        googleStt?.release()
        googleStt = null
        localRecognizer?.release()
        localRecognizer = null
        embeddedTtsEngine?.release()
        embeddedTtsEngine = null
        pcmPlayer?.release()
        pcmPlayer = null
        localResponseGenerator = null
        openAiClient = null

        behaviorMapper.release()
        wakeupManager.release()

        Log.d(TAG, "ContinuousSpeechOrchestrator released")
    }
}

/**
 * Listener for ContinuousSpeechOrchestrator events
 */
interface ContinuousOrchestratorListener {
    fun onStateChanged(state: ContinuousState)
    fun onTranscription(text: String)
    fun onResponse(text: String)
    fun onError(message: String)
    fun onMetrics(metrics: LatencyMetrics)
    fun onSessionEnded(reason: String, totalTurns: Int)
}


