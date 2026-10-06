package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.servicio.MotorProyeccion
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.servicio.OrquestadorMerma

/**
 * Equivalente Android del cableado que hacía `Main.kt` en la Etapa 2.
 *
 * Es un único objeto para toda la app: todos los ViewModels reciben las mismas
 * instancias, de modo que una merma registrada desde MermaViewModel se ve de
 * inmediato en el monitor de lotes y en el panel de gerencia.
 *
 * Uso desde un ViewModel:
 * ```
 * class LoteViewModel(
 *     private val semaforo: MotorSemaforo = ContenedorDatos.semaforo
 * ) : ViewModel()
 * ```
 *
 * En el Avance 2 los repositorios en memoria se reemplazan por los de Firestore
 * aquí mismo, sin tocar los ViewModels.
 */
object ContenedorDatos {

    // Repositorios en memoria (Etapa 2)
    val usuarioRepositorio = UsuarioRepositorio()
    val productoRepositorio = ProductoRepositorio()
    val loteRepositorio = LoteRepositorio()
    val ventaRepositorio = VentaRepositorio()
    val mermaRepositorio = MermaRepositorio()

    // Motores de cálculo
    val configuracion = ConfiguracionMotores()
    val semaforo = MotorSemaforo(loteRepositorio, productoRepositorio, configuracion)
    val financiero = MotorFinanciero(mermaRepositorio, loteRepositorio, productoRepositorio)
    val proyeccion = MotorProyeccion(
        ventaRepositorio, mermaRepositorio, loteRepositorio, productoRepositorio, configuracion
    )
    val orquestadorMerma = OrquestadorMerma(
        loteRepositorio, mermaRepositorio, productoRepositorio, semaforo, financiero, proyeccion
    )

    /**
     * Firebase. Es `lazy` para que la app no falle al arrancar si todavía no
     * existe `google-services.json`; solo falla quien intente autenticarse.
     */
    val authRepositorio: AuthRepositorio by lazy { AuthRepositorio() }

    init {
        DatosIniciales.cargar(usuarioRepositorio, productoRepositorio, loteRepositorio, ventaRepositorio)
    }
}
