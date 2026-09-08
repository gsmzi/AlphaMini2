package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dialogue States following the CONTINUOUS CONVERSATION state machine:
 *
 * Idle → Listening → Capturing → Thinking → Speaking → ListeningForFollowUp → (repeat) → ConversationEnd → Idle
 *
 * Key differences from single-turn:
 * - ListeningForFollowUp: Auto-listens after speaking without requiring wake word
 * - ConversationEnd: Graceful session termination state
 */
enum class DialogueState {
    IDLE,                    // Waiting for wakeup (initial state)
    LISTENING,               // Wakeup detected, playing listening cue, ready to record
    CAPTURING,               // Recording user speech, VAD active
    THINKING,                // Processing: ASR → LLM → TTS pipeline
    SPEAKING,                // Playing response audio with synchronized behavior
    LISTENING_FOR_FOLLOWUP,  // Auto-listening after robot speaks (no wake word needed)
    CONVERSATION_END,        // Graceful conversation ending state
    ERROR                    // Error recovery state
}

/**
 * Events that trigger state transitions
 */
sealed class DialogueEvent {
    // Wakeup events
    data class WakeupDetected(val source: WakeupSource) : DialogueEvent()

    // Recording events
    object ListeningCueComplete : DialogueEvent()
    object SpeechStarted : DialogueEvent()
    object SpeechEnded : DialogueEvent()
    object RecordingTimeout : DialogueEvent()

    // Silence events for follow-up listening
    object ShortPause : DialogueEvent()          // Keep listening
    object LongSilence : DialogueEvent()         // Prompt user
    object VeryLongSilence : DialogueEvent()     // End session

    // Processing events
    object ProcessingStarted : DialogueEvent()
    data class ResponseReady(val response: LLMResponse) : DialogueEvent()
    object ProcessingFailed : DialogueEvent()

    // Playback events
    object SpeakingStarted : DialogueEvent()
    object SpeakingComplete : DialogueEvent()

    // Session events
    object SessionContinue : DialogueEvent()     // LLM says keep_session=true
    object SessionEnd : DialogueEvent()          // User says stop phrase or LLM ends
    object GracefulEnd : DialogueEvent()         // Transition from CONVERSATION_END to IDLE

    // Interaction events
    object BargeIn : DialogueEvent()
    object Cancel : DialogueEvent()
    object Reset : DialogueEvent()

    // Error events
    data class Error(val message: String, val cause: Throwable? = null) : DialogueEvent()
}

enum class WakeupSource {
    VOICE_KEYWORD,
    CHEST_BUTTON,
    MANUAL_TRIGGER,
    FOLLOWUP_AUTO  // Auto-triggered for follow-up turns
}

/**
 * Latency metrics for monitoring performance
 */
data class LatencyMetrics(
    var tWakeup: Long = 0,           // Wakeup timestamp
    var tSpeechStart: Long = 0,      // User speech start
    var tSpeechEnd: Long = 0,        // User speech end (endpoint)
    var tRequestSent: Long = 0,      // Request sent to LLM
    var tFirstToken: Long = 0,       // First LLM token received
    var tTtsStart: Long = 0,         // TTS generation started
    var tAudioStart: Long = 0,       // Audio playback started
    var tAudioEnd: Long = 0,         // Audio playback ended
    var turnIndex: Int = 0           // Current turn in session
) {
    fun endpointToAudioLatency(): Long = tAudioStart - tSpeechEnd
    fun totalTurnLatency(): Long = tAudioStart - tWakeup
    fun llmLatency(): Long = tFirstToken - tRequestSent
    fun ttsLatency(): Long = tAudioStart - tTtsStart

    fun log(tag: String) {
        Log.d(tag, """
            |Latency Metrics (Turn $turnIndex):
            |  Endpoint → Audio: ${endpointToAudioLatency()}ms
            |  Total Turn: ${totalTurnLatency()}ms
            |  LLM: ${llmLatency()}ms
            |  TTS: ${ttsLatency()}ms
        """.trimMargin())
    }

    fun reset() {
        tWakeup = 0; tSpeechStart = 0; tSpeechEnd = 0
        tRequestSent = 0; tFirstToken = 0; tTtsStart = 0
        tAudioStart = 0; tAudioEnd = 0
    }

    fun nextTurn() {
        turnIndex++
        tWakeup = System.currentTimeMillis()
        tSpeechStart = 0; tSpeechEnd = 0
        tRequestSent = 0; tFirstToken = 0; tTtsStart = 0
        tAudioStart = 0; tAudioEnd = 0
    }
}

/**
 * Dialogue State Machine for CONTINUOUS CONVERSATION
 *
 * Manages state transitions with validation and metrics tracking.
 * Supports multi-turn dialogue without re-wakeup between turns.
 */
class DialogueStateMachine {
    companion object {
        private const val TAG = "DialogueStateMachine"
    }

    private val _state = MutableStateFlow(DialogueState.IDLE)
    val state: StateFlow<DialogueState> = _state.asStateFlow()

    private val _metrics = LatencyMetrics()
    val metrics: LatencyMetrics get() = _metrics

    // Valid transitions map for continuous conversation
    private val validTransitions: Map<DialogueState, Set<DialogueState>> = mapOf(
        DialogueState.IDLE to setOf(
            DialogueState.LISTENING,
            DialogueState.ERROR
        ),
        DialogueState.LISTENING to setOf(
            DialogueState.CAPTURING,
            DialogueState.IDLE,
            DialogueState.ERROR
        ),
        DialogueState.CAPTURING to setOf(
            DialogueState.THINKING,
            DialogueState.IDLE,
            DialogueState.LISTENING_FOR_FOLLOWUP,  // Timeout during follow-up
            DialogueState.ERROR
        ),
        DialogueState.THINKING to setOf(
            DialogueState.SPEAKING,
            DialogueState.IDLE,
            DialogueState.ERROR
        ),
        DialogueState.SPEAKING to setOf(
            DialogueState.LISTENING_FOR_FOLLOWUP,  // Continue conversation
            DialogueState.CONVERSATION_END,         // End conversation
            DialogueState.LISTENING,                // Barge-in
            DialogueState.IDLE,
            DialogueState.ERROR
        ),
        DialogueState.LISTENING_FOR_FOLLOWUP to setOf(
            DialogueState.CAPTURING,               // User starts speaking
            DialogueState.SPEAKING,                // Prompt after silence
            DialogueState.CONVERSATION_END,        // Very long silence
            DialogueState.LISTENING,               // Barge-in restarts
            DialogueState.IDLE,
            DialogueState.ERROR
        ),
        DialogueState.CONVERSATION_END to setOf(
            DialogueState.IDLE                     // After goodbye
        ),
        DialogueState.ERROR to setOf(
            DialogueState.IDLE
        )
    )

    /**
     * Process an event and transition to the appropriate state
     */
    fun processEvent(event: DialogueEvent): Boolean {
        val currentState = _state.value
        val newState = determineNextState(currentState, event)

        if (newState == null) {
            Log.w(TAG, "Invalid event $event in state $currentState")
            return false
        }

        if (!isValidTransition(currentState, newState)) {
            Log.w(TAG, "Invalid transition: $currentState → $newState")
            return false
        }

        // Update metrics based on event
        updateMetrics(event)

        // Perform transition
        Log.d(TAG, "State transition: $currentState → $newState (event: $event)")
        _state.value = newState
        return true
    }

    private fun determineNextState(current: DialogueState, event: DialogueEvent): DialogueState? {
        return when (event) {
            // Wakeup events
            is DialogueEvent.WakeupDetected -> {
                when (current) {
                    DialogueState.IDLE -> DialogueState.LISTENING
                    DialogueState.LISTENING_FOR_FOLLOWUP -> {
                        // Barge-in during follow-up listening
                        if (event.source == WakeupSource.FOLLOWUP_AUTO) {
                            DialogueState.CAPTURING
                        } else {
                            DialogueState.LISTENING
                        }
                    }
                    else -> null
                }
            }

            // Recording events
            is DialogueEvent.ListeningCueComplete -> {
                when (current) {
                    DialogueState.LISTENING -> DialogueState.CAPTURING
                    DialogueState.LISTENING_FOR_FOLLOWUP -> DialogueState.CAPTURING
                    else -> null
                }
            }
            is DialogueEvent.SpeechStarted -> {
                // Stay in CAPTURING or transition from LISTENING_FOR_FOLLOWUP
                when (current) {
                    DialogueState.CAPTURING -> DialogueState.CAPTURING
                    DialogueState.LISTENING_FOR_FOLLOWUP -> DialogueState.CAPTURING
                    else -> null
                }
            }
            is DialogueEvent.SpeechEnded, is DialogueEvent.RecordingTimeout -> {
                if (current == DialogueState.CAPTURING) DialogueState.THINKING else null
            }

            // Silence events
            is DialogueEvent.ShortPause -> {
                // Stay in current listening state
                if (current == DialogueState.LISTENING_FOR_FOLLOWUP) current else null
            }
            is DialogueEvent.LongSilence -> {
                // Trigger a prompt (go to SPEAKING with a prompt)
                if (current == DialogueState.LISTENING_FOR_FOLLOWUP) DialogueState.SPEAKING else null
            }
            is DialogueEvent.VeryLongSilence -> {
                // End session
                if (current == DialogueState.LISTENING_FOR_FOLLOWUP) DialogueState.CONVERSATION_END else null
            }

            // Processing events
            is DialogueEvent.ProcessingStarted -> {
                if (current == DialogueState.THINKING) DialogueState.THINKING else null
            }
            is DialogueEvent.ResponseReady -> {
                if (current == DialogueState.THINKING) DialogueState.SPEAKING else null
            }
            is DialogueEvent.ProcessingFailed -> {
                if (current == DialogueState.THINKING) DialogueState.ERROR else null
            }

            // Playback events
            is DialogueEvent.SpeakingStarted -> {
                if (current == DialogueState.SPEAKING) DialogueState.SPEAKING else null
            }
            is DialogueEvent.SpeakingComplete -> {
                // This is now handled by SessionContinue/SessionEnd
                if (current == DialogueState.SPEAKING) DialogueState.SPEAKING else null
            }

            // Session events
            is DialogueEvent.SessionContinue -> {
                // Continue to ListeningForFollowUp after speaking
                if (current == DialogueState.SPEAKING) DialogueState.LISTENING_FOR_FOLLOWUP else null
            }
            is DialogueEvent.SessionEnd -> {
                // End the conversation
                when (current) {
                    DialogueState.SPEAKING -> DialogueState.CONVERSATION_END
                    DialogueState.LISTENING_FOR_FOLLOWUP -> DialogueState.CONVERSATION_END
                    DialogueState.CAPTURING -> DialogueState.CONVERSATION_END
                    else -> null
                }
            }
            is DialogueEvent.GracefulEnd -> {
                if (current == DialogueState.CONVERSATION_END) DialogueState.IDLE else null
            }

            // Interaction events
            is DialogueEvent.BargeIn -> {
                // Barge-in: interrupt speaking and go back to listening
                when (current) {
                    DialogueState.SPEAKING -> DialogueState.LISTENING
                    DialogueState.LISTENING_FOR_FOLLOWUP -> DialogueState.LISTENING
                    else -> null
                }
            }
            is DialogueEvent.Cancel, is DialogueEvent.Reset -> DialogueState.IDLE
            is DialogueEvent.Error -> DialogueState.ERROR
        }
    }

    private fun isValidTransition(from: DialogueState, to: DialogueState): Boolean {
        return validTransitions[from]?.contains(to) == true || from == to
    }

    private fun updateMetrics(event: DialogueEvent) {
        val now = System.currentTimeMillis()
        when (event) {
            is DialogueEvent.WakeupDetected -> {
                if (event.source != WakeupSource.FOLLOWUP_AUTO) {
                    _metrics.reset()
                    _metrics.turnIndex = 0
                } else {
                    _metrics.nextTurn()
                }
                _metrics.tWakeup = now
            }
            is DialogueEvent.SpeechStarted -> _metrics.tSpeechStart = now
            is DialogueEvent.SpeechEnded -> _metrics.tSpeechEnd = now
            is DialogueEvent.ProcessingStarted -> _metrics.tRequestSent = now
            is DialogueEvent.ResponseReady -> _metrics.tFirstToken = now
            is DialogueEvent.SpeakingStarted -> _metrics.tAudioStart = now
            is DialogueEvent.SpeakingComplete -> {
                _metrics.tAudioEnd = now
                _metrics.log(TAG)
            }
            else -> {}
        }
    }

    /**
     * Force reset to IDLE (for error recovery)
     */
    fun forceReset() {
        Log.w(TAG, "Force reset from ${_state.value} to IDLE")
        _state.value = DialogueState.IDLE
        _metrics.reset()
    }

    /**
     * Check if currently in an interruptible state (barge-in allowed)
     */
    fun isInterruptible(): Boolean {
        return _state.value in setOf(
            DialogueState.SPEAKING,
            DialogueState.LISTENING_FOR_FOLLOWUP
        )
    }

    /**
     * Check if ready for new interaction
     */
    fun isIdle(): Boolean {
        return _state.value == DialogueState.IDLE
    }

    /**
     * Check if in active conversation (not idle or ending)
     */
    fun isInConversation(): Boolean {
        return _state.value !in setOf(
            DialogueState.IDLE,
            DialogueState.CONVERSATION_END,
            DialogueState.ERROR
        )
    }

    /**
     * Check if listening for follow-up
     */
    fun isListeningForFollowUp(): Boolean {
        return _state.value == DialogueState.LISTENING_FOR_FOLLOWUP
    }
}
