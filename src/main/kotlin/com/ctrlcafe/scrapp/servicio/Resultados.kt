package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import java.time.LocalDate

/**
 * Modelos de salida de los motores de cálculo.
 *
 * Viven en `servicio/` y no en `modelo/` a propósito: `modelo/` es el dominio
 * persistido (paquete del Integrante 1), esto son resultados derivados que solo
 * produce el paquete de motores y consume la vista.
 */

// ---------------------------------------------------------------- Proyección

/**
 * Proyección de producción de un producto para una fecha objetivo.
 *
 * Se devuelve el desglose completo y no solo [cantidadSugerida] porque el
 * dashboard del Integrante 4 muestra el cálculo paso a paso.
 */
data class ProyeccionProducto(
    val productoId: String,
    val nombreProducto: String,
    val fechaObjetivo: LocalDate,
    /** Ventas de los 4 días equivalentes anteriores; `null` donde no hay historial. */
    val ventasEquivalentes: List<Double?>,
    val demandaBase: Double,
    val tasaMerma: Double,
    val margenSeguridad: Double,
    val factorCorreccion: Double,
    val cantidadSugerida: Double,
    /** Mensaje a mostrar cuando el cálculo se hizo con datos incompletos. */
    val advertencia: String? = null
) {
    val tieneHistorialCompleto: Boolean get() = ventasEquivalentes.none { it == null }
    val semanasConDatos: Int get() = ventasEquivalentes.count { it != null }
}

// --------------------------------------------------------------- Financiero

/** Una fila del ranking de productos con mayor pérdida en un periodo. */
data class ProductoCritico(
    val productoId: String,
    val nombreProducto: String,
    val unidadesPerdidas: Double,
    val costoPerdida: Double,
    /** Participación de este producto en el costo total de mermas del periodo. */
    val porcentajeDelTotal: Double
)

/** Cierre financiero de un rango de fechas. */
data class ResumenFinanciero(
    val desde: LocalDate,
    val hasta: LocalDate,
    val costoMermas: Double,
    val costoProduccionTotal: Double,
    /** `(costoMermas / costoProduccionTotal) * 100`; 0.0 si no hubo producción. */
    val indiceMerma: Double,
    val unidadesPerdidas: Double,
    val criticos: List<ProductoCritico>,
    val advertencia: String? = null
)

// ----------------------------------------------------------------- Semáforo

/** Lote con su estado de caducidad ya evaluado, listo para el monitor de lotes. */
data class EstadoLote(
    val lote: Lote,
    val nombreProducto: String,
    val horasRestantes: Long,
    val estado: EstadoCaducidad,
    /** Un lote vencido no puede usarse: el sistema propone registrar la merma. */
    val bloqueado: Boolean
)

// --------------------------------------------- RF5: actualización dinámica

/**
 * Efecto en cadena de registrar una merma. Guarda el **antes y el después** de
 * cada valor: ese contraste impreso en consola es la evidencia del RF5.
 */
data class ResultadoRecalculo(
    val merma: Merma,
    val loteId: String,
    val nombreProducto: String,
    val cantidadLoteAntes: Double,
    val cantidadLoteDespues: Double,
    val estadoLoteAntes: EstadoCaducidad,
    val estadoLoteDespues: EstadoCaducidad,
    val costoPerdida: Double,
    val perdidaDiaAntes: Double,
    val perdidaDiaDespues: Double,
    val tasaMermaAntes: Double,
    val tasaMermaDespues: Double,
    val sugeridaAntes: Double,
    val sugeridaDespues: Double
) {
    val loteQuedoVacio: Boolean get() = cantidadLoteDespues <= 0.0
    val cambioEstadoLote: Boolean get() = estadoLoteAntes != estadoLoteDespues
}
