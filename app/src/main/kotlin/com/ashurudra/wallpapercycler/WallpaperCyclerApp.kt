package com.ashurudra.wallpapercycler

import android.app.Application
import com.ashurudra.wallpapercycler.di.AppContainer

class WallpaperCyclerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
