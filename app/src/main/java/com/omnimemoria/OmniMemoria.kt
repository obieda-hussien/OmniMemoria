package com.omnimemoria

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.objectbox.BoxStore
import com.omnimemoria.data.local.objectbox.MyObjectBox

@HiltAndroidApp
class OmniMemoria : Application() {
    override fun onCreate() {
        super.onCreate()
        boxStore = MyObjectBox.builder().androidContext(this).build()
    }

    companion object {
        lateinit var boxStore: BoxStore
            private set
    }
}
