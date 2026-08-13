package com.kanshu.reader

import android.app.Application
import com.kanshu.reader.data.AppContainer

class KanshuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
