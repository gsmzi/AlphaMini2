package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dialogue turn result
 */
data class DialogueTurnResult(
    val userTranscription: String?,
    val robotResponse: String?,
    val emotion: LLMResponse.Emotion,
    val wasBargedIn: Boolean,
    val latencyMetrics: LatencyMetrics,
    val error: String? = null
)

/**
 * Orchestrator listener for UI updates
 */
interface OrchestratorListener {
    fun onStateChanged(state: DialogueState)
    fun onTranscription(text: String)
    fun onResponse(text: String)
    fun onError(message: String)
    fun onMetrics(metrics: LatencyMetrics)
}

/**
 * Speech Orchestrator
 *
 * Coordinates the entire voice dialogue pipeline:
 * 1. Wakeup detection
 * 2. Audio recording with VAD
 * 3. LLM processing
 * 4. TTS synthesis
 * 5. Audio playback with behavior synchronization
 * 6. Barge-in handling
 *
 * Implements the state machine: Idle → Listening → Capturing → Thinking → Speaking → Idle
 */
class SpeechOrchestrator(
    private val context: Context,
    private val config: DialogueConfig
) {
    companion object {
        private const val TAG = "SpeechOrchestrator"
    }

    // State machine
    private val stateMachine = DialogueStateMachine()
    val state: StateFlow<DialogueState> = stateMachine.state

    // Components
    private val wakeupManager = WakeupManager(
        context = context,
        voiceConfig = VoiceWakeupConfig(
            language = if (config.language == DialogueConfig.Language.DE) "de" else "en",
            sampleRate = config.sampleRate,
            channelConfig = config.channelConfig,
            use4MicArray = config.use4MicArray,
            micArrayChannelConfig = config.micArrayChannelConfig
        ),
        enableVoiceWakeup = false, // Keep disabled in single-turn orchestrator by default
        enableButtonWakeup = true
    )
    private val audioRecorder = StreamingAudioRecorder(config)
    private val audioPlayer = StreamingAudioPlayer(context, config)
    private val llmClient = LLMClient(config)
    private val ttsClient = CachedTTSClient(TTSClient(config))
    private val behaviorMapper = BehaviorMapper()
    private val speechFormatter = SpeechFormatter(config.language)

    // Coroutine management
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dialogueJob: Job? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    // Barge-in control
    private val bargeInRequested = AtomicBoolean(false)

    // Audio buffer
    private val audioBuffer = ByteArrayOutputStream()

    // Listener
    private var listener: OrchestratorListener? = null

    // Continuous conversation mode
    private var continuousMode = false

    /**
     * Initialize the orchestrator
     */
    fun initialize() {
        Log.d(TAG, "Initializing SpeechOrchestrator")

        // Initialize behavior mapper with robot APIs
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

        // Set up audio player listener
        audioPlayer.setListener(object : PlaybackListener {
            override fun onPlaybackStarted() {
                stateMachine.processEvent(DialogueEvent.SpeakingStarted)
            }

            override fun onPlaybackComplete() {
                stateMachine.processEvent(DialogueEvent.SpeakingComplete)
                handleSpeakingComplete()
            }

            override fun onPlaybackError(error: String) {
                Log.e(TAG, "Playback error: $error")
                stateMachine.processEvent(DialogueEvent.Error(error))
            }

            override fun onPlaybackProgress(positionMs: Long, durationMs: Long) {
                // Could update UI with progress
            }
        })

        // Observe state changes
        scope.launch {
            stateMachine.state.collect { state ->
                listener?.onStateChanged(state)
                behaviorMapper.setStateExpression(state)
                behaviorMapper.setStateLight(state)
            }
        }

        // Preload TTS cache
        scope.launch {
            ttsClient.preloadCommonPhrases(config.language.ttsCode)
        }

        Log.d(TAG, "SpeechOrchestrator initialized")
    }

    /**
     * Set orchestrator listener
     */
    fun setListener(listener: OrchestratorListener?) {
        this.listener = listener
    }

    /**
     * Start the dialogue system (begin listening for wakeup)
     */
    fun start() {
        if (!stateMachine.isIdle()) {
            Log.w(TAG, "Cannot start: not in IDLE state")
            return
        }

        wakeupManager.start()
        Log.d(TAG, "Dialogue system started - waiting for wakeup")
    }

    /**
     * Stop the dialogue system
     */
    fun stop() {
        dialogueJob?.cancel()
        recordingJob?.cancel()
        playbackJob?.cancel()

        wakeupManager.stop()
        audioRecorder.stopRecording()
        audioPlayer.stop()
        behaviorMapper.stopAllBehaviors()

        stateMachine.forceReset()
        Log.d(TAG, "Dialogue system stopped")
    }

    /**
     * Enable/disable continuous conversation mode
     */
    fun setContinuousMode(enabled: Boolean) {
        continuousMode = enabled
    }

    /**
     * Trigger barge-in (user interruption)
     */
    fun triggerBargeIn() {
        if (!config.bargeInEnabled) return
        if (!stateMachine.isInterruptible()) return

        Log.d(TAG, "Barge-in triggered")
        bargeInRequested.set(true)

        // Stop playback immediately
        audioPlayer.stop()
        behaviorMapper.stopAllBehaviors()

        // Transition to listening
        stateMachine.processEvent(DialogueEvent.BargeIn)

        // Start new recording
        scope.launch {
            delay(100) // Brief pause
            startRecording()
        }
    }

    /**
     * Manual wakeup trigger (for testing or alternative input)
     */
    fun triggerManualWakeup() {
        wakeupManager.triggerManualWakeup()
    }

    /**
     * Handle wakeup event
     */
    private fun handleWakeup(event: WakeupEvent) {
        if (!stateMachine.isIdle()) {
            Log.w(TAG, "Ignoring wakeup: not in IDLE state")
            return
        }

        Log.d(TAG, "Wakeup detected: ${event.source}")
        stateMachine.processEvent(DialogueEvent.WakeupDetected(event.source))

        // Start dialogue turn
        dialogueJob = scope.launch {
            try {
                executeDialogueTurn()
            } catch (e: CancellationException) {
                Log.d(TAG, "Dialogue turn cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Dialogue turn error", e)
                handleError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Execute a complete dialogue turn
     */
    private suspend fun executeDialogueTurn() {
        bargeInRequested.set(false)

        // Play listening cue (async)
        scope.launch {
            playListeningCue()
        }

        // Brief delay for listening cue then start recording
        delay(config.earconDurationMs)
        stateMachine.processEvent(DialogueEvent.ListeningCueComplete)

        // Record user speech
        val audioData = recordUserSpeech()

        if (audioData == null || audioData.isEmpty()) {
            handleNoAudioCaptured()
            return
        }

        // Process with LLM
        stateMachine.processEvent(DialogueEvent.ProcessingStarted)

        // Show filler if taking too long
        val fillerJob = scope.launch {
            delay(config.fillerThresholdMs)
            if (stateMachine.state.value == DialogueState.THINKING) {
                playFillerResponse()
            }
        }

        val response = processWithLLM(audioData)
        fillerJob.cancel()

        if (response == null) {
            handleProcessingError()
            return
        }

        // Notify transcription
        response.transcription?.let {
            listener?.onTranscription(it)
        }

        stateMachine.processEvent(DialogueEvent.ResponseReady(response))

        // Speak response
        speakResponse(response)
    }

    /**
     * Record user speech with VAD
     */
    private suspend fun startRecording() {
        if (stateMachine.state.value != DialogueState.LISTENING) return

        stateMachine.processEvent(DialogueEvent.ListeningCueComplete)

        recordingJob = scope.launch {
            try {
                val audioData = recordUserSpeech()
                if (audioData != null && audioData.isNotEmpty()) {
                    processAndRespond(audioData)
                } else {
                    handleNoAudioCaptured()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
                handleError(e.message ?: "Recording failed")
            }
        }
    }

    /**
     * Record user speech and return audio data
     */
    private suspend fun recordUserSpeech(): ByteArray? {
        audioBuffer.reset()
        audioRecorder.clearAccumulatedAudio()

        return withContext(Dispatchers.IO) {
            try {
                audioRecorder.recordWithVAD().collect { result ->
                    when (result) {
                        is VADResult.SpeechStarted -> {
                            stateMachine.processEvent(DialogueEvent.SpeechStarted)
                            Log.d(TAG, "Speech started")
                        }
                        is VADResult.SpeechEnded -> {
                            stateMachine.processEvent(DialogueEvent.SpeechEnded)
                            Log.d(TAG, "Speech ended, audio: ${result.audioData.size} bytes")
                            return@collect
                        }
                        is VADResult.Timeout -> {
                            stateMachine.processEvent(DialogueEvent.RecordingTimeout)
                            Log.d(TAG, "Recording timeout")
                            return@collect
                        }
                        else -> {}
                    }
                }

                audioRecorder.getAccumulatedAudio()
            } catch (e: CancellationException) {
                Log.d(TAG, "Recording cancelled")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
                null
            }
        }
    }

    /**
     * Process audio with LLM
     */
    private suspend fun processWithLLM(audioData: ByteArray): LLMResponse? {
        return try {
            llmClient.sendAudio(audioData, config.language.code)
        } catch (e: Exception) {
            Log.e(TAG, "LLM error", e)
            null
        }
    }

    /**
     * Process audio and generate response
     */
    private suspend fun processAndRespond(audioData: ByteArray) {
        stateMachine.processEvent(DialogueEvent.ProcessingStarted)

        val response = processWithLLM(audioData)

        if (response == null) {
            handleProcessingError()
            return
        }

        response.transcription?.let { listener?.onTranscription(it) }
        stateMachine.processEvent(DialogueEvent.ResponseReady(response))
        speakResponse(response)
    }

    /**
     * Speak the response with behavior synchronization
     */
    private suspend fun speakResponse(response: LLMResponse) {
        // Format speech
        val formattedSpeech = speechFormatter.formatWithNaturalPauses(response.speech)
        listener?.onResponse(formattedSpeech)

        // Synthesize TTS
        val ttsData = ttsClient.synthesize(formattedSpeech, config.language.ttsCode)

        if (ttsData == null) {
            Log.e(TAG, "TTS synthesis failed")
            handleTTSError()
            return
        }

        // Create behavior plan
        val behaviorPlan = behaviorMapper.createBehaviorPlan(response)

        // Estimate audio duration
        val durationMs = audioPlayer.estimateDurationMs(ttsData.size)

        // Execute behavior with synchronized playback
        playbackJob = scope.launch {
            behaviorMapper.executeBehaviorPlan(behaviorPlan, durationMs) {
                // This is called when pre-roll completes, start audio
                launch {
                    if (AudioFormatDetector.isMP3(ttsData)) {
                        audioPlayer.playMP3(ttsData)
                    } else {
                        audioPlayer.playPCM(ttsData)
                    }
                }
            }
        }

        playbackJob?.join()
    }

    /**
     * Handle speaking complete
     */
    private fun handleSpeakingComplete() {
        Log.d(TAG, "Speaking complete")

        // Report metrics
        listener?.onMetrics(stateMachine.metrics)

        if (continuousMode && !bargeInRequested.get()) {
            // Auto-start next turn
            scope.launch {
                delay(300) // Natural pause
                if (stateMachine.isIdle()) {
                    triggerManualWakeup()
                }
            }
        } else {
            // Return to listening for wakeup
            wakeupManager.start()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FEEDBACK AND ERROR HANDLING
    // ═══════════════════════════════════════════════════════════════

    private suspend fun playListeningCue() {
        // Play earcon or short expression
        behaviorMapper.setStateExpression(DialogueState.LISTENING)
        behaviorMapper.setStateLight(DialogueState.LISTENING)
    }

    private suspend fun playFillerResponse() {
        val filler = if (config.language == DialogueConfig.Language.DE) {
            "Einen Moment..."
        } else {
            "One moment..."
        }

        val ttsData = ttsClient.synthesize(filler, config.language.ttsCode)
        ttsData?.let {
            if (AudioFormatDetector.isMP3(it)) {
                audioPlayer.playMP3(it, useVoicePool = false)
            }
        }
    }

    private suspend fun handleNoAudioCaptured() {
        val message = if (config.language == DialogueConfig.Language.DE) {
            "Das habe ich nicht verstanden."
        } else {
            "I didn't catch that."
        }

        speakErrorMessage(message)
        returnToIdle()
    }

    private suspend fun handleProcessingError() {
        stateMachine.processEvent(DialogueEvent.ProcessingFailed)

        val message = if (config.language == DialogueConfig.Language.DE) {
            "Entschuldigung, ein Fehler ist aufgetreten."
        } else {
            "Sorry, an error occurred."
        }

        speakErrorMessage(message)
        returnToIdle()
    }

    private suspend fun handleTTSError() {
        val message = if (config.language == DialogueConfig.Language.DE) {
            "Sprachausgabe fehlgeschlagen."
        } else {
            "Speech output failed."
        }

        listener?.onError(message)
        returnToIdle()
    }

    private fun handleError(message: String) {
        stateMachine.processEvent(DialogueEvent.Error(message))
        listener?.onError(message)

        scope.launch {
            delay(1000)
            returnToIdle()
        }
    }

    private suspend fun speakErrorMessage(message: String) {
        listener?.onError(message)

        val ttsData = ttsClient.synthesize(message, config.language.ttsCode)
        ttsData?.let {
            if (AudioFormatDetector.isMP3(it)) {
                audioPlayer.playMP3(it)
            }
        }
    }

    private fun returnToIdle() {
        stateMachine.forceReset()
        wakeupManager.start()
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        scope.cancel()
        audioRecorder.release()
        audioPlayer.release()
        behaviorMapper.release()
        wakeupManager.release()
        Log.d(TAG, "SpeechOrchestrator released")
    }
}
