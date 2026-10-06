package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.util.Logger
import java.time.LocalDateTime

/**
 * Actualización dinámica al registrar una merma.
 *
 * Contrato con `MermaController.registrarMerma`: el controlador
 * valida, congela el costo, **descuenta el stock del lote** y guarda la merma.
 * Después se llama a [aplicarMerma], que NO vuelve a descontar: reevalúa el
 * semáforo y recalcula pérdida del día, tasa de merma y proyección.
 *
 * Los valores "antes" se reconstruyen excluyendo la merma recién guardada, así
 * el resultado muestra el efecto real del registro.
 */
class OrquestadorMerma(
    private val loteRepositorio: LoteRepositorio,
    private val mermaRepositorio: MermaRepositorio,
    private val productoRepositorio: ProductoRepositorio,
    private val semaforo: MotorSemaforo,
    private val financiero: MotorFinanciero,
    private val proyeccion: MotorProyeccion
) {

    fun aplicarMerma(merma: Merma, ahora: LocalDateTime = LocalDateTime.now()): ResultadoRecalculo {
        check(mermaRepositorio.buscarPorId(merma.id) != null) {
            "La merma ${merma.id} debe registrarse con MermaController antes de aplicar el recálculo"
        }
        val lote = loteRepositorio.buscarPorId(merma.loteId)
            ?: throw IllegalStateException("El lote ${merma.loteId} de la merma ${merma.id} no existe")

        val hoy = ahora.toLocalDate()
        val manana = hoy.plusDays(1)
        val todas = mermaRepositorio.listar()
        val sinEsta = todas.filter { it.id != merma.id }

        // Antes: estado previo al registro de esta merma.
        val estadoAntes = lote.estado
        val cantidadAntes = lote.cantidadDisponible + merma.cantidad
        val costo = financiero.costoPerdida(merma)
        val perdidaDiaDespues = financiero.perdidaDelDia(merma.fecha)
        val tasaAntes = proyeccion.tasaMermaCon(merma.productoId, hoy, sinEsta)
        val sugeridaAntes = proyeccion.proyectarCon(merma.productoId, manana, sinEsta).cantidadSugerida

        // Después: cadena de recálculo.
        val estadoLote = semaforo.recalcular(lote, ahora)
        val tasaDespues = proyeccion.tasaMermaCon(merma.productoId, hoy, todas)
        val sugeridaDespues = proyeccion.proyectarCon(merma.productoId, manana, todas).cantidadSugerida

        Logger.info(
            OrquestadorMerma::class.java,
            "Recálculo por merma ${merma.id}: lote ${lote.id} ${cantidadAntes} -> ${lote.cantidadDisponible}, " +
                "tasa ${tasaAntes.redondear(4)} -> ${tasaDespues.redondear(4)}"
        )

        return ResultadoRecalculo(
            merma = merma,
            loteId = lote.id,
            nombreProducto = productoRepositorio.buscarPorId(merma.productoId)?.nombre ?: merma.productoId,
            cantidadLoteAntes = cantidadAntes,
            cantidadLoteDespues = lote.cantidadDisponible,
            estadoLoteAntes = estadoAntes,
            estadoLoteDespues = estadoLote.estado,
            costoPerdida = costo,
            perdidaDiaAntes = perdidaDiaDespues - costo,
            perdidaDiaDespues = perdidaDiaDespues,
            tasaMermaAntes = tasaAntes,
            tasaMermaDespues = tasaDespues,
            sugeridaAntes = sugeridaAntes,
            sugeridaDespues = sugeridaDespues
        )
    }
}
