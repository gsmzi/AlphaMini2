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
import com.ubtrobot.mini.sdkdemo.voicedialogue.audio.DialogueAudioPlayer
import com.ubtrobot.mini.sdkdemo.voicedialogue.audio.DialogueAudioRecorder
import com.ubtrobot.mini.sdkdemo.voicedialogue.intent.BehaviorPlan
import com.ubtrobot.mini.sdkdemo.voicedialogue.intent.IntentInterpreter
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.HttpLlmClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.LlmClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.llm.MockLlmClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.tts.EdgeTtsClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.tts.TtsClient
import com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup.CombinedWakeupDetector
import com.ubtrobot.mini.sdkdemo.voicedialogue.wakeup.WakeUpType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Voice Dialogue Activity
 * Main UI for the voice dialogue system
 */
class VoiceDialogueActivity : AppCompatActivity(), VoiceDialogueListener {
    companion object {
        private const val TAG = "VoiceDialogueActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var binding: ActivityVoiceDialogueBinding
    private var controller: VoiceDialogueController? = null
    private var config: VoiceDialogueConfig = VoiceDialogueConfig()

    // Status colors
    private val statusColors = mapOf(
        DialogueState.IDLE to Color.GRAY,
        DialogueState.WAKING to Color.YELLOW,
        DialogueState.RECORDING to Color.GREEN,
        DialogueState.PROCESSING to Color.BLUE,
        DialogueState.SPEAKING to Color.CYAN,
        DialogueState.ERROR to Color.RED
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityVoiceDialogueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Voice Dialogue"

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Language mode selection
        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val languageMode = when (checkedId) {
                R.id.rbEnglish -> LanguageMode.EN
                R.id.rbGerman -> LanguageMode.DE
                R.id.rbAuto -> LanguageMode.AUTO
                else -> LanguageMode.EN
            }
            updateConfig(languageMode = languageMode)
        }

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
            Log.d(TAG, "Manual Wake Up button clicked, controller=$controller")
            controller?.triggerWakeUp(WakeUpType.BUTTON)
        }

        // Stop recording button
        binding.btnStopRecording.setOnClickListener {
            controller?.stopRecording()
        }

        // Initialize status
        updateStatusUI(DialogueState.IDLE)
    }

    private fun updateConfig(languageMode: LanguageMode? = null) {
        val ttsUrl = binding.etTtsServerUrl.text.toString()
        val llmUrl = binding.etLlmServerUrl.text.toString()

        config = VoiceDialogueConfig(
            languageMode = languageMode ?: config.languageMode,
            ttsServerUrl = ttsUrl,
            llmServerUrl = llmUrl,
            enableButtonWakeUp = true,
            enableVoiceWakeUp = false
        )
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
        updateConfig()

        // Create components
        val wakeUpDetector = CombinedWakeupDetector(
            enableButton = config.enableButtonWakeUp,
            enableVoice = config.enableVoiceWakeUp
        )

        val audioRecorder = DialogueAudioRecorder(
            sampleRate = config.sampleRate,
            use4MicArray = config.use4MicArray,
            silenceTimeoutMs = config.silenceTimeoutMs
        )

        val llmClient: LlmClient = if (binding.cbUseMockLlm.isChecked) {
            MockLlmClient()
        } else {
            HttpLlmClient(config.llmServerUrl)
        }

        val ttsClient: TtsClient = EdgeTtsClient(config.ttsServerUrl)

        val audioPlayer = DialogueAudioPlayer(
            context = this,
            sampleRate = config.sampleRate
        )

        val intentInterpreter = IntentInterpreter()

        // Create controller
        controller = VoiceDialogueController(this, config).apply {
            initialize(
                wakeUpDetector = wakeUpDetector,
                audioRecorder = audioRecorder,
                llmClient = llmClient,
                ttsClient = ttsClient,
                audioPlayer = audioPlayer,
                intentInterpreter = intentInterpreter
            )
            setListener(this@VoiceDialogueActivity)
        }

        // Observe state changes
        lifecycleScope.launch {
            controller?.state?.collectLatest { state ->
                updateStatusUI(state)
            }
        }

        // Check TTS server availability
        lifecycleScope.launch {
            val ttsAvailable = ttsClient.isAvailable()
            if (!ttsAvailable) {
                Toast.makeText(
                    this@VoiceDialogueActivity,
                    "TTS server not available. Check URL: ${config.ttsServerUrl}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Start the dialogue system
        Log.d(TAG, "Starting dialogue controller...")
        controller?.start()
        Log.d(TAG, "Dialogue controller started, controller=$controller")

        // Update UI
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
        binding.btnManualWakeUp.isEnabled = true
        binding.etTtsServerUrl.isEnabled = false
        binding.etLlmServerUrl.isEnabled = false
        binding.cbUseMockLlm.isEnabled = false

        Toast.makeText(this, "Voice dialogue started. Long-press chest button to wake up.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Voice dialogue started")
    }

    private fun stopVoiceDialogue() {
        controller?.stop()
        controller?.release()
        controller = null

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
                DialogueState.RECORDING -> {
                    binding.btnStopRecording.visibility = View.VISIBLE
                    binding.btnManualWakeUp.isEnabled = false
                }
                DialogueState.IDLE -> {
                    binding.btnStopRecording.visibility = View.GONE
                    binding.btnManualWakeUp.isEnabled = controller != null
                }
                else -> {
                    binding.btnStopRecording.visibility = View.GONE
                    binding.btnManualWakeUp.isEnabled = false
                }
            }
        }
    }

    // VoiceDialogueListener implementation

    override fun onStateChanged(oldState: DialogueState, newState: DialogueState) {
        Log.d(TAG, "State changed: $oldState -> $newState")
        updateStatusUI(newState)
    }

    override fun onTranscription(text: String) {
        runOnUiThread {
            binding.tvTranscription.text = text
        }
    }

    override fun onResponse(text: String) {
        runOnUiThread {
            binding.tvResponse.text = text
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBehaviorExecuted(plan: BehaviorPlan) {
        Log.d(TAG, "Behavior executed: $plan")
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
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
