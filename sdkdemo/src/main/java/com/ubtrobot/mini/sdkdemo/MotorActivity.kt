package com.ubtrobot.mini.sdkdemo

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ubtrobot.mini.sdkdemo.databinding.ActivityExpressBinding
import com.ubtrobot.mini.sdkdemo.databinding.ActivityMotorBinding
import com.ubtrobot.mini.sdkdemo.databinding.ActivitySysStateBinding

class MotorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMotorBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMotorBinding.inflate(layoutInflater).apply {
            setContentView(root)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "舵机控制"
        }
        setContentView(R.layout.activity_motor)
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
}