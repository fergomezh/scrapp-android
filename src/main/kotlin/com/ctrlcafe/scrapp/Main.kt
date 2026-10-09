1
package com.ctrlcafe.scrapp

import com.ctrlcafe.scrapp.controlador.*
import com.ctrlcafe.scrapp.repositorio.*
import com.ctrlcafe.scrapp.servicio.*
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.vista.FlujoRegistroMerma
import com.ctrlcafe.scrapp.vista.MenuAdministrador
import com.ctrlcafe.scrapp.vista.MenuOperativo
import com.ctrlcafe.scrapp.vista.MenuPrincipal

fun main() {
    Logger.info(MenuPrincipal::class.java, "Inicializando Scrapp en modo consola...")

    // 1. Repositorios
    val usuarioRepo = UsuarioRepositorio()
    val productoRepo = ProductoRepositorio()
    val loteRepo = LoteRepositorio()
    val ventaRepo = VentaRepositorio()
    val mermaRepo = MermaRepositorio()

    // Carga de datos semilla iniciales
    DatosIniciales.cargar(usuarioRepo, productoRepo, loteRepo, ventaRepo)

    // 2. Instanciación del sistema de autenticación y motores de cálculo
    val auth = AuthController(usuarioRepo)
    val config = ConfiguracionMotores()
    val semaforo = MotorSemaforo(loteRepo, productoRepo, config)
    val financiero = MotorFinanciero(mermaRepo, loteRepo, productoRepo)
    val proyeccion = MotorProyeccion(ventaRepo, mermaRepo, loteRepo, productoRepo, config)

    // 3. Controladores y Orquestador
    val mermaController = MermaController(mermaRepo, loteRepo, productoRepo, auth)
    val orquestador = OrquestadorMerma(loteRepo, mermaRepo, productoRepo, semaforo, financiero, proyeccion)

    // 4. Vistas (Pasando las dependencias requeridas a los menús)
    val registroMerma = FlujoRegistroMerma(mermaController, orquestador, semaforo, productoRepo, mermaRepo)
    val menuOperativo = MenuOperativo(productoRepo, semaforo, mermaRepo, registroMerma)
    val menuAdministrador = MenuAdministrador(
        productoRepo, loteRepo, mermaRepo, semaforo, financiero, proyeccion, registroMerma
    )
    val menuPrincipal = MenuPrincipal(auth, menuOperativo, menuAdministrador)

    // Arrancamos la aplicación
    menuPrincipal.iniciar()
}