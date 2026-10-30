package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.repositorio.firestore.*
import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.servicio.MotorProyeccion
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.servicio.OrquestadorMerma

object ContenedorDatos {

    // 1. Instanciamos los nuevos repositorios de Firestore
    val configuracionRepositorio = ConfiguracionRepositorio()
    val productoRepositorio = FirestoreProductoRepositorio()
    val loteRepositorio = FirestoreLoteRepositorio()
    val ventaRepositorio = FirestoreVentaRepositorio()
    val mermaRepositorio = FirestoreMermaRepositorio()

    // Mantenemos el usuarioRepositorio en memoria por ahora para no romper el Auth (I2 se encarga)
    val usuarioRepositorio = UsuarioRepositorio()

    // 2. Motores de cálculo (No se tocan, usan la interfaz Repositorio<T>)
    val configuracion = ConfiguracionMotores()
    val semaforo = MotorSemaforo(loteRepositorio, productoRepositorio, configuracion)
    val financiero = MotorFinanciero(mermaRepositorio, loteRepositorio, productoRepositorio)
    val proyeccion = MotorProyeccion(
        ventaRepositorio, mermaRepositorio, loteRepositorio, productoRepositorio, configuracion
    )
    val orquestadorMerma = OrquestadorMerma(
        loteRepositorio, mermaRepositorio, productoRepositorio, semaforo, financiero, proyeccion
    )

    val authRepositorio: AuthRepositorio by lazy { AuthRepositorio() }

    init {
        // 3. Sembrar datos automáticamente si la base en la nube está vacía
        SembradorDatos.sembrarSiEstaVacio(
            productoRepositorio,
            loteRepositorio,
            ventaRepositorio,
            mermaRepositorio
        )
    }
}