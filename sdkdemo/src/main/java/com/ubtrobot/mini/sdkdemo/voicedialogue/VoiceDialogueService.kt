package com.ubtrobot.mini.sdkdemo.voicedialogue

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ubtrobot.mini.sdkdemo.R
import com.ubtrobot.mini.sdkdemo.BuildConfig
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.DialogueConfig
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.EmbeddedTtsEngine
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.PcmAudioPlayer
import kotlinx.coroutines.runBlocking

class VoiceDialogueService : Service() {
    companion object {
        private const val CHANNEL_ID = "voice_dialogue_service"
        private const val NOTIF_ID = 1001
        private const val PREFS_NAME = "voice_dialogue_prefs"
        private const val KEY_LAST_BOOT_MS = "last_boot_ms"
        private const val TAG = "VoiceDialogueService"
    }

    private var ttsEngine: EmbeddedTtsEngine? = null
    private var pcmPlayer: PcmAudioPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val entryTs = System.currentTimeMillis()
        Log.d(TAG, "ENTRYPOINT SERVICE $entryTs buildType=${BuildConfig.BUILD_TYPE} version=${BuildConfig.VERSION_NAME} appId=${BuildConfig.APPLICATION_ID} gitHash=${BuildConfig.GIT_HASH}")
        startForeground(NOTIF_ID, createNotification())
        Log.d(TAG, "Service created; initializing TTS")

        pcmPlayer = PcmAudioPlayer()
        ttsEngine = EmbeddedTtsEngine(this).apply {
            val ok = initialize(DialogueConfig.Language.EN)
            Log.d(TAG, "Embedded TTS init ok=$ok (lang=en)")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service start command received")
        val bootTimeMs = intent?.getLongExtra("boot_time_ms", -1L) ?: -1L
        if (bootTimeMs > 0) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastBoot = prefs.getLong(KEY_LAST_BOOT_MS, -1L)
            if (bootTimeMs != lastBoot) {
                prefs.edit().putLong(KEY_LAST_BOOT_MS, bootTimeMs).apply()
                Log.d(TAG, "New boot detected — launching VoiceDialogueActivityV3")
                runBootSpeakTest()
                launchDialogueActivity()
            }
        }
        return START_STICKY
    }

    /**
     * Launch the dialogue Activity as a new task from boot.
     * The Activity owns the orchestrator and gets foreground mic access.
     */
    private fun launchDialogueActivity() {
        try {
            val activityIntent = Intent(this, VoiceDialogueActivityV3::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("from_boot", true)
            }
            startActivity(activityIntent)
            Log.d(TAG, "VoiceDialogueActivityV3 launched from boot service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Activity from boot service", e)
        }
    }

    override fun onDestroy() {
        ttsEngine?.release()
        pcmPlayer?.release()
        ttsEngine = null
        pcmPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Dialogue",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Dialogue")
            .setContentText("Voice service running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun runBootSpeakTest() {
        val engine = ttsEngine ?: return
        val pcm = engine.synthesize("System ready") ?: return
        Thread {
            try {
                runBlockingSafe { pcmPlayer?.playPcmBytes(pcm) }
            } catch (e: Exception) {
                Log.w(TAG, "Boot speak test failed", e)
            }
        }.start()
    }

    private fun runBlockingSafe(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
        } catch (e: Exception) {
            Log.w(TAG, "Boot test runBlocking failed", e)
        }
    }
}
