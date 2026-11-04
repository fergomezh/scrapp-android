package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.repositorio.firestore.*
import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.servicio.MotorProyeccion
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.servicio.OrquestadorMerma

object ContenedorDatos {

    // Parámetros de los motores: se crean primero porque ConfiguracionRepositorio los
    // actualiza en tiempo real desde configuracion/motores
    val configuracion = ConfiguracionMotores()

    // 1. Instanciamos los nuevos repositorios de Firestore
    val configuracionRepositorio = ConfiguracionRepositorio(configuracion)
    val productoRepositorio = FirestoreProductoRepositorio()
    val loteRepositorio = FirestoreLoteRepositorio()
    val ventaRepositorio = FirestoreVentaRepositorio()
    val mermaRepositorio = FirestoreMermaRepositorio()

    // Mantenemos el usuarioRepositorio en memoria por ahora para no romper el Auth (I2 se encarga)
    val usuarioRepositorio = UsuarioRepositorio()

    // 2. Motores de cálculo (No se tocan, usan la interfaz Repositorio<T>)
    val semaforo = MotorSemaforo(loteRepositorio, productoRepositorio, configuracion)
    val financiero = MotorFinanciero(mermaRepositorio, loteRepositorio, productoRepositorio)
    val proyeccion = MotorProyeccion(
        ventaRepositorio, mermaRepositorio, loteRepositorio, productoRepositorio, configuracion
    )
    val orquestadorMerma = OrquestadorMerma(
        loteRepositorio, mermaRepositorio, productoRepositorio, semaforo, financiero, proyeccion
    )

    val authRepositorio: AuthRepositorio by lazy { AuthRepositorio() }

    // Subida de fotos de evidencia a Cloudinary
    val evidenciaRepositorio = EvidenciaRepositorio()

    init {
        // 3. Sembrar datos automáticamente si la base en la nube está vacía. Se espera a que
        //    haya sesión: este objeto se crea antes del login y las reglas rechazan la consulta.
        SesionFirestore.alCambiarUsuario { uid ->
            if (uid != null) {
                SembradorDatos.sembrarSiEstaVacio(
                    productoRepositorio,
                    loteRepositorio,
                    ventaRepositorio,
                    mermaRepositorio
                )
                // Ventas de las últimas semanas para la recomendación de producción. Cubre la
                // sesión guardada (no pasa por el login); en el primer login con Google el perfil
                // aún no existe y las reglas lo rechazan, así que AuthRepositorio lo repite después.
                SembradorDatos.programarVentasRecientes()
            }
        }
    }
}