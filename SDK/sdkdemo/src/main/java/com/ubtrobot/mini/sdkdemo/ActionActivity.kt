package com.ubtrobot.mini.sdkdemo

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ubtech.logsdk.LogUtils
import com.ubtrobot.action.ActionApi
import com.ubtrobot.commons.ResponseListener
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.sdkdemo.databinding.ActivityActionBinding
import com.ubtrobot.transport.message.CallException
import com.ubtrobot.transport.message.Request
import com.ubtrobot.transport.message.Response
import com.ubtrobot.transport.message.ResponseCallback
import ubtechinc.com.standupsdk.StandUpApi

class ActionActivity : AppCompatActivity() {
     private lateinit var binding: ActivityActionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionBinding.inflate(layoutInflater).apply {
            setContentView(root)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
             supportActionBar?.title = "运动控制"
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

    fun actionTaiji(view: View) {
        ActionApi.get().playAction("014", ResourcePolicy.Exclusive, object : ResponseListener<Void> {
            override fun onResponseSuccess(aVoid: Void?) {
                LogUtils.d("squareAction onActionFinished")
            }

            override fun onFailure(i: Int, s: String) {
                LogUtils.d("squareAction onActionFailure,msg = $s")
            }
        })
    }
    fun actionHandUp(view: View) {
        ActionApi.get().playAction("017", ResourcePolicy.Exclusive, object : ResponseListener<Void> {
            override fun onResponseSuccess(aVoid: Void?) {
                LogUtils.d("squareAction onActionFinished")
            }

            override fun onFailure(i: Int, s: String) {
                LogUtils.d("squareAction onActionFailure,msg = $s")
            }
        })
    }
    fun actionSquat(view: View) {
        ActionApi.get().playAction("031", ResourcePolicy.Exclusive, null)

    }
    fun actionDone(view: View) {
         val actionName = binding.etActionName.text.toString()
        if(actionName.isEmpty()) {
            binding.etActionName.error = "请输入动作名称(如): dance_0008"
            return
        }
        ActionApi.get().playAction(actionName, ResourcePolicy.Exclusive, object : ResponseListener<Void> {
            override fun onResponseSuccess(aVoid: Void?) {
                LogUtils.d("squareAction onActionFinished")
            }

            override fun onFailure(i: Int, s: String) {
              LogUtils.d("squareAction onActionFailure,msg = $s")
            }
   })

    }


    fun actionStop(view: View) {
        ActionApi.get().stopAction()

    }
    fun actionResect(view: View) {
        StandUpApi.getInstance().resetIsNotHead(object:ResponseCallback{

            override fun onResponse(p0: Request?, p1: Response?) {
                LogUtils.d("squareAction onActionFinished")
            }

            override fun onFailure(p0: Request?, p1: CallException?) {
                LogUtils.d("squareAction onActionFailure")
            }
        })

    }

    fun actionList(view: View) {
        binding.tvActionLog.text = ""
        var index = 0
        for(action in ActionApi.get().actionList) {
            binding.tvActionLog.append("${++index}、 actionName: ${action.toString()}\n")
        }
    }

    fun actionStandup(view: View) {
        StandUpApi.getInstance().standUp(object:ResponseCallback{

            override fun onResponse(p0: Request?, p1: Response?) {
                LogUtils.d("squareAction onActionFinished")
            }

            override fun onFailure(p0: Request?, p1: CallException?) {
                LogUtils.d("squareAction onActionFailure")
            }
        })
    }

    fun customizeActionDone(view: View) {
        val actionName = binding.etCustomizeActionName.text.toString()
        if(actionName.isEmpty()) {
            binding.etCustomizeActionName.error = "请输入动作名称(如): dance_0008"
            return
        }
        ActionApi.get().playCustomizeAction(actionName, ResourcePolicy.Exclusive, object : ResponseListener<Void> {
            override fun onResponseSuccess(aVoid: Void?) {
                LogUtils.d("squareAction onActionFinished")
            }

            override fun onFailure(i: Int, s: String) {
                LogUtils.d("squareAction onActionFailure,msg = $s")
            }
        })
    }

}