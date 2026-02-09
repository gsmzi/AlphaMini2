package com.ubtrobot.mini.sdkdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueService
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueActivityV3

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_SPEAK_TEST = "com.ubtrobot.mini.sdkdemo.SPEAK_TEST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "BOOT_COMPLETED received; starting VoiceDialogueService")
                val serviceIntent = Intent(context, VoiceDialogueService::class.java)
                serviceIntent.putExtra("boot_time_ms", System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            ACTION_SPEAK_TEST -> {
                // Route to the Activity which has the shared TTS engine
                val text = intent.getStringExtra("text") ?: "Hello, I am Wukong."
                Log.d(TAG, "SPEAK_TEST received: text='$text' — routing to Activity")
                val activityIntent = Intent(context, VoiceDialogueActivityV3::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("speak_test_text", text)
                }
                context.startActivity(activityIntent)
            }
        }
    }
}
