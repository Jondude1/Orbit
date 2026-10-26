package com.jondude1.orbit

import android.app.Application
import com.jondude1.orbit.builder.BuildSetup
import com.jondude1.orbit.data.Settings
import com.jondude1.orbit.data.ThoughtStore
import com.jondude1.orbit.notify.ReviewScheduler

class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        BuildSetup.init()
        ThoughtStore.init(this)
        ReviewScheduler.createChannel(this)
        ReviewScheduler.schedule(this)
    }
}
