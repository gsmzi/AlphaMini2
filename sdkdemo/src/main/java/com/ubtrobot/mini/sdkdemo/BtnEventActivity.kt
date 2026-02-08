package com.ubtrobot.mini.sdkdemo

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ubtech.logsdk.LogUtils
import com.ubtrobot.masterevent.protos.SysMasterEvent
import com.ubtrobot.mini.sdkdemo.databinding.ActivityBtnEventBinding
import com.ubtrobot.mini.sdkdemo.databinding.ActivityExpressBinding
import com.ubtrobot.mini.sysevent.SysEventApi
import com.ubtrobot.mini.sysevent.event.ChestEvent
import com.ubtrobot.mini.sysevent.event.HeadEvent
import com.ubtrobot.mini.sysevent.event.PowerButtonEvent
import com.ubtrobot.mini.sysevent.event.VolumeEvent
import com.ubtrobot.mini.sysevent.event.base.KeyEvent
import com.ubtrobot.mini.sysevent.receiver.KeyEventReceiver

class BtnEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBtnEventBinding
     companion object {
        private const val TAG = "BtnEventActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBtnEventBinding.inflate(layoutInflater).apply {
            setContentView(root)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "按钮事件"
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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

    internal class CustomKeyEventReceiver(eventName: String,binding: ActivityBtnEventBinding) : KeyEventReceiver() {
              var eventName: String? = eventName
               var binding: ActivityBtnEventBinding = binding
        override fun onSingleClick(event: KeyEvent?): Boolean {
               binding.tvEventLog.text   = "onSingleClick=======$eventName"
                LogUtils.d(TAG, "onSingleClick=======$eventName")
                return true
            }

            override fun onDoubleClick(event: KeyEvent?): Boolean {
                binding.tvEventLog.text   = "onDoubleClick=======$eventName"
                LogUtils.d(TAG, "onDoubleClick=======$eventName")
                return true
            }

            override fun onLongClick(event: KeyEvent?): Boolean {
                  binding.tvEventLog.text   = "onLongClick=======$eventName"
                  LogUtils.d(TAG, "onLongClick=======$eventName")
                return true
            }

                }

    fun receiveBtnEvent(view: View) {
        when(view.id) {
            R.id.btn_head ->{
                SysEventApi.get().subscribe(HeadEvent.newInstance().setPriority(SysMasterEvent.Priority.NORMAL),
                    CustomKeyEventReceiver("head event",binding))
            }
            R.id.btn_chest -> {
                 SysEventApi.get().subscribe(ChestEvent.newInstance().setPriority(SysMasterEvent.Priority.NORMAL),
                     CustomKeyEventReceiver("chest event",binding))
            }
            R.id.btn_volume -> {
                 SysEventApi.get().subscribe(VolumeEvent.newInstance().setPriority(SysMasterEvent.Priority.NORMAL),
                     CustomKeyEventReceiver("volume event",binding))
            }
            R.id.btn_power -> {
                 SysEventApi.get().subscribe(PowerButtonEvent.newInstance().setPriority(SysMasterEvent.Priority.NORMAL),
                     CustomKeyEventReceiver("power event",binding))
            }

        }

    }
}