package com.ctrlcafe.scrapp.util

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class NivelLog { INFO, WARN, ERROR }

object Logger {
    private val logDir = File("logs")
    private val logFile = File(logDir, "errores.log")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
    }

    @Synchronized
    fun registrar(nivel: NivelLog, origen: Class<*>, mensaje: String, throwable: Throwable? = null) {
        val timestamp = LocalDateTime.now().format(formatter)
        val traza = throwable?.let { " | Excepción: ${it.message}" } ?: ""
        val linea = "[$timestamp] [${nivel.name}] [${origen.simpleName}] - $mensaje$traza\n"

        try {
            logFile.appendText(linea)
        } catch (e: Exception) {
            println("Error crítico al escribir en log: ${e.message}")
        }
    }

    fun error(origen: Class<*>, mensaje: String, throwable: Throwable? = null) =
        registrar(NivelLog.ERROR, origen, mensaje, throwable)

    fun info(origen: Class<*>, mensaje: String) =
        registrar(NivelLog.INFO, origen, mensaje)
}