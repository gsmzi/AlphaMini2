package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Extended Dialogue States for Continuous Conversation
 *
 * State Flow:
 * IDLE → WAKEUP → LISTENING → CAPTURING → THINKING → SPEAKING
 *      → LISTENING_FOR_FOLLOWUP → (CAPTURING → ...) or CONVERSATION_END → IDLE
 */
enum class ContinuousState {
    /** Robot is idle, waiting for wakeup */
    IDLE,

    /** Wakeup detected, initializing */
    WAKEUP,

    /** Actively listening for speech start */
    LISTENING,

    /** Speech detected, capturing audio */
    CAPTURING,

    /** Processing: sending to LLM, waiting for response */
    THINKING,

    /** Playing response audio + synchronized behaviors */
    SPEAKING,

    /** Auto-listening after robot speaks (no wakeup needed) */
    LISTENING_FOR_FOLLOWUP,

    /** Prompting user after silence */
    PROMPTING,

    /** Conversation ending gracefully */
    CONVERSATION_END,

    /** Error state */
    ERROR
}

/**
 * State transition event for logging/debugging
 */
data class StateTransition(
    val from: ContinuousState,
    val to: ContinuousState,
    val trigger: String,
    val timestamp: Long = System.currentTimeMillis(),
    val turnIndex: Int = 0
)

/**
 * Continuous Dialogue State Machine
 *
 * Manages state transitions with validation and logging.
 */
class ContinuousStateMachine(
    private val onStateChanged: ((ContinuousState, ContinuousState) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ContinuousStateMachine"

        /** Valid state transitions */
        private val VALID_TRANSITIONS = mapOf(
            ContinuousState.IDLE to setOf(
                ContinuousState.WAKEUP,
                ContinuousState.ERROR
            ),
            ContinuousState.WAKEUP to setOf(
                ContinuousState.LISTENING,
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.LISTENING to setOf(
                ContinuousState.CAPTURING,
                ContinuousState.PROMPTING,  // Long silence
                ContinuousState.CONVERSATION_END,  // Very long silence
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.CAPTURING to setOf(
                ContinuousState.THINKING,
                ContinuousState.LISTENING,  // False trigger
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.THINKING to setOf(
                ContinuousState.SPEAKING,
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.SPEAKING to setOf(
                ContinuousState.LISTENING_FOR_FOLLOWUP,
                ContinuousState.CAPTURING,  // Barge-in
                ContinuousState.CONVERSATION_END,
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.LISTENING_FOR_FOLLOWUP to setOf(
                ContinuousState.CAPTURING,
                ContinuousState.PROMPTING,  // No response, prompt user
                ContinuousState.CONVERSATION_END,  // Timeout
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.PROMPTING to setOf(
                ContinuousState.SPEAKING,  // Say prompt
                ContinuousState.LISTENING_FOR_FOLLOWUP,
                ContinuousState.CONVERSATION_END,
                ContinuousState.ERROR,
                ContinuousState.IDLE
            ),
            ContinuousState.CONVERSATION_END to setOf(
                ContinuousState.SPEAKING,  // Say goodbye
                ContinuousState.IDLE
            ),
            ContinuousState.ERROR to setOf(
                ContinuousState.IDLE,
                ContinuousState.LISTENING
            )
        )
    }

    private val _state = MutableStateFlow(ContinuousState.IDLE)
    val state: StateFlow<ContinuousState> = _state.asStateFlow()

    private val _transitions = mutableListOf<StateTransition>()
    val transitions: List<StateTransition> get() = _transitions.toList()

    private var currentTurnIndex: Int = 0

    val currentState: ContinuousState get() = _state.value

    /**
     * Attempt state transition
     * @return true if transition was valid and executed
     */
    fun transition(newState: ContinuousState, trigger: String): Boolean {
        val oldState = _state.value

        // Check if transition is valid
        val validTargets = VALID_TRANSITIONS[oldState] ?: emptySet()
        if (newState !in validTargets) {
            Log.w(TAG, "Invalid transition: $oldState → $newState (trigger: $trigger)")
            return false
        }

        // Execute transition
        _state.value = newState

        // Log transition
        val transition = StateTransition(
            from = oldState,
            to = newState,
            trigger = trigger,
            turnIndex = currentTurnIndex
        )
        _transitions.add(transition)

        Log.d(TAG, "State: $oldState → $newState (trigger: $trigger, turn: $currentTurnIndex)")

        // Notify listener
        onStateChanged?.invoke(oldState, newState)

        return true
    }

    /**
     * Force transition to state (bypass validation)
     * Use only for error recovery or reset
     */
    fun forceState(newState: ContinuousState, reason: String) {
        val oldState = _state.value
        _state.value = newState
        Log.w(TAG, "FORCED: $oldState → $newState (reason: $reason)")

        _transitions.add(StateTransition(
            from = oldState,
            to = newState,
            trigger = "FORCED: $reason",
            turnIndex = currentTurnIndex
        ))

        onStateChanged?.invoke(oldState, newState)
    }

    /**
     * Update turn index
     */
    fun setTurnIndex(index: Int) {
        currentTurnIndex = index
    }

    /**
     * Reset state machine
     */
    fun reset() {
        _state.value = ContinuousState.IDLE
        _transitions.clear()
        currentTurnIndex = 0
        Log.d(TAG, "State machine reset")
    }

    /**
     * Check if in a listening state (can receive speech)
     */
    fun isListening(): Boolean {
        return _state.value in setOf(
            ContinuousState.LISTENING,
            ContinuousState.LISTENING_FOR_FOLLOWUP,
            ContinuousState.CAPTURING
        )
    }

    /**
     * Check if in an active conversation state
     */
    fun isInConversation(): Boolean {
        return _state.value !in setOf(
            ContinuousState.IDLE,
            ContinuousState.ERROR
        )
    }

    /**
     * Check if can be interrupted (barge-in)
     */
    fun canBargeIn(): Boolean {
        return _state.value == ContinuousState.SPEAKING
    }

    /**
     * Get state duration in ms
     */
    fun getStateDurationMs(): Long {
        val lastTransition = _transitions.lastOrNull() ?: return 0
        return System.currentTimeMillis() - lastTransition.timestamp
    }
}
