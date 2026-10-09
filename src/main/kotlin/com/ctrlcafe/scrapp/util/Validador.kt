package com.ctrlcafe.scrapp.util

import java.time.LocalDate
import java.time.format.DateTimeParseException

object Validador {

    fun leerTexto(mensaje: String, permitirVacio: Boolean = false): String {
        while (true) {
            print(mensaje)
            val valor = readLine()?.trim() ?: ""
            if (permitirVacio || valor.isNotEmpty()) return valor
            println("Entrada inválida. No puede quedar vacío.")
        }
    }

    fun leerEntero(mensaje: String, rango: IntRange? = null): Int {
        while (true) {
            print(mensaje)
            val valor = readLine()?.trim()
            val numero = valor?.toIntOrNull()

            if (numero != null && (rango == null || numero in rango)) {
                return numero
            }

            val detalle =
                if (rango != null)
                    " entre ${rango.first} y ${rango.last}"
                else
                    ""

            println("Entrada inválida. Ingrese un número entero$detalle.")
        }
    }

    fun leerDecimal(mensaje: String, minimo: Double? = null): Double {
        while (true) {
            print(mensaje)
            val texto = readLine()?.trim()?.replace(',', '.')
            val numero = texto?.toDoubleOrNull()

            if (numero != null && (minimo == null || numero >= minimo)) {
                return numero
            }

            val detalle =
                if (minimo != null)
                    " mayor o igual a $minimo"
                else
                    ""

            println("Entrada inválida. Ingrese un número decimal$detalle.")
        }
    }

    fun leerFecha(mensaje: String): LocalDate {
        while (true) {
            print(mensaje)
            val texto = readLine()?.trim() ?: ""

            try {
                return LocalDate.parse(texto)
            } catch (e: DateTimeParseException) {
                Logger.error(
                    Validador::class.java,
                    "Fecha inválida ingresada: $texto",
                    e
                )
                println("Fecha inválida. Use el formato AAAA-MM-DD.")
            }
        }
    }

    fun leerOpcion(
        mensaje: String,
        opciones: IntRange
    ): Int = leerEntero(mensaje, opciones)
}