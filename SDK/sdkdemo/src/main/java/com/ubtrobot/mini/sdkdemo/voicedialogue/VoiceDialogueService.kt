package com.ubtrobot.mini.sdkdemo.voicedialogue

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ubtrobot.mini.sdkdemo.R
import com.ubtrobot.mini.sdkdemo.BuildConfig

/**
 * Foreground service for boot auto-start.
 * Keeps the process alive and launches VoiceDialogueActivityV3.
 * TTS/STT are owned by the Activity's orchestrator (no duplication here).
 */
class VoiceDialogueService : Service() {
    companion object {
        private const val CHANNEL_ID = "voice_dialogue_service"
        private const val CHANNEL_ID_BOOT = "voice_dialogue_boot"
        private const val NOTIF_ID = 1001
        private const val TAG = "VoiceDialogueService"
        private const val INITIAL_LAUNCH_DELAY_MS = 8_000L   // wait for system to settle after boot
        private const val WATCHDOG_INTERVAL_MS      = 8_000L   // re-check every 8 s while service runs
        private const val FULLSCREEN_NOTIF_ID = 1002
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Perpetual watchdog — relaunches the activity whenever it is not alive. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!VoiceDialogueActivityV3.isAlive) {
                Log.w(TAG, "Watchdog: Activity not alive — relaunching")
                launchDialogueActivity()
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val entryTs = System.currentTimeMillis()
        Log.d(TAG, "ENTRYPOINT SERVICE $entryTs buildType=${BuildConfig.BUILD_TYPE} version=${BuildConfig.VERSION_NAME} appId=${BuildConfig.APPLICATION_ID} gitHash=${BuildConfig.GIT_HASH}")
        startForeground(NOTIF_ID, createNotification())
        Log.d(TAG, "Service created (lean — TTS owned by orchestrator)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service start command received")

        // First launch: wait for the system to settle after boot, then let the watchdog take over
        if (!VoiceDialogueActivityV3.isAlive) {
            Log.d(TAG, "Activity not alive — first launch in ${INITIAL_LAUNCH_DELAY_MS}ms")
            handler.postDelayed({ launchDialogueActivity() }, INITIAL_LAUNCH_DELAY_MS)
        }

        // Start perpetual watchdog (removes any previous one to avoid duplicates)
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, INITIAL_LAUNCH_DELAY_MS + WATCHDOG_INTERVAL_MS)

        return START_STICKY
    }

    /**
     * Launch the dialogue Activity using a full-screen intent notification.
     * Android 10+ blocks direct startActivity() from background/foreground services.
     * Full-screen intents bypass this restriction and launch the Activity immediately.
     */
    private fun launchDialogueActivity() {
        try {
            val activityIntent = Intent(this, VoiceDialogueActivityV3::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("from_boot", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_BOOT)
                .setContentTitle("Voice Dialogue Starting")
                .setContentText("Launching voice dialogue...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            manager.notify(FULLSCREEN_NOTIF_ID, notification)
            Log.d(TAG, "Full-screen intent notification fired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Activity", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        handler.removeCallbacksAndMessages(null)
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

            // High-importance channel for boot full-screen intent
            val bootChannel = NotificationChannel(
                CHANNEL_ID_BOOT,
                "Voice Dialogue Boot",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(bootChannel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Dialogue")
            .setContentText("Voice service running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}
