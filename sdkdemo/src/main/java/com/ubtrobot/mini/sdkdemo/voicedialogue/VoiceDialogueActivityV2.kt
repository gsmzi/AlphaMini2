package com.ubtrobot.mini.sdkdemo.voicedialogue

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ubtrobot.mini.sdkdemo.R
import com.ubtrobot.mini.sdkdemo.databinding.ActivityVoiceDialogueBinding
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.DialogueConfig
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.DialogueState
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.LLMClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.LatencyMetrics
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.OrchestratorListener
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.SpeechOrchestrator
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.TTSClient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Voice Dialogue Activity V2
 * Uses the new low-latency SpeechOrchestrator
 */
class VoiceDialogueActivityV2 : AppCompatActivity(), OrchestratorListener {
    companion object {
        private const val TAG = "VoiceDialogueActivityV2"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var binding: ActivityVoiceDialogueBinding
    private var orchestrator: SpeechOrchestrator? = null

    // Status colors for V2 states
    private val statusColors = mapOf(
        DialogueState.IDLE to Color.GRAY,
        DialogueState.LISTENING to Color.YELLOW,
        DialogueState.CAPTURING to Color.GREEN,
        DialogueState.THINKING to Color.BLUE,
        DialogueState.SPEAKING to Color.CYAN,
        DialogueState.ERROR to Color.RED
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityVoiceDialogueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Voice Dialogue V2"

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Language mode selection - no-op, applied on start

        // Start button
        binding.btnStart.setOnClickListener {
            startVoiceDialogue()
        }

        // Stop button
        binding.btnStop.setOnClickListener {
            stopVoiceDialogue()
        }

        // Manual wake-up button
        binding.btnManualWakeUp.setOnClickListener {
            Log.d(TAG, "Manual Wake Up button clicked")
            orchestrator?.triggerManualWakeup()
        }

        // Stop recording button - now triggers barge-in
        binding.btnStopRecording.setOnClickListener {
            orchestrator?.triggerBargeIn()
        }

        // Initialize status
        updateStatusUI(DialogueState.IDLE)
    }

    private fun getSelectedLanguage(): DialogueConfig.Language {
        return when (binding.rgLanguage.checkedRadioButtonId) {
            R.id.rbGerman -> DialogueConfig.Language.DE
            else -> DialogueConfig.Language.EN
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Audio permission required for voice dialogue",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startVoiceDialogue() {
        val ttsUrl = binding.etTtsServerUrl.text.toString()
        val llmUrl = binding.etLlmServerUrl.text.toString()
        val language = getSelectedLanguage()

        // Create config
        val config = DialogueConfig(
            language = language,
            ttsServerUrl = ttsUrl,
            llmServerUrl = llmUrl,
            vadSilenceTimeoutMs = 800,      // Fast cutoff
            vadHangoverMs = 300,            // Natural pause handling
            fillerThresholdMs = 800,        // Show filler if slow
            bargeInEnabled = true
        )

        // Create orchestrator
        orchestrator = SpeechOrchestrator(this, config).apply {
            setListener(this@VoiceDialogueActivityV2)
            initialize()
        }

        // Observe state changes
        lifecycleScope.launch {
            orchestrator?.state?.collectLatest { state ->
                updateStatusUI(state)
            }
        }

        // Check server availability
        lifecycleScope.launch {
            val llmClient = LLMClient(config)
            val ttsClient = TTSClient(config)

            val llmAvailable = llmClient.isAvailable()
            val ttsAvailable = ttsClient.isAvailable()

            if (!llmAvailable) {
                Toast.makeText(
                    this@VoiceDialogueActivityV2,
                    "LLM server not available: $llmUrl",
                    Toast.LENGTH_SHORT
                ).show()
            }

            if (!ttsAvailable) {
                Toast.makeText(
                    this@VoiceDialogueActivityV2,
                    "TTS server not available: $ttsUrl",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Start the dialogue system
        orchestrator?.start()

        // Update UI
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
        binding.btnManualWakeUp.isEnabled = true
        binding.etTtsServerUrl.isEnabled = false
        binding.etLlmServerUrl.isEnabled = false
        binding.cbUseMockLlm.isEnabled = false

        Toast.makeText(this, "Voice Dialogue V2 started. Long-press chest or tap Wake Up.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Voice dialogue V2 started with language: $language")
    }

    private fun stopVoiceDialogue() {
        orchestrator?.stop()
        orchestrator?.release()
        orchestrator = null

        // Update UI
        binding.btnStart.isEnabled = true
        binding.btnStop.isEnabled = false
        binding.btnManualWakeUp.isEnabled = false
        binding.btnStopRecording.visibility = View.GONE
        binding.etTtsServerUrl.isEnabled = true
        binding.etLlmServerUrl.isEnabled = true
        binding.cbUseMockLlm.isEnabled = true

        updateStatusUI(DialogueState.IDLE)
        Toast.makeText(this, "Voice dialogue stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Voice dialogue stopped")
    }

    private fun updateStatusUI(state: DialogueState) {
        runOnUiThread {
            // Update status text
            binding.tvStatus.text = state.name

            // Update status indicator color
            val color = statusColors[state] ?: Color.GRAY
            (binding.statusIndicator.background as? GradientDrawable)?.setColor(color)
                ?: binding.statusIndicator.setBackgroundColor(color)

            // Update button visibility based on state
            when (state) {
                DialogueState.CAPTURING -> {
                    binding.btnStopRecording.visibility = View.VISIBLE
                    binding.btnStopRecording.text = "Stop Recording"
                    binding.btnManualWakeUp.isEnabled = false
                }
                DialogueState.SPEAKING -> {
                    binding.btnStopRecording.visibility = View.VISIBLE
                    binding.btnStopRecording.text = "Barge In"
                    binding.btnManualWakeUp.isEnabled = false
                }
                DialogueState.IDLE -> {
                    binding.btnStopRecording.visibility = View.GONE
                    binding.btnManualWakeUp.isEnabled = orchestrator != null
                }
                else -> {
                    binding.btnStopRecording.visibility = View.GONE
                    binding.btnManualWakeUp.isEnabled = false
                }
            }
        }
    }

    // OrchestratorListener implementation

    override fun onStateChanged(state: DialogueState) {
        Log.d(TAG, "State changed: $state")
        updateStatusUI(state)
    }

    override fun onTranscription(text: String) {
        Log.d(TAG, "Transcription: $text")
        runOnUiThread {
            binding.tvTranscription.text = text
        }
    }

    override fun onResponse(text: String) {
        Log.d(TAG, "Response: $text")
        runOnUiThread {
            binding.tvResponse.text = text
        }
    }

    override fun onError(message: String) {
        Log.e(TAG, "Error: $message")
        runOnUiThread {
            Toast.makeText(this, "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMetrics(metrics: LatencyMetrics) {
        Log.d(TAG, "Latency: endpoint→audio = ${metrics.endpointToAudioLatency()}ms, " +
                "LLM = ${metrics.llmLatency()}ms, " +
                "total = ${metrics.totalTurnLatency()}ms")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        orchestrator?.release()
        orchestrator = null
        super.onDestroy()
    }
}
