package com.ubtrobot.mini.sdkdemo

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ubtech.logsdk.LogUtils
import com.ubtrobot.express.ExpressApi
import com.ubtrobot.express.listeners.AnimationListener
import com.ubtrobot.master.component.ResourcePolicy
import com.ubtrobot.mini.sdkdemo.databinding.ActivityExpressBinding

class ExpressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpressBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityExpressBinding.inflate(layoutInflater).apply {
            setContentView(root)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "表情"
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

    fun expressNormal(view: View) {
        //随机表情
        //  ExpressionManager.execute()
        //默认表情
        doExpress("normal_1", 1, object : AnimationListener{
            override fun onAnimationStart() {

            }

            override fun onAnimationEnd(p0: Int) {
            }

            override fun onAnimationRepeat(p0: Int) {

            }
        })
    }
    fun expressSmile(view: View) {
        doExpress("emo_007", 2, null)
    }
    fun expressEye(view: View) {
        doExpress("codemao20", 2, null)
    }

    private fun doExpress(expressName: String, loopCount: Int = 1, animationListener : AnimationListener? = null) {
        ExpressApi.get().doExpress(expressName, loopCount, false, ResourcePolicy.GiveUp, animationListener)
    }

    fun expressDone(view: View) {
        val expressName = binding.etExpressName.text.toString()
        if(expressName.isEmpty()) {
            binding.etExpressName.error = "请入表情名称(如): emo_010"
            return
        }
        doExpress(expressName, 1, null)

    }

    fun expressList(view: View) {
        binding.tvExpressLog.text = ""
        var index = 0
        for(express in ExpressApi.get().expressList ) {
            binding.tvExpressLog.append("${++index}、 ${express}\n")
        }
    }

    fun customizeExpressDone(view: View) {
        val expressName = binding.etCustomizeExpressName.text.toString()
        if(expressName.isEmpty()) {
            binding.etCustomizeExpressName.error = "请入表情名称(如): emo_010"
            return
        }
        ExpressApi.get().doCustomizeExpress(expressName, 1, false, ResourcePolicy.GiveUp, object:
            AnimationListener {
            override fun onAnimationStart() {
               LogUtils.d("express onAnimationStart")
            }

            override fun onAnimationEnd(p0: Int) {
                LogUtils.d("express onAnimationEnd")
            }

            override fun onAnimationRepeat(p0: Int) {
                LogUtils.d("express onAnimationRepeat")
            }

        })
    }
}