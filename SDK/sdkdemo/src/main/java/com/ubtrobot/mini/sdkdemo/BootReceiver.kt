package com.ubtrobot.mini.sdkdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ubtrobot.action.ActionApi
import com.ubtrobot.action.ActionExApi
import com.ubtrobot.action.listeners.ActionExListener
import com.ubtrobot.commons.ResponseListener
import com.ubtrobot.express.ExpressApi
import com.ubtrobot.led.ColorUtil
import com.ubtrobot.led.LightApi
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueService
import com.ubtrobot.mini.sdkdemo.voicedialogue.VoiceDialogueActivityV3
import com.ubtrobot.mini.voice.VoiceListener
import com.ubtrobot.mini.voice.VoicePool
import com.ubtrobot.transport.message.CallException
import com.ubtrobot.transport.message.Request
import com.ubtrobot.transport.message.Response
import com.ubtrobot.transport.message.ResponseCallback
import ubtechinc.com.standupsdk.StandUpApi

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_SPEAK_TEST = "com.ubtrobot.mini.sdkdemo.SPEAK_TEST"
        const val ACTION_WALK = "com.ubtrobot.mini.sdkdemo.WALK"
        const val ACTION_TURN = "com.ubtrobot.mini.sdkdemo.TURN"
        const val ACTION_ROBOT = "com.ubtrobot.mini.sdkdemo.ACTION"
        const val ACTION_STOP = "com.ubtrobot.mini.sdkdemo.ACTION_STOP"
        const val ACTION_EXPRESSION = "com.ubtrobot.mini.sdkdemo.EXPRESSION"
        const val ACTION_LIGHT = "com.ubtrobot.mini.sdkdemo.LIGHT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive: action=$action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "BOOT_COMPLETED received — launching Activity directly + starting Service")
                val activityIntent = Intent(context, VoiceDialogueActivityV3::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("from_boot", true)
                }
                context.startActivity(activityIntent)

                val serviceIntent = Intent(context, VoiceDialogueService::class.java)
                serviceIntent.putExtra("boot_time_ms", System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            ACTION_SPEAK_TEST -> {
                val text = intent.getStringExtra("text") ?: "Hallo"
                Log.d(TAG, "SPEAK_TEST received: text='$text'")
                try {
                    ExpressApi.get().doExpress("emo_008", 1, false, ResourcePolicy.GiveUp, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Error showing speak expression: ${e.message}")
                }
                try {
                    VoicePool.get().playTTs(text, ResourcePolicy.Exclusive, object : VoiceListener {
                        override fun onCompleted() { Log.d(TAG, "playTTs completed") }
                        override fun onError(code: Int, msg: String?) { Log.w(TAG, "playTTs error: $code $msg") }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Error playing TTS: ${e.message}")
                }
            }
            ACTION_WALK -> {
                val direction = intent.getStringExtra("direction") ?: "forward"
                val steps = intent.getIntExtra("steps", 2)
                Log.d(TAG, "WALK received: direction=$direction, steps=$steps")
                val listener = object : ActionExListener {
                    override fun onActonStarted() { Log.d(TAG, "walk started") }
                    override fun onActionProgress(current: Int, total: Int) { Log.d(TAG, "walk progress: $current/$total") }
                    override fun onActionCompleted() { Log.d(TAG, "walk completed") }
                    override fun onActionFailure(code: Int, msg: String) { Log.e(TAG, "walk failure: $code $msg") }
                }
                try {
                    if (direction.equals("backward", ignoreCase = true)) {
                        ActionExApi.get().walkBackward(steps, ResourcePolicy.Exclusive, listener)
                    } else {
                        ActionExApi.get().walkForward(steps, ResourcePolicy.Exclusive, listener)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing walk: ${e.message}", e)
                }
            }
            ACTION_TURN -> {
                val direction = intent.getStringExtra("direction") ?: "left"
                val steps = intent.getIntExtra("steps", 2)
                Log.d(TAG, "TURN received: direction=$direction, steps=$steps")
                val listener = object : ActionExListener {
                    override fun onActonStarted() { Log.d(TAG, "turn started") }
                    override fun onActionProgress(current: Int, total: Int) { Log.d(TAG, "turn progress: $current/$total") }
                    override fun onActionCompleted() { Log.d(TAG, "turn completed") }
                    override fun onActionFailure(code: Int, msg: String) { Log.e(TAG, "turn failure: $code $msg") }
                }
                try {
                    if (direction.equals("right", ignoreCase = true)) {
                        ActionExApi.get().turnRight(steps, ResourcePolicy.Exclusive, listener)
                    } else {
                        ActionExApi.get().turnLeft(steps, ResourcePolicy.Exclusive, listener)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing turn: ${e.message}", e)
                }
            }
            ACTION_ROBOT -> {
                val actId = intent.getStringExtra("action") ?: "010"
                Log.d(TAG, "ACTION received: $actId")
                try {
                    when (actId) {
                        "pressup" -> {
                            val pushUpListener = object : ActionExListener {
                                override fun onActonStarted() { Log.d(TAG, "pressups started") }
                                override fun onActionProgress(current: Int, total: Int) { Log.d(TAG, "pressups progress: $current/$total") }
                                override fun onActionCompleted() { Log.d(TAG, "pressups completed") }
                                override fun onActionFailure(code: Int, msg: String) { Log.e(TAG, "pressups failure: $code $msg") }
                            }
                            ActionExApi.get().makePressUps(3, ResourcePolicy.Exclusive, pushUpListener)
                        }
                        "standup" -> {
                            StandUpApi.getInstance().standUp(object : ResponseCallback {
                                override fun onResponse(p0: Request?, p1: Response?) { Log.d(TAG, "standup success") }
                                override fun onFailure(p0: Request?, p1: CallException?) { Log.e(TAG, "standup failed: ${p1?.message}") }
                            })
                        }
                        else -> {
                            ActionApi.get().playAction(actId, ResourcePolicy.Exclusive, object : ResponseListener<Void> {
                                override fun onResponseSuccess(aVoid: Void?) { Log.d(TAG, "action success: $actId") }
                                override fun onFailure(code: Int, msg: String) { Log.e(TAG, "action failure: $code $msg") }
                            })
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing action: ${e.message}", e)
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                try {
                    ActionApi.get().stopAction()
                    ActionExApi.get().stopWalkForward()
                    ActionExApi.get().stopWalkBackward()
                    ActionExApi.get().stopTurnLeft()
                    ActionExApi.get().stopTurnRight()
                    ActionExApi.get().stopMakePressUps()
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing stop: ${e.message}", e)
                }
            }
            ACTION_EXPRESSION -> {
                val expr = intent.getStringExtra("expression") ?: "emo_007"
                Log.d(TAG, "EXPRESSION received: $expr")
                try {
                    ExpressApi.get().doExpress(expr, 1, false, ResourcePolicy.GiveUp, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing express: ${e.message}", e)
                }
            }
            ACTION_LIGHT -> {
                val color = intent.getStringExtra("color") ?: "green"
                Log.d(TAG, "LIGHT received: $color")
                try {
                    val rgb = when (color.lowercase()) {
                        "blue" -> ColorUtil.rgbColor(0, 180, 255)
                        "red" -> ColorUtil.rgbColor(255, 30, 60)
                        "yellow" -> ColorUtil.rgbColor(255, 200, 0)
                        "purple" -> ColorUtil.rgbColor(180, 20, 200)
                        "white" -> ColorUtil.rgbColor(255, 255, 255)
                        "off" -> ColorUtil.rgbColor(0, 0, 0)
                        else -> ColorUtil.rgbColor(0, 255, 100) // green default
                    }
                    val ids = listOf(0, 1, 2, 3, 4, 5, 6, 7)
                    LightApi.getInstance().normalEffect(ids, rgb, 3000, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting light: ${e.message}", e)
                }
            }
        }
    }
}
