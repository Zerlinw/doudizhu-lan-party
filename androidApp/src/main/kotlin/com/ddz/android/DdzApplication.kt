package com.ddz.android

import android.app.Application
import com.ddz.shared.discovery.AndroidPlatform

class DdzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)
    }
}
