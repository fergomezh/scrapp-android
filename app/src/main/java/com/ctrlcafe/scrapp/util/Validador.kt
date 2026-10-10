package com.ctrlcafe.scrapp.util

import android.util.Patterns
import java.time.LocalDate

object Validador {

    fun validarCorreo(correo: String): String? {
        val texto = correo.trim()
        return when {
            texto.isEmpty() -> "El correo electrónico es requerido."
            !Patterns.EMAIL_ADDRESS.matcher(texto).matches() -> "Formato de correo inválido."
            else -> null
        }
    }

    fun validarContrasena(contrasena: String): String? {
        return when {
            contrasena.isEmpty() -> "La contraseña es requerida."
            contrasena.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
            else -> null
        }
    }

    fun validarConfirmacion(contrasena: String, confirmacion: String): String? {
        return when {
            confirmacion.isEmpty() -> "Confirma tu contraseña."
            contrasena != confirmacion -> "Las contraseñas no coinciden."
            else -> null
        }
    }

    fun validarCampoRequerido(valor: String, nombreCampo: String = "Este campo"): String? {
        return if (valor.trim().isEmpty()) "$nombreCampo es obligatorio." else null
    }

    fun validarNumeroPositivo(valor: String, nombreCampo: String = "La cantidad"): String? {
        val numero = valor.trim().toDoubleOrNull()
        return when {
            valor.trim().isEmpty() -> "$nombreCampo es obligatoria."
            numero == null -> "$nombreCampo debe ser un valor numérico."
            numero <= 0.0 -> "$nombreCampo debe ser mayor a cero."
            else -> null
        }
    }

    fun validarFechaNoFutura(fecha: LocalDate): String? {
        return if (fecha.isAfter(LocalDate.now())) "La fecha no puede ser futura." else null
    }
}