package com.ctrlcafe.scrapp.modelo

import java.time.LocalDate

enum class Accion {
    REGISTRAR_MERMA,
    CONSULTAR_STOCK,
    ADMINISTRAR_PRODUCTOS,
    ADMINISTRAR_LOTES,
    /** Editar o eliminar mermas ya registradas; distinta de REGISTRAR_MERMA a propósito. */
    ADMINISTRAR_MERMAS,
    VER_REPORTES_FINANCIEROS,
    CONFIGURAR_SISTEMA
}

abstract class Usuario(
    val id: String,
    val username: String,
    val contrasena: String, // Aunque Firebase gestiona la clave, se mantiene para no romper la app local
    val nombreCompleto: String,
    // Nuevos campos requeridos por Firestore
    val correo: String = "",
    val activo: Boolean = true,
    val proveedor: String = "password",
    val creadoEn: LocalDate? = null
) {
    abstract val rol: String
    abstract fun puede(accion: Accion): Boolean
}

class Administrador(
    id: String,
    username: String,
    contrasena: String,
    nombreCompleto: String,
    correo: String = "",
    activo: Boolean = true,
    proveedor: String = "password",
    creadoEn: LocalDate? = null
) : Usuario(id, username, contrasena, nombreCompleto, correo, activo, proveedor, creadoEn) {
    override val rol: String = "ADMINISTRADOR"
    override fun puede(accion: Accion): Boolean = true
}

class Operativo(
    id: String,
    username: String,
    contrasena: String,
    nombreCompleto: String,
    correo: String = "",
    activo: Boolean = true,
    proveedor: String = "password",
    creadoEn: LocalDate? = null
) : Usuario(id, username, contrasena, nombreCompleto, correo, activo, proveedor, creadoEn) {
    override val rol: String = "OPERATIVO"
    override fun puede(accion: Accion): Boolean {
        return when (accion) {
            Accion.REGISTRAR_MERMA,
            Accion.CONSULTAR_STOCK -> true
            Accion.ADMINISTRAR_PRODUCTOS,
            Accion.ADMINISTRAR_LOTES,
            Accion.ADMINISTRAR_MERMAS,
            Accion.VER_REPORTES_FINANCIEROS,
            Accion.CONFIGURAR_SISTEMA -> false
        }
    }
}