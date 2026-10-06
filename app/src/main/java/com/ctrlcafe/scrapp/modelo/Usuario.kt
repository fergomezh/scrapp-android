package com.ctrlcafe.scrapp.modelo

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
    val contrasena: String,
    val nombreCompleto: String
) {
    abstract val rol: String
    abstract fun puede(accion: Accion): Boolean
}

class Administrador(
    id: String,
    username: String,
    contrasena: String,
    nombreCompleto: String
) : Usuario(id, username, contrasena, nombreCompleto) {
    override val rol: String = "ADMINISTRADOR"
    override fun puede(accion: Accion): Boolean = true
}

class Operativo(
    id: String,
    username: String,
    contrasena: String,
    nombreCompleto: String
) : Usuario(id, username, contrasena, nombreCompleto) {
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
