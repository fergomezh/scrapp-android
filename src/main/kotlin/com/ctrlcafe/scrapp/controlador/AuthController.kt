package com.ctrlcafe.scrapp.controlador

import com.ctrlcafe.scrapp.modelo.Accion
import com.ctrlcafe.scrapp.modelo.Usuario
import com.ctrlcafe.scrapp.repositorio.UsuarioRepositorio
import com.ctrlcafe.scrapp.util.PermisoDenegadoException
import com.ctrlcafe.scrapp.util.SesionNoIniciadaException

/**
 * Controlador encargado de la autenticación, gestión de sesión activa
 * y validación de permisos basados en roles (Administrador u Operativo).
 */
class AuthController(private val usuarioRepositorio: UsuarioRepositorio) {

    // companion object funciona como los miembros estáticos de Java.
    // Mantiene una única sesión activa en toda la ejecución de la app (patrón Singleton simple).
    companion object {
        var usuarioActual: Usuario? = null
            private set // Solo se puede modificar desde dentro de esta clase (encapsulamiento).
    }

    /**
     * Inicia sesión validando credenciales contra el repositorio de usuarios.
     * @return El usuario autenticado si las credenciales son correctas.
     */
    fun iniciarSesion(usernameInput: String, contrasenaInput: String): Usuario {
        val usuario = usuarioRepositorio.listar().find { it.username == usernameInput }
            ?: throw IllegalArgumentException("Usuario no encontrado.")

        if (usuario.contrasena != contrasenaInput) {
            throw IllegalArgumentException("Contraseña incorrecta.")
        }

        // Asignamos la sesión activa
        usuarioActual = usuario
        return usuario
    }

    /**
     * Cierra la sesión actual limpiando la variable global.
     */
    fun cerrarSesion() {
        usuarioActual = null
    }

    /**
     * Verifica si hay un usuario logueado actualmente.
     */
    fun estaAutenticado(): Boolean {
        return usuarioActual != null
    }

    /**
     * Valida si el usuario en sesión tiene permiso para ejecutar una acción específica.
     * Si no hay sesión o no tiene permisos, lanza la excepción correspondiente del dominio.
     */
    fun verificarPermiso(accion: Accion) {
        val usuario = usuarioActual ?: throw SesionNoIniciadaException()

        // Llamamos al método abstracto definido en la jerarquía de Usuario
        if (!usuario.puede(accion)) {
            throw PermisoDenegadoException(accion.name)
        }
    }
}