package com.ubtrobot.mini.sdkdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ubtech.logsdk.LogUtils
import com.ubtrobot.api.PirManager
import com.ubtrobot.api.PreProcessedRecorder
import com.ubtrobot.led.ColorUtil
import com.ubtrobot.led.LightApi
import com.ubtrobot.mini.sdkdemo.databinding.ActivityMainBinding
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueActivity
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueActivityV2
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueActivityV3
import com.ubtrobot.recorder.AudioRecordListener
import com.ubtrobot.recorder.AudioRecorder
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var edgeTTS: EdgeTTS

    companion object {
        // TODO: Set your PC's IP address where the Python TTS server is running
        // Find your IP: Windows: ipconfig | Mac/Linux: ifconfig
        // The robot and PC must be on the same WiFi network
        private const val TTS_SERVER_URL = "http://127.0.0.1:5000"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Edge TTS client
        edgeTTS = EdgeTTS(this, TTS_SERVER_URL)

        // Check server connection
        lifecycleScope.launch {
            if (edgeTTS.isServerAvailable()) {
                Toast.makeText(this@MainActivity, "TTS Server connected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "TTS Server not available. Check IP and start server.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        if (::edgeTTS.isInitialized) {
            edgeTTS.release()
        }
        super.onDestroy()
    }

    // Helper function to speak using Edge TTS
    private fun speak(text: String, languageCode: String = "en-US") {
        lifecycleScope.launch {
            edgeTTS.speak(
                text = text,
                languageCode = languageCode,
                onComplete = {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Speech completed", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    fun initRecorder(view: View) {
        PreProcessedRecorder.init(this@MainActivity.applicationContext)
    }

    fun startRecorder(view: View) {
        if (!PreProcessedRecorder.start()) {
            Toast.makeText(this@MainActivity, "Please initialize first", Toast.LENGTH_SHORT).show()
        } else {
            PreProcessedRecorder.registerRecordListener(audioRecordListener, null, null, AudioRecorder.Type.FOR_WAKEUP)
            binding.tvRecordMsg.text = "Recording started"
            Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecorder(view: View) {
        PreProcessedRecorder.unregisterRecordListener(audioRecordListener, AudioRecorder.Type.FOR_WAKEUP)
        if (!PreProcessedRecorder.stop()) {
            Toast.makeText(this@MainActivity, "Please initialize first", Toast.LENGTH_SHORT).show()
        } else {
            binding.tvRecordMsg.text = "Recording stopped"
        }
    }

    private val audioRecordListener =
        AudioRecordListener { data, length ->
            binding.tvRecordMsg.text = "Recording... data length=$length"
            LogUtils.d("MainActivity", "Recording...")
        }

    fun openPir(view: View) {
        PirManager.openPir()
    }

    fun closePir(view: View) {
        PirManager.closePir()
    }

    fun registerPirEvent(view: View) {
        PirManager.subscribe(pirListener)
    }

    fun unRegisterPirEvent(view: View) {
        PirManager.unSubscribe(pirListener)
    }

    private val pirListener: (Int) -> Unit = { value ->
        binding.tvPirResultMsg.text = "pir value=======$value"
        Log.d(tag, "value=======$value")
    }

    fun isPirOpen(view: View) {
        binding.tvPirResultMsg.text = "isPirOpen=======${PirManager.isPirOpen()}"
        LogUtils.d("", "isPirOpen=======" + PirManager.isPirOpen())
    }

    fun ledNormal(view: View) {
        val list = listOf(0, 1, 2, 3, 4, 5, 6, 7)
        LightApi.getInstance().normalEffect(list, ColorUtil.rgbColor(30, 30, 30), 0, false)
    }

    fun ledGreen(view: View) {
        val list = listOf(0, 1, 2, 3, 4, 5, 6, 7)
        LightApi.getInstance().normalEffect(list, ColorUtil.rgbColor(0, 100, 0), 0, false)
    }

    fun ledBlue(view: View) {
        val list = listOf(0, 1, 2, 3, 4, 5, 6, 7)
        LightApi.getInstance().normalEffect(list, ColorUtil.rgbColor(0, 0, 100), 0, false)
    }

    fun ledRed(view: View) {
        val list = listOf(0, 1, 2, 3, 4, 5, 6, 7)
        LightApi.getInstance().normalEffect(list, ColorUtil.rgbColor(100, 0, 0), 0, false)
    }

    fun ledBreath(view: View) {
        val list = listOf(0, 1, 2, 3)
        LightApi.getInstance().breathEffect(list, ColorUtil.rgbColor(20, 20, 20), 1600, 5000, false)
    }

    fun lightOff(view: View) {
        LightApi.getInstance().lightOffChest()
    }

    fun mouthOFF(view: View) {
        LightApi.getInstance().mouthOff()
    }

    fun mouthNormal(view: View) {
        LightApi.getInstance().mouthOn(127)
    }

    fun mouthBreathEffect(view: View) {
        LightApi.getInstance().mouthEffect(255, 1000, 1000, -1)
    }

    fun MouthMaximumBrightness(view: View) {
        LightApi.getInstance().mouthOn(255)
    }

    fun toExpressPage(view: View) {
        startActivity(Intent(this, ExpressActivity::class.java))
    }

    fun toActionControlPage(view: View) {
        startActivity(Intent(this, ActionActivity::class.java))
    }

    fun toBtnEventPage(view: View) {
        startActivity(Intent(this, BtnEventActivity::class.java))
    }

    fun toMotorControlPage(view: View) {
        startActivity(Intent(this, MotorActivity::class.java))
    }

    fun toRobotStatusPage(view: View) {
        startActivity(Intent(this, SysStatusActivity::class.java))
    }

    fun toVoiceDialoguePage(view: View) {
        startActivity(Intent(this, VoiceDialogueActivity::class.java))
    }

    fun toVoiceDialogueV2Page(view: View) {
        startActivity(Intent(this, VoiceDialogueActivityV2::class.java))
    }

    fun toVoiceDialogueV3Page(view: View) {
        startActivity(Intent(this, VoiceDialogueActivityV3::class.java))
    }

    // English Text-to-Speech functions (using Edge TTS)
    fun speakHello(view: View) {
        speak("Hello! I am your robot friend.", "en-US")
    }

    fun speakGreeting(view: View) {
        speak("Nice to meet you! How can I help you today?", "en-US")
    }

    fun speakDance(view: View) {
        speak("Let me show you my dance moves!", "en-US")
    }

    fun stopSpeech(view: View) {
        edgeTTS.stopSpeaking()
        Toast.makeText(this, "Speech stopped", Toast.LENGTH_SHORT).show()
    }

    // German Text-to-Speech functions (using Edge TTS)
    fun speakHelloGerman(view: View) {
        speak("Hallo! Ich bin dein Roboterfreund.", "de-DE")
    }

    fun speakGreetingGerman(view: View) {
        speak("Freut mich, dich kennenzulernen! Wie kann ich dir heute helfen?", "de-DE")
    }

    fun speakDanceGerman(view: View) {
        speak("Lass mich dir meine Tanzbewegungen zeigen!", "de-DE")
    }

    fun speakGoodbyeGerman(view: View) {
        speak("Auf Wiedersehen! Es war schön, mit dir zu sprechen.", "de-DE")
    }
}
