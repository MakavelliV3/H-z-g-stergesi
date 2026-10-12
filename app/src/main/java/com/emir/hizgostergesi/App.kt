package com.emir.hizgostergesi

import android.app.Application

/** Uygulama açılırken renkleri (Material You) hazırlar. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Ui.init(this)
    }
}
