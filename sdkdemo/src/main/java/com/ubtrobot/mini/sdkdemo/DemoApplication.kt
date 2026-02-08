package com.ubtrobot.mini.sdkdemo

import android.app.Application
import com.ubtrobot.api.WkSdk
import com.ubtrobot.master.Master

class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        //åˆå§‹åŒ–sdk
        WkSdk.init(this)
        Master.initialize(this)
}
}
