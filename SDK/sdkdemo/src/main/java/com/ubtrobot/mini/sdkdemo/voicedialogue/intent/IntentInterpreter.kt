package com.ubtrobot.mini.sdkdemo.voicedialogue.intent

import android.util.Log
import com.ubtrobot.action.ActionApi
import com.ubtrobot.commons.ResponseListener
import com.ubtrobot.express.ExpressApi
import com.ubtrobot.express.listeners.AnimationListener
import com.ubtrobot.led.ColorUtil
import com.ubtrobot.led.LightApi
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.IntentTag
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.LlmResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Robot behavior plan generated from LLM intent tags
 */
data class BehaviorPlan(
    val expressionId: String? = null,
    val actionId: String? = null,
    val lightPattern: LightPattern? = null,
    val timingCues: List<TimingCue> = emptyList()
)

/**
 * Light pattern for LED control
 */
data class LightPattern(
    val type: LightType,
    val color: Int,
    val duration: Long = 0,
    val breathPeriod: Long = 1600
)

enum class LightType {
    NORMAL,
    BREATH,
    OFF
}

/**
 * Timing cue for synchronized behavior
 */
data class TimingCue(
    val behaviorType: String, // "expression", "action", "light"
    val timing: String,       // "start", "end", "during"
    val delayMs: Long = 0
)

/**
 * Intent Interpreter
 * Parses LLM response intent tags and maps to robot behaviors
 */
class IntentInterpreter {
    companion object {
        private const val TAG = "IntentInterpreter"

        // Expression mappings (emotion -> expression ID)
        private val EXPRESSION_MAP = mapOf(
            "happy" to "emo_007",
            "joy" to "emo_007",
            "smile" to "emo_007",
            "normal" to "normal_1",
            "neutral" to "normal_1",
            "default" to "normal_1",
            "thinking" to "emo_010",
            "confused" to "emo_010",
            "sad" to "emo_003",
            "surprised" to "emo_005",
            "excited" to "emo_008",
            "sleepy" to "emo_004",
            "angry" to "emo_002",
            "love" to "emo_006",
            "listening" to "w_basic_0001_1",
            "processing" to "w_basic_0002_1"
        )

        // Action mappings (action name -> action ID)
        private val ACTION_MAP = mapOf(
            "wave" to "017",
            "hand_up" to "017",
            "greeting" to "017",
            "dance" to "dance_0008",
            "taichi" to "014",
            "tai_chi" to "014",
            "squat" to "031",
            "bow" to "016",
            "nod" to "015",
            "shake_head" to "018",
            "clap" to "019",
            "cheer" to "020",
            "think" to "021",
            "idle" to "011"
        )

        // Light color mappings
        private val LIGHT_COLOR_MAP = mapOf(
            "blue" to ColorUtil.rgbColor(0, 0, 100),
            "green" to ColorUtil.rgbColor(0, 100, 0),
            "red" to ColorUtil.rgbColor(100, 0, 0),
            "yellow" to ColorUtil.rgbColor(100, 100, 0),
            "purple" to ColorUtil.rgbColor(100, 0, 100),
            "cyan" to ColorUtil.rgbColor(0, 100, 100),
            "white" to ColorUtil.rgbColor(100, 100, 100),
            "orange" to ColorUtil.rgbColor(100, 50, 0),
            "pink" to ColorUtil.rgbColor(100, 20, 50),
            "normal" to ColorUtil.rgbColor(30, 30, 30),
            "off" to 0
        )

        // Default fallbacks
        private const val DEFAULT_EXPRESSION = "normal_1"
        private const val DEFAULT_ACTION = "011" // Idle action
    }

    /**
     * Parse LLM response and create behavior plan
     */
    fun parse(response: LlmResponse): BehaviorPlan {
        var expressionId: String? = null
        var actionId: String? = null
        var lightPattern: LightPattern? = null
        val timingCues = mutableListOf<TimingCue>()

        for (tag in response.intentTags) {
            when (tag.type.lowercase()) {
                "emotion", "expression", "face" -> {
                    expressionId = getExpression(tag.value)
                    tag.timing?.let {
                        timingCues.add(TimingCue("expression", it))
                    }
                }
                "action", "motion", "move" -> {
                    actionId = getAction(tag.value)
                    tag.timing?.let {
                        timingCues.add(TimingCue("action", it))
                    }
                }
                "light", "led", "color" -> {
                    lightPattern = getLightPattern(tag.value, tag.duration)
                    tag.timing?.let {
                        timingCues.add(TimingCue("light", it))
                    }
                }
            }
        }

        Log.d(TAG, "Parsed behavior plan: expression=$expressionId, action=$actionId, light=$lightPattern")
        return BehaviorPlan(expressionId, actionId, lightPattern, timingCues)
    }

    /**
     * Execute behavior plan
     */
    suspend fun execute(plan: BehaviorPlan) {
        Log.d(TAG, "Executing behavior plan")

        // Determine timing for each behavior
        val startBehaviors = plan.timingCues.filter { it.timing == "start" }
        val endBehaviors = plan.timingCues.filter { it.timing == "end" }

        // Execute "start" behaviors immediately
        if (startBehaviors.any { it.behaviorType == "expression" } ||
            plan.timingCues.isEmpty() && plan.expressionId != null) {
            plan.expressionId?.let { executeExpression(it) }
        }

        if (startBehaviors.any { it.behaviorType == "light" } ||
            plan.timingCues.isEmpty() && plan.lightPattern != null) {
            plan.lightPattern?.let { executeLight(it) }
        }

        if (startBehaviors.any { it.behaviorType == "action" }) {
            plan.actionId?.let { executeAction(it) }
        }

        // "end" behaviors will be executed after audio playback (handled by controller)
        // Actions without timing default to "end"
        if (plan.timingCues.isEmpty() && plan.actionId != null) {
            // Default: action at end - don't execute now
        } else if (endBehaviors.any { it.behaviorType == "action" }) {
            // Will be executed later
        }
    }

    /**
     * Execute behavior plan at end of speech
     */
    suspend fun executeEndBehaviors(plan: BehaviorPlan) {
        val endBehaviors = plan.timingCues.filter { it.timing == "end" }

        // Execute actions that should happen at the end
        if (endBehaviors.any { it.behaviorType == "action" } ||
            (plan.timingCues.isEmpty() && plan.actionId != null)) {
            plan.actionId?.let { executeAction(it) }
        }

        // Reset expression to normal after a delay
        delay(500)
        executeExpression(DEFAULT_EXPRESSION)
    }

    /**
     * Execute wake-up feedback (expression + light)
     */
    suspend fun executeWakeUpFeedback() {
        Log.d(TAG, "Executing wake-up feedback")
        executeExpression("emo_007") // Happy expression
        executeLightNormal()
    }

    /**
     * Execute listening feedback
     */
    suspend fun executeListeningFeedback() {
        Log.d(TAG, "Executing listening feedback")
        executeExpression("w_basic_0001_1")
        executeLightBreath(ColorUtil.rgbColor(0, 100, 0)) // Green breathing
    }

    /**
     * Execute processing feedback
     */
    suspend fun executeProcessingFeedback() {
        Log.d(TAG, "Executing processing feedback")
        executeExpression("emo_010") // Thinking
        executeLightBreath(ColorUtil.rgbColor(0, 0, 100)) // Blue breathing
    }

    /**
     * Execute idle feedback (return to normal)
     */
    suspend fun executeIdleFeedback() {
        Log.d(TAG, "Executing idle feedback")
        executeExpression(DEFAULT_EXPRESSION)
        executeLightNormal()
    }

    /**
     * Map emotion value to expression ID
     */
    private fun getExpression(value: String): String {
        val normalized = value.lowercase().trim()
        return EXPRESSION_MAP[normalized] ?: run {
            Log.w(TAG, "Unknown emotion '$value', using default expression")
            DEFAULT_EXPRESSION
        }
    }

    /**
     * Map action value to action ID
     */
    private fun getAction(value: String): String {
        val normalized = value.lowercase().trim().replace(" ", "_")
        return ACTION_MAP[normalized] ?: run {
            Log.w(TAG, "Unknown action '$value', using default action")
            DEFAULT_ACTION
        }
    }

    /**
     * Create light pattern from value
     */
    private fun getLightPattern(value: String, duration: Long?): LightPattern {
        val normalized = value.lowercase().trim()

        // Check for pattern modifiers
        return when {
            normalized.contains("breath") -> {
                val colorName = normalized.replace("breath", "").trim()
                val color = LIGHT_COLOR_MAP[colorName] ?: LIGHT_COLOR_MAP["normal"]!!
                LightPattern(LightType.BREATH, color, duration ?: 5000)
            }
            normalized == "off" -> {
                LightPattern(LightType.OFF, 0)
            }
            else -> {
                val color = LIGHT_COLOR_MAP[normalized] ?: LIGHT_COLOR_MAP["normal"]!!
                LightPattern(LightType.NORMAL, color, duration ?: 0)
            }
        }
    }

    /**
     * Execute expression on robot
     */
    private suspend fun executeExpression(expressionId: String) {
        try {
            val deferred = CompletableDeferred<Unit>()

            ExpressApi.get().doExpress(
                expressionId,
                1,
                false,
                ResourcePolicy.GiveUp,
                object : AnimationListener {
                    override fun onAnimationStart() {
                        Log.d(TAG, "Expression started: $expressionId")
                    }

                    override fun onAnimationEnd(code: Int) {
                        Log.d(TAG, "Expression ended: $expressionId")
                        deferred.complete(Unit)
                    }

                    override fun onAnimationRepeat(count: Int) {}
                }
            )

            // Wait for animation with timeout
            withTimeoutOrNull(5000) {
                deferred.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute expression: $expressionId", e)
        }
    }

    /**
     * Execute action on robot
     */
    private suspend fun executeAction(actionId: String) {
        try {
            val deferred = CompletableDeferred<Unit>()

            ActionApi.get().playAction(
                actionId,
                ResourcePolicy.Exclusive,
                object : ResponseListener<Void> {
                    override fun onResponseSuccess(result: Void?) {
                        Log.d(TAG, "Action completed: $actionId")
                        deferred.complete(Unit)
                    }

                    override fun onFailure(code: Int, message: String) {
                        Log.e(TAG, "Action failed: $actionId - $message")
                        deferred.complete(Unit)
                    }
                }
            )

            // Wait for action with timeout
            withTimeoutOrNull(30000) {
                deferred.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute action: $actionId", e)
        }
    }

    /**
     * Execute light pattern on robot
     */
    private fun executeLight(pattern: LightPattern) {
        try {
            val ledIds = listOf(0, 1, 2, 3, 4, 5, 6, 7)

            when (pattern.type) {
                LightType.NORMAL -> {
                    LightApi.getInstance().normalEffect(
                        ledIds,
                        pattern.color,
                        pattern.duration.toInt(),
                        false
                    )
                }
                LightType.BREATH -> {
                    LightApi.getInstance().breathEffect(
                        ledIds,
                        pattern.color,
                        pattern.breathPeriod.toInt(),
                        pattern.duration.toInt(),
                        false
                    )
                }
                LightType.OFF -> {
                    LightApi.getInstance().lightOffChest()
                }
            }
            Log.d(TAG, "Light executed: ${pattern.type}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute light", e)
        }
    }

    private fun executeLightNormal() {
        executeLight(LightPattern(LightType.NORMAL, ColorUtil.rgbColor(30, 30, 30)))
    }

    private fun executeLightBreath(color: Int) {
        executeLight(LightPattern(LightType.BREATH, color, 5000))
    }
}
