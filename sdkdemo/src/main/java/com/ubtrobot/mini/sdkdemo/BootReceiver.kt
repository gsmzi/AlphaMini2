package com.ubtrobot.mini.sdkdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueService
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.EmbeddedTtsEngine
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.DialogueConfig
import com.ubtrobot.mini.sdkdemo.voicedialogue.v2.PcmAudioPlayer
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val ACTION_SPEAK_TEST = "com.ubtrobot.mini.sdkdemo.SPEAK_TEST"
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
                val text = intent.getStringExtra("text") ?: "Hello, I am Wukong. System is working."
                Log.d(TAG, "SPEAK_TEST received: text='$text'")
                Thread {
                    try {
                        val engine = EmbeddedTtsEngine(context)
                        if (engine.initialize(DialogueConfig.Language.EN)) {
                            val pcm = engine.synthesize(text)
                            if (pcm != null && pcm.isNotEmpty()) {
                                Log.d(TAG, "SPEAK_TEST: synthesized ${pcm.size} bytes, playing...")
                                runBlocking { PcmAudioPlayer().playPcmBytes(pcm) }
                                Log.d(TAG, "SPEAK_TEST: playback complete")
                            } else {
                                Log.e(TAG, "SPEAK_TEST: synthesis returned null/empty")
                            }
                            engine.release()
                        } else {
                            Log.e(TAG, "SPEAK_TEST: TTS engine init failed")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "SPEAK_TEST failed", e)
                    }
                }.start()
            }
        }
    }
}
