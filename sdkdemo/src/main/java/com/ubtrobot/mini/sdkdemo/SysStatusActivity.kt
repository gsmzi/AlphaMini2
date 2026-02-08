package com.ubtrobot.mini.sdkdemo

import android.app.ApplicationErrorReport
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.ubtech.logsdk.LogUtils
import com.ubtrobot.commons.ResponseListener
import com.ubtrobot.masterevent.protos.SysMasterEvent
import com.ubtrobot.mini.sdkdemo.databinding.ActivitySysStateBinding
import com.ubtrobot.mini.sysevent.SysEventApi
import com.ubtrobot.mini.sysevent.event.AlphaStateEvent
import com.ubtrobot.mini.sysevent.event.BatteryEvent
import com.ubtrobot.mini.sysevent.listener.subscribe.GetBatteryInfoListener
import com.ubtrobot.mini.sysevent.receiver.AlphaStateEventReceiver
import com.ubtrobot.mini.sysevent.receiver.BatteryEventReceiver
import com.ubtrobot.transport.message.CallException

class SysStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySysStateBinding
    private val TAG = "SysStatusActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySysStateBinding.inflate(layoutInflater).apply {
            setContentView(root)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "系统状态"
        }

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

    fun receiveSysEvent(view: View) {
        when(view.id) {
            R.id.btn_register_power_status -> {
                SysEventApi.get().subscribe(
                    BatteryEvent.newInstance(), // Adjust priority as needed
                    batteryEventReceiver
                )
            }
            R.id.btn_unregister_power_status -> {
                SysEventApi.get().unsubscribe(batteryEventReceiver)
            }
            R.id.btn_power_info -> {
                //异步获取
       /*         SysEventApi.get().getCurrentBatteryInfo(
                    object: GetBatteryInfoListener {
                        override fun onSuccess(p0: SysMasterEvent.BatteryStatusData?) {
                            binding.tvSysEventLog.text = p0?.toString()
                        }

                        override fun onFiald(p0: CallException?) {
                            LogUtils.d("SysStatusActivity", "getCurrentBatteryInfo error: ${p0?.message}")
                        }

                    })*/
                //同步获取
               binding.tvSysEventLog.text = SysEventApi.get().currentBatteryInfoSync.toString()
            }
            R.id.btn_is_charging -> {
                binding.tvSysEventLog.text =   if (SysEventApi.get().isCharging) "正在充电" else "未充电"
            }
            R.id.btn_register_status -> {
               SysEventApi.get().subscribe(AlphaStateEvent.instance(), alphaStateEventReceiver )
            }
            R.id.btn_publish_status -> {
                  SysEventApi.getStateApi().publishAlphaStateSync(SysMasterEvent.AlphaState.sleep)
           }
            R.id.btn_unregister_status -> {
                 binding.tvSysStatusLog.text = ""
                 SysEventApi.get().unsubscribe( alphaStateEventReceiver)
           }
        }
    }

    // Example of subscribing to battery events
    val batteryEventReceiver: BatteryEventReceiver = object : BatteryEventReceiver() {
        override fun onReceive(batteryEvent: BatteryEvent): Boolean {
            // Handle battery level change here
            // batteryEvent.level provides the current percentage
            // batteryEvent.status provides the status (e.g., CHARGING, DISCHARGING)
            // batteryEvent.levelStatus might indicate LOW, NORMAL, etc.
            LogUtils.d("BatteryListener", "Battery level: ${batteryEvent.level}, Status: ${batteryEvent.status}")
            binding.tvSysEventLog.text = "Battery level: ${batteryEvent.level}, Status: ${batteryEvent.status}"
            // Return true if the event was handled
            return true
        }
    }
    val alphaStateEventReceiver = object: AlphaStateEventReceiver() {
                    override fun onReceive(p0: AlphaStateEvent?): Boolean {
                        when (p0?.state?.destination) {
                            SysMasterEvent.AlphaState.sleep -> {
                                binding.tvSysStatusLog.text = "alphaStateEventReceiver: sleep"
                                LogUtils.d(TAG, "alphaStateEventReceiver sleep")
                            }
                            SysMasterEvent.AlphaState.working -> {
                                binding.tvSysStatusLog.text = "alphaStateEventReceiver: working"
                            }
                            SysMasterEvent.AlphaState.active -> {
                                binding.tvSysStatusLog.text = "alphaStateEventReceiver: active"
                            }
                            SysMasterEvent.AlphaState.standby -> {
                                binding.tvSysStatusLog.text = "alphaStateEventReceiver: standby"
                                LogUtils.d(TAG, "alphaStateEventReceiver active")
                            }
                            SysMasterEvent.AlphaState.UNRECOGNIZED -> {
                                binding.tvSysStatusLog.text = "alphaStateEventReceiver: UNRECOGNIZED"
                            }
                            null -> TODO()
                        }
                        return false;
                    }
                }

}