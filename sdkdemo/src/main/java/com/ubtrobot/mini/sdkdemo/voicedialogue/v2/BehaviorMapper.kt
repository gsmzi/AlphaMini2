package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.util.Log
import kotlinx.coroutines.*

/**
 * Robot behavior plan synchronized with speech
 */
data class BehaviorPlan(
    val expression: String?,
    val action: String?,
    val light: LightConfig?,
    val preRollMs: Long,
    val postRollMs: Long
) {
    data class LightConfig(
        val mode: String,
        val color: Int? = null,
        val brightness: Int = 100
    )
}

/**
 * Behavior execution result
 */
sealed class BehaviorResult {
    object Success : BehaviorResult()
    data class PartialSuccess(val errors: List<String>) : BehaviorResult()
    data class Failure(val error: String) : BehaviorResult()
}

/**
 * Behavior Mapper
 *
 * Maps LLM response intents to robot behaviors (expressions, actions, lights)
 * and handles synchronization with audio playback.
 *
 * Uses placeholder interfaces for robot APIs - replace with actual SDK calls.
 */
class BehaviorMapper {
    companion object {
        private const val TAG = "BehaviorMapper"

        // Expression ID mappings
        private val EXPRESSION_MAP = mapOf(
            // Emotion to expression ID
            "happy" to "emo_007",       // Happy face
            "开心" to "emo_007",
            "neutral" to "normal_1",
            "中性" to "normal_1",
            "comfort" to "emo_006",
            "安慰" to "emo_006",
            "sorry" to "emo_014",
            "抱歉" to "emo_014",
            "surprised" to "codemao8",
            "惊讶" to "codemao8",
            "thinking" to "emo_010",
            "思考" to "emo_010",
            "excited" to "emo_008",     // Excited face
            "兴奋" to "emo_008",
            "listening" to "emo_007",   // Changed from emo_009 (crying) to emo_007 (happy)
            "speaking" to "emo_007"     // Changed to happy expression
        )

        // Action ID mappings - Use IDs from ActionActivity.kt
        // Note: IDs are plain numbers like "017", not "action_017"
        private val ACTION_MAP = mapOf(
            "wave" to "010",         // Greeting/wave action
            "nod" to "011",          // Nod action
            "dance" to "014",        // Tai Chi dance (changed from 020)
            "bow" to "016",          // Bow action
            "clap" to "018",         // Clap action
            "think" to "021",        // Think action
            "idle" to "011",         // Idle/nod
            "greeting" to "010",     // Greeting action
            "hands_up" to "017",     // Hands up (from ActionActivity)
            "raise_hands" to "017",  // Hands up alias
            "taiji" to "014",        // Taiji action
            "squat" to "031"         // Squat action
        )

        // Light mode mappings
        private val LIGHT_MAP = mapOf(
            "normal" to LightMode.NORMAL,
            "happy" to LightMode.HAPPY,
            "thinking" to LightMode.THINKING,
            "listening" to LightMode.LISTENING,
            "speaking" to LightMode.SPEAKING,
            "error" to LightMode.ERROR,
            "green" to LightMode.GREEN,
            "blue" to LightMode.BLUE,
            "red" to LightMode.RED,
            "yellow" to LightMode.YELLOW,
            "purple" to LightMode.PURPLE
        )
    }

    enum class LightMode(val colorHex: Int) {
        NORMAL(0xFFFFFF),
        HAPPY(0x00FF00),
        THINKING(0x0000FF),
        LISTENING(0x00FFFF),
        SPEAKING(0xFF00FF),
        ERROR(0xFF0000),
        GREEN(0x00FF00),
        BLUE(0x0000FF),
        RED(0xFF0000),
        YELLOW(0xFFFF00),
        PURPLE(0x800080)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Robot API interfaces (placeholder - replace with actual SDK)
    private var expressApi: ExpressApiPlaceholder? = null
    private var actionApi: ActionApiPlaceholder? = null
    private var lightApi: LightApiPlaceholder? = null

    /**
     * Initialize with robot APIs
     */
    fun initialize(
        expressApi: ExpressApiPlaceholder? = null,
        actionApi: ActionApiPlaceholder? = null,
        lightApi: LightApiPlaceholder? = null
    ) {
        this.expressApi = expressApi
        this.actionApi = actionApi
        this.lightApi = lightApi
        Log.d(TAG, "=== BehaviorMapper INITIALIZED ===")
        Log.d(TAG, "  expressApi: ${if (expressApi != null) "OK" else "NULL"}")
        Log.d(TAG, "  actionApi: ${if (actionApi != null) "OK" else "NULL"}")
        Log.d(TAG, "  lightApi: ${if (lightApi != null) "OK" else "NULL"}")
    }

    /**
     * Create behavior plan from LLM response
     */
    fun createBehaviorPlan(response: LLMResponse): BehaviorPlan {
        // Map emotion to expression
        val expressionId = response.expression
            ?: EXPRESSION_MAP[response.emotion.name.lowercase()]
            ?: response.emotion.expressionId

        // Map action - check ACTION_MAP first, then use as-is if not found
        val rawAction = response.action
        val actionId = rawAction?.let {
            val mapped = ACTION_MAP[it.lowercase()]
            Log.d(TAG, "Action mapping: '$it' -> '${mapped ?: it}'")
            mapped ?: it
        }

        // Map light
        val lightConfig = response.light?.let { lightValue ->
            val mode = LIGHT_MAP[lightValue.lowercase()] ?: LightMode.NORMAL
            BehaviorPlan.LightConfig(
                mode = lightValue,
                color = mode.colorHex
            )
        }

        Log.d(TAG, "BehaviorPlan: expression=$expressionId, action=$actionId, light=${lightConfig?.mode}")

        return BehaviorPlan(
            expression = expressionId,
            action = actionId,
            light = lightConfig,
            preRollMs = response.timing.preRollMs,
            postRollMs = response.timing.postRollMs
        )
    }

    /**
     * Execute behavior plan with timing synchronization
     *
     * @param plan Behavior plan to execute
     * @param speechDurationMs Expected speech duration for post-roll timing
     * @param onPreRollComplete Called when pre-roll behaviors have started
     */
    suspend fun executeBehaviorPlan(
        plan: BehaviorPlan,
        speechDurationMs: Long,
        onPreRollComplete: suspend () -> Unit
    ): BehaviorResult {
        val errors = mutableListOf<String>()

        try {
            // Start behaviors before speech (pre-roll)
            Log.d(TAG, "Starting pre-roll behaviors (${plan.preRollMs}ms before speech)")

            // Start expression
            plan.expression?.let {
                try {
                    startExpression(it)
                } catch (e: Exception) {
                    errors.add("Expression failed: ${e.message}")
                    Log.e(TAG, "Expression error", e)
                }
            }

            // Start light
            plan.light?.let {
                try {
                    setLight(it)
                } catch (e: Exception) {
                    errors.add("Light failed: ${e.message}")
                    Log.e(TAG, "Light error", e)
                }
            }

            // Wait pre-roll duration
            delay(plan.preRollMs)

            // Signal that pre-roll is complete, speech can start
            onPreRollComplete()

            // Start action (timed to speech)
            plan.action?.let { actionId ->
                scope.launch {
                    try {
                        Log.d(TAG, "=== STARTING ACTION ===")
                        Log.d(TAG, "  actionId: $actionId")
                        Log.d(TAG, "  actionApi is ${if (actionApi != null) "AVAILABLE" else "NULL"}")
                        startAction(actionId)
                    } catch (e: Exception) {
                        errors.add("Action failed: ${e.message}")
                        Log.e(TAG, "Action error", e)
                    }
                }
            } ?: Log.d(TAG, "No action in behavior plan")

            // Wait for speech duration + post-roll
            // Actions run asynchronously - don't block for them
            val totalWait = speechDurationMs + plan.postRollMs
            Log.d(TAG, "Waiting ${totalWait}ms for speech+action to complete")
            delay(totalWait)

            // Reset to neutral after speech AND action complete
            // But don't stop actions abruptly - just reset expression/lights
            Log.d(TAG, "Post-roll complete, resetting expression/lights (action may still be running)")

            return if (errors.isEmpty()) {
                BehaviorResult.Success
            } else {
                BehaviorResult.PartialSuccess(errors)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Behavior execution cancelled")
            resetToNeutral()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Behavior execution failed", e)
            resetToNeutral()
            return BehaviorResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Execute earcon (audio cue)
     */
    fun getEarconForState(state: DialogueState): String? {
        return when (state) {
            DialogueState.LISTENING -> "listening_cue"
            DialogueState.THINKING -> "thinking_cue"
            else -> null
        }
    }

    /**
     * Set expression for dialogue state
     */
    suspend fun setStateExpression(state: DialogueState) {
        val expressionId = when (state) {
            DialogueState.IDLE -> "normal_1"
            DialogueState.LISTENING -> EXPRESSION_MAP["listening"]
            DialogueState.CAPTURING -> EXPRESSION_MAP["listening"]
            DialogueState.THINKING -> EXPRESSION_MAP["thinking"]
            DialogueState.SPEAKING -> EXPRESSION_MAP["speaking"]
            DialogueState.LISTENING_FOR_FOLLOWUP -> EXPRESSION_MAP["listening"]
            DialogueState.CONVERSATION_END -> "normal_1"
            DialogueState.ERROR -> EXPRESSION_MAP["sorry"]
        }
        expressionId?.let { startExpression(it) }
    }

    /**
     * Set light for dialogue state
     */
    suspend fun setStateLight(state: DialogueState) {
        val mode = when (state) {
            DialogueState.IDLE -> LightMode.NORMAL
            DialogueState.LISTENING -> LightMode.LISTENING
            DialogueState.CAPTURING -> LightMode.LISTENING
            DialogueState.THINKING -> LightMode.THINKING
            DialogueState.SPEAKING -> LightMode.SPEAKING
            DialogueState.LISTENING_FOR_FOLLOWUP -> LightMode.LISTENING
            DialogueState.CONVERSATION_END -> LightMode.NORMAL
            DialogueState.ERROR -> LightMode.ERROR
        }
        setLight(BehaviorPlan.LightConfig(mode.name, mode.colorHex))
    }

    /**
     * Stop all behaviors immediately (for barge-in)
     */
    fun stopAllBehaviors() {
        Log.d(TAG, "Stopping all behaviors")
        try {
            expressApi?.stopExpression()
            actionApi?.stopAction()
            lightApi?.setLight(LightMode.NORMAL.colorHex)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping behaviors", e)
        }
    }

    /**
     * Reset to neutral state (expression and lights only, not actions)
     * Actions are allowed to complete on their own
     */
    private fun resetToNeutral() {
        try {
            expressApi?.playExpression("normal_1")
            lightApi?.setLight(LightMode.NORMAL.colorHex)
            // NOTE: We intentionally don't stop actions here to let them complete
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting to neutral", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ROBOT API CALLS (Placeholder implementations)
    // ═══════════════════════════════════════════════════════════════

    private suspend fun startExpression(expressionId: String) {
        Log.d(TAG, "Starting expression: $expressionId")
        withContext(Dispatchers.Main) {
            expressApi?.playExpression(expressionId)
        }
    }

    private suspend fun startAction(actionId: String) {
        Log.d(TAG, "=== startAction() called ===")
        Log.d(TAG, "  actionId: $actionId")
        Log.d(TAG, "  actionApi: ${if (actionApi != null) "NOT NULL" else "NULL!"}")
        withContext(Dispatchers.Main) {
            if (actionApi != null) {
                Log.d(TAG, ">>> Calling actionApi.playAction($actionId)")
                actionApi?.playAction(actionId)
                Log.d(TAG, ">>> actionApi.playAction() called successfully")
            } else {
                Log.e(TAG, "!!! actionApi is NULL - cannot execute action!")
            }
        }
    }

    private suspend fun setLight(config: BehaviorPlan.LightConfig) {
        Log.d(TAG, "Setting light: ${config.mode}")
        withContext(Dispatchers.Main) {
            config.color?.let { lightApi?.setLight(it) }
        }
    }

    fun release() {
        scope.cancel()
    }
}

// ═══════════════════════════════════════════════════════════════
// PLACEHOLDER INTERFACES
// Replace these with actual robot SDK API calls
// ═══════════════════════════════════════════════════════════════

interface ExpressApiPlaceholder {
    fun playExpression(expressionId: String)
    fun stopExpression()
}

interface ActionApiPlaceholder {
    fun playAction(actionId: String)
    fun stopAction()
}

interface LightApiPlaceholder {
    fun setLight(colorHex: Int)
    fun setLightMode(mode: String)
}

/**
 * Factory to create real API implementations
 */
object RobotApiFactory {
    /**
     * Create ExpressApi wrapper
     */
    fun createExpressApi(): ExpressApiPlaceholder? {
        return try {
            object : ExpressApiPlaceholder {
                override fun playExpression(expressionId: String) {
                    try {
                        com.ubtrobot.express.ExpressApi.get()
                            .doExpress(expressionId, 1, false, com.ubtrobot.master.component.ResourcePolicy.GiveUp, null)
                    } catch (e: Exception) {
                        Log.w("RobotApiFactory", "Expression playback failed", e)
                    }
                }
                override fun stopExpression() {
                    try {
                        com.ubtrobot.express.ExpressApi.get().stopExpress()
                    } catch (e: Exception) {
                        Log.w("RobotApiFactory", "Expression stop failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("RobotApiFactory", "ExpressApi not available", e)
            null
        }
    }

    /**
     * Create ActionApi wrapper
     */
    fun createActionApi(): ActionApiPlaceholder? {
        return try {
            object : ActionApiPlaceholder {
                override fun playAction(actionId: String) {
                    try {
                        Log.d("RobotApiFactory", ">>> EXECUTING ACTION: $actionId")
                        com.ubtrobot.action.ActionApi.get()
                            .playAction(actionId, com.ubtrobot.master.component.ResourcePolicy.Exclusive,
                                object : com.ubtrobot.commons.ResponseListener<Void> {
                                    override fun onResponseSuccess(result: Void?) {
                                        Log.d("RobotApiFactory", "Action $actionId completed successfully")
                                    }
                                    override fun onFailure(code: Int, message: String) {
                                        Log.e("RobotApiFactory", "Action $actionId failed: $code - $message")
                                    }
                                })
                    } catch (e: Exception) {
                        Log.e("RobotApiFactory", "Action playback exception: ${e.message}", e)
                    }
                }
                override fun stopAction() {
                    try {
                        com.ubtrobot.action.ActionApi.get().stopAction()
                    } catch (e: Exception) {
                        Log.w("RobotApiFactory", "Action stop failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("RobotApiFactory", "ActionApi not available", e)
            null
        }
    }

    /**
     * Create LightApi wrapper
     */
    fun createLightApi(): LightApiPlaceholder? {
        return try {
            object : LightApiPlaceholder {
                override fun setLight(colorHex: Int) {
                    try {
                        val r = (colorHex shr 16) and 0xFF
                        val g = (colorHex shr 8) and 0xFF
                        val b = colorHex and 0xFF
                        val ledList = listOf(0, 1, 2, 3, 4, 5, 6, 7)
                        com.ubtrobot.led.LightApi.getInstance()
                            .normalEffect(ledList, com.ubtrobot.led.ColorUtil.rgbColor(r, g, b), 0, false)
                    } catch (e: Exception) {
                        Log.w("RobotApiFactory", "Light set failed", e)
                    }
                }
                override fun setLightMode(mode: String) {
                    try {
                        val ledList = listOf(0, 1, 2, 3, 4, 5, 6, 7)
                        when (mode.lowercase()) {
                            "breath", "breathing" -> {
                                com.ubtrobot.led.LightApi.getInstance()
                                    .breathEffect(ledList, com.ubtrobot.led.ColorUtil.rgbColor(20, 20, 20), 1600, 5000, false)
                            }
                            "off" -> {
                                com.ubtrobot.led.LightApi.getInstance().lightOffChest()
                            }
                            else -> {
                                com.ubtrobot.led.LightApi.getInstance()
                                    .normalEffect(ledList, com.ubtrobot.led.ColorUtil.rgbColor(30, 30, 30), 0, false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("RobotApiFactory", "Light mode set failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("RobotApiFactory", "LightApi not available", e)
            null
        }
    }
}
