package com.eduvark.app

import android.app.Application
import com.eduvark.app.ui.theme.EduResources
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EduVarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EduResources.install(this)
    }
}
