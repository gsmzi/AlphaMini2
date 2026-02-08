package com.ubtrobot.mini.sdkdemo.voicedialogue

import android.content.Context
import android.util.Log
import com.ubtrobot.mini.sdkdemo.voicedialogue.audio.DialogueAudioPlayer
import com.ubtrobot.mini.sdkdemo.voicedialogue.audio.DialogueAudioRecorder
import com.ubtrobot.mini.sdkdemo.voicedialogue.intent.BehaviorPlan
import com.ubtrobot.mini.sdkdemo.voicedialogue.intent.IntentInterpreter
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.LlmClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.LlmResponse
import com.ubtrobot.mini.sdkdemo.voicedialogue.tts.TtsClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup.WakeUpDetector
import com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup.WakeUpType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

/**
 * Voice Dialogue States
 */
enum class DialogueState {
    IDLE,       // Waiting for wake-up
    WAKING,     // Wake-up detected, preparing to record
    RECORDING,  // Recording user speech
    PROCESSING, // Sending to LLM and waiting for response
    SPEAKING,   // Playing response audio
    ERROR       // Error state, will recover to IDLE
}

/**
 * Events that trigger state transitions
 */
sealed class DialogueEvent {
    data class WakeUp(val type: WakeUpType) : DialogueEvent()
    object RecordingStarted : DialogueEvent()
    object RecordingEnded : DialogueEvent()
    data class ProcessingComplete(val response: LlmResponse) : DialogueEvent()
    object SpeakingComplete : DialogueEvent()
    data class Error(val message: String, val exception: Throwable? = null) : DialogueEvent()
    object Reset : DialogueEvent()
}

/**
 * Listener for dialogue state changes and events
 */
interface VoiceDialogueListener {
    fun onStateChanged(oldState: DialogueState, newState: DialogueState)
    fun onTranscription(text: String)
    fun onResponse(text: String)
    fun onError(message: String)
    fun onBehaviorExecuted(plan: BehaviorPlan)
}

/**
 * Voice Dialogue Controller - Main state machine for voice interaction
 *
 * Flow:
 * IDLE -> WAKING (on wake event)
 * WAKING -> RECORDING (start AudioRecord)
 * RECORDING -> PROCESSING (stop recording when VAD/timeout)
 * PROCESSING -> SPEAKING (when LLM response ready)
 * SPEAKING -> IDLE (after playback finishes)
 * any -> ERROR (on fatal exceptions)
 * ERROR -> IDLE (recovery)
 */
class VoiceDialogueController(
    private val context: Context,
    private val config: VoiceDialogueConfig = VoiceDialogueConfig()
) {
    companion object {
        private const val TAG = "VoiceDialogueController"
    }

    // Current state
    private val _state = MutableStateFlow(DialogueState.IDLE)
    val state: StateFlow<DialogueState> = _state.asStateFlow()

    // Detected language (for AUTO mode)
    private var detectedLanguage: String? = null

    // Components
    private var wakeUpDetector: WakeUpDetector? = null
    private var audioRecorder: DialogueAudioRecorder? = null
    private var llmClient: LlmClient? = null
    private var ttsClient: TtsClient? = null
    private var audioPlayer: DialogueAudioPlayer? = null
    private var intentInterpreter: IntentInterpreter? = null

    // Coroutine scope for dialogue operations
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    // Listener
    private var listener: VoiceDialogueListener? = null

    // Recording buffer
    private val recordingBuffer = ByteArrayOutputStream()

    /**
     * Initialize the controller with all components
     */
    fun initialize(
        wakeUpDetector: WakeUpDetector,
        audioRecorder: DialogueAudioRecorder,
        llmClient: LlmClient,
        ttsClient: TtsClient,
        audioPlayer: DialogueAudioPlayer,
        intentInterpreter: IntentInterpreter
    ) {
        this.wakeUpDetector = wakeUpDetector
        this.audioRecorder = audioRecorder
        this.llmClient = llmClient
        this.ttsClient = ttsClient
        this.audioPlayer = audioPlayer
        this.intentInterpreter = intentInterpreter

        // Set up wake-up callback
        wakeUpDetector.setWakeUpCallback { type ->
            handleEvent(DialogueEvent.WakeUp(type))
        }

        Log.d(TAG, "VoiceDialogueController initialized")
    }

    /**
     * Set listener for dialogue events
     */
    fun setListener(listener: VoiceDialogueListener?) {
        this.listener = listener
    }

    /**
     * Start the dialogue system (begin listening for wake-up)
     */
    fun start() {
        if (_state.value != DialogueState.IDLE) {
            Log.w(TAG, "Cannot start: not in IDLE state")
            return
        }

        wakeUpDetector?.start()
        Log.d(TAG, "Voice dialogue system started")
    }

    /**
     * Stop the dialogue system
     */
    fun stop() {
        recordingJob?.cancel()
        processingJob?.cancel()
        wakeUpDetector?.stop()
        audioRecorder?.stopRecording()
        audioPlayer?.stop()
        transitionTo(DialogueState.IDLE)
        Log.d(TAG, "Voice dialogue system stopped")
    }

    /**
     * Handle dialogue events
     */
    private fun handleEvent(event: DialogueEvent) {
        Log.d(TAG, "Event: $event in state: ${_state.value}")

        when (event) {
            is DialogueEvent.WakeUp -> handleWakeUp(event.type)
            is DialogueEvent.RecordingStarted -> { /* State already transitioned */ }
            is DialogueEvent.RecordingEnded -> handleRecordingEnded()
            is DialogueEvent.ProcessingComplete -> handleProcessingComplete(event.response)
            is DialogueEvent.SpeakingComplete -> handleSpeakingComplete()
            is DialogueEvent.Error -> handleError(event.message, event.exception)
            is DialogueEvent.Reset -> handleReset()
        }
    }

    /**
     * Handle wake-up event
     */
    private fun handleWakeUp(type: WakeUpType) {
        Log.d(TAG, "handleWakeUp called with type=$type, currentState=${_state.value}")

        if (_state.value != DialogueState.IDLE) {
            Log.w(TAG, "Ignoring wake-up: not in IDLE state (current=${_state.value})")
            return
        }

        Log.d(TAG, "Wake-up detected: $type - transitioning to WAKING")
        transitionTo(DialogueState.WAKING)

        // LOW LATENCY: Start recording IMMEDIATELY, feedback in parallel
        scope.launch {
            try {
                Log.d(TAG, "Wake-up: starting recording immediately")

                // Start recording FIRST (no delays!)
                startRecording()

                // Execute feedback in parallel (non-blocking)
                if (config.enableExpressions || config.enableLights) {
                    scope.launch { intentInterpreter?.executeWakeUpFeedback() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wake-up handling failed", e)
                handleEvent(DialogueEvent.Error("Wake-up handling failed", e))
            }
        }
    }

    /**
     * Start recording audio - LOW LATENCY: no delays, parallel feedback
     */
    private fun startRecording() {
        Log.d(TAG, "startRecording() called")
        transitionTo(DialogueState.RECORDING)
        recordingBuffer.reset()

        // Show listening feedback in parallel (non-blocking)
        if (config.enableExpressions) {
            scope.launch { intentInterpreter?.executeListeningFeedback() }
        }

        recordingJob = scope.launch {
            try {
                Log.d(TAG, "Starting audio collection...")
                // Collect audio frames
                audioRecorder?.record()?.collect { chunk ->
                    recordingBuffer.write(chunk)
                }

                // Recording completed (VAD triggered stop)
                Log.d(TAG, "Recording done: ${recordingBuffer.size()} bytes")
                handleEvent(DialogueEvent.RecordingEnded)

            } catch (e: CancellationException) {
                Log.d(TAG, "Recording cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                handleEvent(DialogueEvent.Error("Recording failed", e))
            }
        }

        // Set up recording timeout
        scope.launch {
            delay(config.recordingTimeoutMs)
            if (_state.value == DialogueState.RECORDING) {
                Log.d(TAG, "Recording timeout")
                stopRecording()
            }
        }
    }

    /**
     * Stop recording
     */
    fun stopRecording() {
        audioRecorder?.stopRecording()
        recordingJob?.cancel()
    }

    /**
     * Handle recording ended event
     */
    private fun handleRecordingEnded() {
        Log.d(TAG, "handleRecordingEnded() called, currentState=${_state.value}")
        if (_state.value != DialogueState.RECORDING) {
            Log.w(TAG, "Ignoring RecordingEnded: not in RECORDING state")
            return
        }

        transitionTo(DialogueState.PROCESSING)

        processingJob = scope.launch {
            try {
                // Show processing feedback
                if (config.enableExpressions) {
                    Log.d(TAG, "Executing processing feedback...")
                    intentInterpreter?.executeProcessingFeedback()
                }

                val audioData = recordingBuffer.toByteArray()
                Log.d(TAG, "Audio data size: ${audioData.size} bytes")

                if (audioData.isEmpty()) {
                    // No audio captured
                    Log.w(TAG, "No audio captured, handling empty audio case")
                    handleNoAudioCaptured()
                    return@launch
                }

                // Send to LLM
                Log.d(TAG, "Sending audio to LLM (${audioData.size} bytes)...")
                val response = if (config.useDirectAudioUpload) {
                    llmClient?.sendAudio(audioData, config.getLlmLanguageInstruction(detectedLanguage))
                } else {
                    // For now, send audio directly; ASR integration would go here
                    llmClient?.sendAudio(audioData, config.getLlmLanguageInstruction(detectedLanguage))
                }

                Log.d(TAG, "LLM response received: ${response != null}")

                if (response != null) {
                    Log.d(TAG, "LLM response text: ${response.responseText?.take(50)}...")
                    // Update detected language if available
                    response.detectedLanguage?.let { detectedLanguage = it }

                    handleEvent(DialogueEvent.ProcessingComplete(response))
                } else {
                    Log.e(TAG, "LLM returned null response")
                    handleEvent(DialogueEvent.Error("No response from LLM"))
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Processing cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                handleEvent(DialogueEvent.Error("Processing failed", e))
            }
        }
    }

    /**
     * Handle case when no audio was captured
     */
    private suspend fun handleNoAudioCaptured() {
        val message = VoiceDialogueStrings.DID_NOT_CATCH.get(config.languageMode, detectedLanguage)
        speakFeedback(message)
        transitionTo(DialogueState.IDLE)
        wakeUpDetector?.start()
    }

    /**
     * Handle LLM response received
     */
    private fun handleProcessingComplete(response: LlmResponse) {
        Log.d(TAG, "handleProcessingComplete() called, currentState=${_state.value}")
        if (_state.value != DialogueState.PROCESSING) {
            Log.w(TAG, "Ignoring ProcessingComplete: not in PROCESSING state")
            return
        }

        transitionTo(DialogueState.SPEAKING)

        scope.launch {
            try {
                // Parse intents and create behavior plan
                Log.d(TAG, "Parsing intents from response...")
                val behaviorPlan = intentInterpreter?.parse(response)

                // Notify transcription immediately
                response.transcription?.let {
                    Log.d(TAG, "User said: ${it}")
                    listener?.onTranscription(it)
                }

                // Prepare audio first, then show response and play
                var audioData: ByteArray? = null

                if (response.responseAudio != null) {
                    audioData = response.responseAudio
                    Log.d(TAG, "Using direct audio from LLM (${audioData.size} bytes)")
                } else if (response.responseText != null) {
                    // Synthesize TTS first
                    val languageCode = config.getTtsLanguageCode(detectedLanguage)
                    Log.d(TAG, "Synthesizing TTS: '${response.responseText.take(50)}...'")
                    audioData = ttsClient?.synthesize(response.responseText, languageCode)
                    Log.d(TAG, "TTS ready: ${audioData?.size ?: 0} bytes")
                }

                if (audioData != null) {
                    // NOW show response (audio is ready)
                    response.responseText?.let {
                        Log.d(TAG, "Showing response: ${it.take(50)}...")
                        listener?.onResponse(it)
                    }

                    // Execute behaviors
                    if (behaviorPlan != null && (config.enableExpressions || config.enableActions || config.enableLights)) {
                        Log.d(TAG, "Executing behavior plan...")
                        executeBehaviorPlan(behaviorPlan)
                    }

                    // Play audio immediately
                    playAudioResponse(audioData)
                } else {
                    Log.e(TAG, "No audio available")
                    handleEvent(DialogueEvent.Error("Failed to generate audio"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Response handling failed", e)
                handleEvent(DialogueEvent.Error("Response handling failed", e))
            }
        }
    }

    /**
     * Execute behavior plan (expressions, actions, lights)
     */
    private suspend fun executeBehaviorPlan(plan: BehaviorPlan) {
        try {
            intentInterpreter?.execute(plan)
            listener?.onBehaviorExecuted(plan)
        } catch (e: Exception) {
            Log.e(TAG, "Behavior execution failed", e)
        }
    }

    /**
     * Play audio response
     */
    private suspend fun playAudioResponse(audioData: ByteArray) {
        Log.d(TAG, "playAudioResponse() called with ${audioData.size} bytes")
        try {
            audioPlayer?.play(audioData, onComplete = {
                Log.d(TAG, "Audio playback completed callback received")
                handleEvent(DialogueEvent.SpeakingComplete)
            })
            Log.d(TAG, "audioPlayer.play() called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback failed", e)
            handleEvent(DialogueEvent.Error("Audio playback failed", e))
        }
    }

    /**
     * Handle speaking complete
     */
    private fun handleSpeakingComplete() {
        if (_state.value != DialogueState.SPEAKING) return

        // Check if continuous conversation mode is enabled
        if (config.enableContinuousConversation) {
            // Continue listening immediately without requiring wake-up
            Log.d(TAG, "Continuous mode: auto-starting next recording")

            // Go directly to recording (skip WAKING state for speed)
            scope.launch {
                try {
                    // Brief pause for natural conversation flow
                    delay(300)

                    // Start recording directly
                    startRecording()
                } catch (e: Exception) {
                    Log.e(TAG, "Continuous recording failed", e)
                    transitionTo(DialogueState.IDLE)
                    wakeUpDetector?.start()
                }
            }
        } else {
            // Return to idle and listen for next wake-up
            transitionTo(DialogueState.IDLE)
            wakeUpDetector?.start()

            // Reset expression to normal
            scope.launch {
                if (config.enableExpressions) {
                    intentInterpreter?.executeIdleFeedback()
                }
            }
        }
    }

    /**
     * Handle error
     */
    private fun handleError(message: String, exception: Throwable?) {
        Log.e(TAG, "Error: $message", exception)
        listener?.onError(message)

        transitionTo(DialogueState.ERROR)

        // Speak error message and recover
        scope.launch {
            try {
                val errorMessage = VoiceDialogueStrings.ERROR_OCCURRED.get(config.languageMode, detectedLanguage)
                speakFeedback(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to speak error message", e)
            } finally {
                // Recover to IDLE
                delay(1000)
                handleEvent(DialogueEvent.Reset)
            }
        }
    }

    /**
     * Handle reset (recover from error)
     */
    private fun handleReset() {
        recordingJob?.cancel()
        processingJob?.cancel()
        audioRecorder?.stopRecording()
        audioPlayer?.stop()
        recordingBuffer.reset()

        transitionTo(DialogueState.IDLE)
        wakeUpDetector?.start()

        scope.launch {
            if (config.enableExpressions) {
                intentInterpreter?.executeIdleFeedback()
            }
        }
    }

    /**
     * Speak feedback message
     */
    private suspend fun speakFeedback(message: String) {
        val languageCode = config.getTtsLanguageCode(detectedLanguage)
        val audioData = ttsClient?.synthesize(message, languageCode)
        if (audioData != null) {
            val deferred = CompletableDeferred<Unit>()
            audioPlayer?.play(audioData, onComplete = { deferred.complete(Unit) })
            deferred.await()
        }
    }

    /**
     * Transition to new state
     */
    private fun transitionTo(newState: DialogueState) {
        val oldState = _state.value
        if (oldState == newState) return

        _state.value = newState
        Log.d(TAG, "State transition: $oldState -> $newState")
        listener?.onStateChanged(oldState, newState)
    }

    /**
     * Manually trigger wake-up (for testing or button press)
     */
    fun triggerWakeUp(type: WakeUpType = WakeUpType.BUTTON) {
        Log.d(TAG, "triggerWakeUp() called manually with type=$type")
        handleEvent(DialogueEvent.WakeUp(type))
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        scope.cancel()
        wakeUpDetector?.release()
        audioRecorder?.release()
        audioPlayer?.release()
        ttsClient?.release()
        Log.d(TAG, "VoiceDialogueController released")
    }
}
