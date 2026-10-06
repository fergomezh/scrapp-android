package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import java.time.LocalDate

/**
 * Impacto financiero de las mermas.
 *
 * Todo cálculo de pérdida usa `Merma.costoUnitarioCongelado`, nunca el costo
 * actual del producto: si el administrador cambia el precio, el histórico no
 * se distorsiona.
 *
 * Costo de producción de un periodo = Suma (cantidadInicial * costoUnitario) de los
 * lotes ingresados en ese periodo.
 */
class MotorFinanciero(
    private val mermaRepositorio: MermaRepositorio,
    private val loteRepositorio: LoteRepositorio,
    private val productoRepositorio: ProductoRepositorio
) {

    /** costoPerdida = cantidadDesperdiciada * costoUnitarioProduccion (congelado). */
    fun costoPerdida(merma: Merma): Double = merma.cantidad * merma.costoUnitarioCongelado

    fun perdidaAcumulada(desde: LocalDate, hasta: LocalDate): Double {
        validarRango(desde, hasta)
        return mermaRepositorio.listar().enRango(desde, hasta).costoTotal()
    }

    fun perdidaDelDia(fecha: LocalDate = LocalDate.now()): Double = perdidaAcumulada(fecha, fecha)

    fun costoProduccion(desde: LocalDate, hasta: LocalDate): Double {
        validarRango(desde, hasta)
        return loteRepositorio.listar().ingresadosEntre(desde, hasta).costoProduccion()
    }

    /**
     * Índice de merma = (costoMermas / costoProduccionTotal) * 100.
     * Sin producción en el periodo devuelve 0.0 y lo registra en el log, para no
     * mostrar `NaN` ni `Infinity` en consola.
     */
    fun indiceMerma(desde: LocalDate, hasta: LocalDate): Double {
        val produccion = costoProduccion(desde, hasta)
        if (produccion == 0.0) {
            Logger.registrar(
                NivelLog.WARN, MotorFinanciero::class.java,
                "Índice de merma sin producción registrada entre $desde y $hasta; se reporta 0.0"
            )
            return 0.0
        }
        return perdidaAcumulada(desde, hasta).porcentajeDe(produccion)
    }

    /** Productos con mayor costo de pérdida en el periodo, de mayor a menor. */
    fun rankingCriticos(desde: LocalDate, hasta: LocalDate, top: Int = 5): List<ProductoCritico> {
        validarRango(desde, hasta)
        val mermas = mermaRepositorio.listar().enRango(desde, hasta)
        val costoTotal = mermas.costoTotal()

        return mermas
            .groupBy { it.productoId }
            .map { (productoId, delProducto) ->
                val costo = delProducto.costoTotal()
                ProductoCritico(
                    productoId = productoId,
                    nombreProducto = productoRepositorio.buscarPorId(productoId)?.nombre ?: productoId,
                    unidadesPerdidas = delProducto.unidadesTotales(),
                    costoPerdida = costo,
                    porcentajeDelTotal = costo.porcentajeDe(costoTotal)
                )
            }
            .sortedByDescending { it.costoPerdida }
            .take(top)
    }

    /** Cierre financiero completo del periodo, listo para el reporte (RF4). */
    fun resumen(desde: LocalDate, hasta: LocalDate, top: Int = 5): ResumenFinanciero {
        val produccion = costoProduccion(desde, hasta)
        return ResumenFinanciero(
            desde = desde,
            hasta = hasta,
            costoMermas = perdidaAcumulada(desde, hasta),
            costoProduccionTotal = produccion,
            indiceMerma = indiceMerma(desde, hasta),
            unidadesPerdidas = mermaRepositorio.listar().enRango(desde, hasta).unidadesTotales(),
            criticos = rankingCriticos(desde, hasta, top),
            advertencia = if (produccion == 0.0) "Sin lotes ingresados en el periodo: índice no calculable" else null
        )
    }

    private fun validarRango(desde: LocalDate, hasta: LocalDate) {
        require(!desde.isAfter(hasta)) { "Rango de fechas inválido: $desde es posterior a $hasta" }
    }
}
