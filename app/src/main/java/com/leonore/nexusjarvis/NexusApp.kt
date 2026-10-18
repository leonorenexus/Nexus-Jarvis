package com.leonore.nexusjarvis

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import com.leonore.nexusjarvis.notifications.NotificationHelper

object AppForeground {
    @Volatile var isForeground: Boolean = false
}

class NexusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { started++; AppForeground.isForeground = true }
            override fun onActivityStopped(activity: Activity) { started--; if (started <= 0) { started = 0; AppForeground.isForeground = false } }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

fun Context.container(): AppContainer = (applicationContext as NexusApp).container
