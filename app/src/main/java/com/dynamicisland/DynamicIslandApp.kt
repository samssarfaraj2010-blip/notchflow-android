package com.dynamicisland

import android.app.Application
import com.dynamicisland.core.IslandRuntime

class DynamicIslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        IslandRuntime.init(this)
    }
}
