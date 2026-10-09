package com.ctrlcafe.scrapp.vista

import com.ctrlcafe.scrapp.controlador.AuthController
import com.ctrlcafe.scrapp.modelo.Usuario
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.Validador

class MenuPrincipal(
    private val authController: AuthController,
    private val operativo: MenuOperativo,
    private val administrador: MenuAdministrador
) {
    fun iniciar() {
        while (true) {
            ConsolaUI.titulo("SCRAPP - Control de Mermas")
            println("1. Iniciar sesión")
            println("0. Salir")
            when (Validador.leerOpcion("Seleccione una opción: ", 0..1)) {
                1 -> {
                    val usuario = login()
                    if (usuario != null) {
                        try {
                            if (usuario.rol.equals("ADMINISTRADOR", true)) {
                                administrador.mostrar(usuario)
                            } else {
                                operativo.mostrar(usuario)
                            }
                        } finally {
                            // Cierra la sesión automáticamente al salir del menú del rol correspondiente
                            authController.cerrarSesion()
                        }
                    }
                }
                0 -> {
                    println("Gracias por utilizar Scrapp.")
                    return
                }
            }
        }
    }

    private fun login(): Usuario? {
        ConsolaUI.titulo("Inicio de sesión")
        val username = Validador.leerTexto("Usuario: ")
        val password = Validador.leerTexto("Contraseña: ")

        return try {
            // Delegamos la autenticación y apertura de sesión al AuthController
            val usuario = authController.iniciarSesion(username, password)
            Logger.info(MenuPrincipal::class.java, "Inicio de sesión exitoso: $username")
            println("\nBienvenido(a), ${usuario.nombreCompleto}.")
            usuario
        } catch (e: IllegalArgumentException) {
            Logger.error(MenuPrincipal::class.java, "Intento de inicio de sesión fallido para: $username")
            println("\nUsuario o contraseña incorrectos.")
            ConsolaUI.pausa()
            null
        }
    }
}