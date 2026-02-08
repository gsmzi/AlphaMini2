package com.ubtrobot.mini.sdkdemo.voicedialogue

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ubtrobot.mini.sdkdemo.R
import com.ubtrobot.mini.sdkdemo.BuildConfig
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * CONTINUOUS VOICE DIALOGUE ACTIVITY (V3)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Full multi-turn conversation UI with:
 * - Visual state indicator
 * - Conversation history display
 * - Session metrics
 * - Barge-in button
 * - Manual wakeup
 * - Language selection
 * - Configuration options
 */
class VoiceDialogueActivityV3 : AppCompatActivity(), ContinuousOrchestratorListener {

    companion object {
        private const val TAG = "VoiceDialogueV3"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // UI Components
    private lateinit var stateIndicator: View
    private lateinit var stateText: TextView
    private lateinit var turnCounter: TextView
    private lateinit var conversationHistory: TextView
    private lateinit var transcriptionText: TextView
    private lateinit var responseText: TextView
    private lateinit var metricsText: TextView
    private lateinit var wakeupButton: Button
    private lateinit var bargeInButton: Button
    private lateinit var stopButton: Button
    private lateinit var languageSpinner: Spinner
    private lateinit var sessionStatus: TextView
    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView

    // Orchestrator
    private var orchestrator: ContinuousSpeechOrchestrator? = null

    // Configuration
    private var currentLanguage = DialogueConfig.Language.EN
    private var isFirstLanguageSelection = true

    // Conversation history
    private val historyBuilder = StringBuilder()
    private var turnCount = 0

    // HTTP client for server check
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryTs = System.currentTimeMillis()
        Log.d(TAG, "ENTRYPOINT V3 $entryTs buildType=${BuildConfig.BUILD_TYPE} version=${BuildConfig.VERSION_NAME} appId=${BuildConfig.APPLICATION_ID} gitHash=${BuildConfig.GIT_HASH}")
        setContentView(R.layout.activity_voice_dialogue_v3)

        initializeViews()
        setupLanguageSpinner()
        checkServersAndPermissions()
    }

    private fun initializeViews() {
        stateIndicator = findViewById(R.id.stateIndicator)
        stateText = findViewById(R.id.stateText)
        turnCounter = findViewById(R.id.turnCounter)
        conversationHistory = findViewById(R.id.conversationHistory)
        transcriptionText = findViewById(R.id.transcriptionText)
        responseText = findViewById(R.id.responseText)
        metricsText = findViewById(R.id.metricsText)
        wakeupButton = findViewById(R.id.wakeupButton)
        bargeInButton = findViewById(R.id.bargeInButton)
        stopButton = findViewById(R.id.stopButton)
        languageSpinner = findViewById(R.id.languageSpinner)
        sessionStatus = findViewById(R.id.sessionStatus)
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)

        // Back button - return to main menu
        backButton.setOnClickListener {
            finish()
        }

        // Button listeners
        wakeupButton.setOnClickListener {
            orchestrator?.triggerManualWakeup()
        }

        bargeInButton.setOnClickListener {
            orchestrator?.triggerBargeIn()
        }

        stopButton.setOnClickListener {
            orchestrator?.stop()
            updateSessionStatus("Stopped by user")
        }

        // Initial state
        updateStateUI(ContinuousState.IDLE)
    }

    private fun setupLanguageSpinner() {
        val languages = arrayOf("English", "Deutsch")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newLanguage = if (position == 1) {
                    DialogueConfig.Language.DE
                } else {
                    DialogueConfig.Language.EN
                }

                // Skip restart on initial setup or if language hasn't changed
                if (isFirstLanguageSelection) {
                    isFirstLanguageSelection = false
                    currentLanguage = newLanguage
                    return
                }

                if (newLanguage != currentLanguage) {
                    currentLanguage = newLanguage
                    restartOrchestrator()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun checkServersAndPermissions() {
        // When using local processing, skip server checks entirely
        val config = ContinuousDialogueConfig(language = currentLanguage)
        if (config.useLocalProcessing) {
            updateSessionStatus("Local processing mode - no servers needed")
            checkPermissions()
            return
        }

        updateSessionStatus("Checking servers...")

        lifecycleScope.launch {
            val ttsOk = checkServer("http://127.0.0.1:5000/health")
            val llmOk = checkServer("http://127.0.0.1:8080/health")

            if (!ttsOk || !llmOk) {
                val missing = mutableListOf<String>()
                if (!ttsOk) missing.add("TTS (port 5000)")
                if (!llmOk) missing.add("LLM (port 8080)")

                updateSessionStatus("Servers offline: ${missing.joinToString(", ")}")
                Toast.makeText(
                    this@VoiceDialogueActivityV3,
                    "Please start servers first:\n- TTS: python tts_server/server.py\n- LLM: python llm_server/server.py",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                updateSessionStatus("Servers connected!")
                checkPermissions()
            }
        }
    }

    private suspend fun checkServer(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Server check failed: $url", e)
                false
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initializeOrchestrator()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeOrchestrator()
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeOrchestrator() {
        val config = ContinuousDialogueConfig(
            language = currentLanguage,
            silenceShortPauseMs = 800,
            silenceEndOfUtteranceMs = 1500,
            silenceLongMs = 5000,
            silenceVeryLongMs = 10000,
            maxSessionDurationMs = 5 * 60 * 1000, // 5 minutes
            maxTurnsPerSession = 20,
            bargeInEnabled = true,
            enableVoiceWakeup = true,
            enableButtonWakeup = true,
            wakeWords = listOf("hello wukong", "hi wukong", "wukong")
        )

        Log.d(TAG, "ORCH_FACTORY=ContinuousSpeechOrchestrator")
        orchestrator = ContinuousSpeechOrchestrator(this, config).apply {
            setListener(this@VoiceDialogueActivityV3)
            initialize()
        }

        // Observe state changes
        lifecycleScope.launch {
            orchestrator?.state?.collectLatest { state ->
                updateStateUI(state)
            }
        }

        // Enable mic capture and start listening for wakeup
        orchestrator?.setMicCaptureAllowed(true)
        orchestrator?.start()
        updateSessionStatus("Ready - Say wake word or long-press chest button")
    }

    private fun restartOrchestrator() {
        orchestrator?.release()
        historyBuilder.clear()
        conversationHistory.text = ""
        turnCount = 0
        updateTurnCounter()
        initializeOrchestrator()
    }

    // ═══════════════════════════════════════════════════════════════
    // ORCHESTRATOR LISTENER CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    override fun onStateChanged(state: ContinuousState) {
        runOnUiThread {
            updateStateUI(state)
        }
    }

    override fun onTranscription(text: String) {
        runOnUiThread {
            transcriptionText.text = "You: $text"
            appendToHistory("You", text)
        }
    }

    override fun onResponse(text: String) {
        runOnUiThread {
            responseText.text = "Robot: $text"
            appendToHistory("Robot", text)
            turnCount++
            updateTurnCounter()
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $message", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error: $message")
        }
    }

    override fun onMetrics(metrics: LatencyMetrics) {
        runOnUiThread {
            metricsText.text = """
                Turn ${metrics.turnIndex}
                Endpoint→Audio: ${metrics.endpointToAudioLatency()}ms
                LLM: ${metrics.llmLatency()}ms
                TTS: ${metrics.ttsLatency()}ms
            """.trimIndent()
        }
    }

    override fun onSessionEnded(reason: String, totalTurns: Int) {
        runOnUiThread {
            updateSessionStatus("Session ended: $reason ($totalTurns turns)")
            appendToHistory("SYSTEM", "Session ended: $reason")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UI UPDATES
    // ═══════════════════════════════════════════════════════════════

    private fun updateStateUI(state: ContinuousState) {
        stateText.text = state.name

        // Update indicator color based on state
        val color = when (state) {
            ContinuousState.IDLE -> Color.GRAY
            ContinuousState.WAKEUP -> Color.YELLOW
            ContinuousState.LISTENING -> Color.GREEN
            ContinuousState.CAPTURING -> Color.parseColor("#00FF00") // Bright green
            ContinuousState.THINKING -> Color.BLUE
            ContinuousState.SPEAKING -> Color.CYAN
            ContinuousState.LISTENING_FOR_FOLLOWUP -> Color.parseColor("#90EE90") // Light green
            ContinuousState.PROMPTING -> Color.parseColor("#FFD700") // Gold
            ContinuousState.CONVERSATION_END -> Color.parseColor("#FFA500") // Orange
            ContinuousState.ERROR -> Color.RED
        }
        stateIndicator.setBackgroundColor(color)

        // Enable/disable buttons based on state
        wakeupButton.isEnabled = state in setOf(
            ContinuousState.IDLE,
            ContinuousState.LISTENING_FOR_FOLLOWUP
        )

        bargeInButton.isEnabled = state == ContinuousState.SPEAKING

        stopButton.isEnabled = state != ContinuousState.IDLE

        // Update session status
        when (state) {
            ContinuousState.IDLE -> updateSessionStatus("Idle - Press wakeup to start")
            ContinuousState.LISTENING -> updateSessionStatus("Listening...")
            ContinuousState.CAPTURING -> updateSessionStatus("Recording your speech...")
            ContinuousState.THINKING -> updateSessionStatus("Thinking...")
            ContinuousState.SPEAKING -> updateSessionStatus("Speaking...")
            ContinuousState.LISTENING_FOR_FOLLOWUP -> updateSessionStatus("Waiting for your response...")
            ContinuousState.PROMPTING -> updateSessionStatus("Prompting...")
            ContinuousState.CONVERSATION_END -> updateSessionStatus("Ending conversation...")
            else -> {}
        }
    }

    private fun updateTurnCounter() {
        turnCounter.text = "Turns: $turnCount"
    }

    private fun updateSessionStatus(status: String) {
        sessionStatus.text = status
    }

    private fun appendToHistory(speaker: String, text: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        historyBuilder.append("[$timestamp] $speaker: $text\n")
        conversationHistory.text = historyBuilder.toString()

        // Scroll to bottom
        val scrollView = conversationHistory.parent as? ScrollView
        scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume — enabling mic capture")
        orchestrator?.setMicCaptureAllowed(true)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause — disabling mic capture")
        orchestrator?.setMicCaptureAllowed(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        orchestrator?.release()
        orchestrator = null
    }
}
