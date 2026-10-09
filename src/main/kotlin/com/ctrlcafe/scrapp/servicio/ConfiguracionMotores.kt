package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.util.CantidadInvalidaException

/**
 * Umbrales del semáforo de caducidad, en horas restantes.
 * Valores por defecto acordados en la Etapa 1: >72h verde, 24-72h amarillo, 0-24h rojo.
 */
data class UmbralesSemaforo(
    val horasCritico: Long = 24,
    val horasProximo: Long = 72
) {
    init {
        if (horasCritico <= 0 || horasProximo <= horasCritico) {
            throw CantidadInvalidaException(
                "Umbrales inválidos: se requiere 0 < horasCritico ($horasCritico) < horasProximo ($horasProximo)"
            )
        }
    }
}

/**
 * Parámetros que el administrador puede ajustar en tiempo de ejecución y que
 * alimentan a los tres motores. Se instancia una sola vez en Main y se inyecta
 * por constructor a cada motor.
 */
class ConfiguracionMotores(margenSeguridadInicial: Double = MARGEN_POR_DEFECTO) {

    var margenSeguridad: Double = margenSeguridadInicial
        set(valor) {
            if (valor < 0.0 || valor > 1.0) {
                throw CantidadInvalidaException("El margen de seguridad debe estar entre 0.0 y 1.0, se recibió $valor")
            }
            field = valor
        }

    init {
        // Asignar en init fuerza que el valor inicial pase por la validación del setter.
        margenSeguridad = margenSeguridadInicial
    }

    private val umbralesPorProducto = mutableMapOf<String, UmbralesSemaforo>()

    /** Umbrales del producto, o los valores por defecto si no se configuraron. */
    fun umbralesDe(productoId: String): UmbralesSemaforo =
        umbralesPorProducto[productoId] ?: UmbralesSemaforo()

    fun configurarUmbrales(productoId: String, umbrales: UmbralesSemaforo) {
        umbralesPorProducto[productoId] = umbrales
    }

    fun restablecerUmbrales(productoId: String): Boolean =
        umbralesPorProducto.remove(productoId) != null

    fun productosConUmbralPropio(): Map<String, UmbralesSemaforo> = umbralesPorProducto.toMap()

    companion object {
        const val MARGEN_POR_DEFECTO = 0.10
        const val DIAS_VENTANA_MERMA = 14L
        const val SEMANAS_PROYECCION = 4
        val PESOS_PROYECCION = listOf(0.40, 0.30, 0.20, 0.10)
    }
}
