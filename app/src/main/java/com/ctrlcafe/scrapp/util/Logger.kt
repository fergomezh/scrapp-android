package com.ctrlcafe.scrapp.util

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class NivelLog { INFO, WARN, ERROR }

/**
 * Versión Android del Logger de la Etapa 2. Conserva la misma API (`registrar`,
 * `error`, `info`) para que los motores copiados compilen sin cambios.
 *
 * Cada entrada va a Logcat y, si ya se llamó a [inicializar], también a
 * `filesDir/logs/errores.log`. Antes de inicializar solo escribe en Logcat.
 */
object Logger {
    private const val TAG = "Scrapp"
    private const val NOMBRE_ARCHIVO = "errores.log"

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Volatile
    private var logFile: File? = null

    /** Se llama una vez desde ScrappApp.onCreate. */
    fun inicializar(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, NOMBRE_ARCHIVO)
    }

    /** Ruta del archivo de log, útil para revisarlo desde el Device Explorer. */
    val archivo: File? get() = logFile

    @Synchronized
    fun registrar(nivel: NivelLog, origen: Class<*>, mensaje: String, throwable: Throwable? = null) {
        val texto = "[${origen.simpleName}] $mensaje"
        when (nivel) {
            NivelLog.INFO -> Log.i(TAG, texto, throwable)
            NivelLog.WARN -> Log.w(TAG, texto, throwable)
            NivelLog.ERROR -> Log.e(TAG, texto, throwable)
        }

        val destino = logFile ?: return
        val timestamp = LocalDateTime.now().format(formatter)
        val traza = throwable?.let { " | Excepción: ${it.message}" } ?: ""
        val linea = "[$timestamp] [${nivel.name}] [${origen.simpleName}] - $mensaje$traza\n"

        try {
            destino.appendText(linea)
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico al escribir en log: ${e.message}", e)
        }
    }

    fun error(origen: Class<*>, mensaje: String, throwable: Throwable? = null) =
        registrar(NivelLog.ERROR, origen, mensaje, throwable)

    fun advertencia(origen: Class<*>, mensaje: String, throwable: Throwable? = null) =
        registrar(NivelLog.WARN, origen, mensaje, throwable)

    fun info(origen: Class<*>, mensaje: String) =
        registrar(NivelLog.INFO, origen, mensaje)
}
