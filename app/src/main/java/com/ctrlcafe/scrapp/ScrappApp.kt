package com.ctrlcafe.scrapp

import android.app.Application
import com.ctrlcafe.scrapp.util.Logger

/** Punto de arranque de la app, antes de cualquier Activity. */
class ScrappApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.inicializar(this)
        Logger.info(ScrappApp::class.java, "Inicializando Scrapp (Android)")
    }
}
