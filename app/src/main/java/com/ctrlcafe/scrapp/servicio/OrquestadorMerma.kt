package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.ctrlcafe.scrapp.util.CantidadInvalidaException
import com.ctrlcafe.scrapp.util.Logger
import java.time.LocalDateTime

/**
 * Actualización dinámica al registrar una merma.
 *
 * Flujo del Avance 2: el controlador valida y arma la merma con el costo ya
 * congelado, y llama a [registrarMerma]. Ese método descuenta el lote, persiste
 * merma + lote juntos (transacción acordada con el Integrante 1, ver
 * [RegistroMermaAtomico]) y luego llama a [aplicarMerma].
 *
 * [aplicarMerma] NO vuelve a descontar: reevalúa el semáforo y recalcula pérdida
 * del día, tasa de merma y proyección. Los valores "antes" se reconstruyen
 * excluyendo la merma recién guardada, así el resultado muestra el efecto real
 * del registro.
 */
class OrquestadorMerma(
    private val loteRepositorio: Repositorio<Lote>,
    private val mermaRepositorio: Repositorio<Merma>,
    private val productoRepositorio: Repositorio<Producto>,
    private val semaforo: MotorSemaforo,
    private val financiero: MotorFinanciero,
    private val proyeccion: MotorProyeccion
) {

    /**
     * Descuenta el lote, guarda merma y lote en una sola escritura y recalcula.
     *
     * Si el repositorio de mermas no soporta la escritura atómica (repositorios en
     * memoria de la Etapa 2), se hacen las dos escrituras por separado.
     *
     * @throws CantidadInvalidaException si el lote no existe o la cantidad no es válida.
     */
    fun registrarMerma(merma: Merma, ahora: LocalDateTime = LocalDateTime.now()): ResultadoRecalculo {
        val lote = loteRepositorio.buscarPorId(merma.loteId)
            ?: throw CantidadInvalidaException("El lote seleccionado no existe.")
        if (merma.cantidad <= 0.0) {
            throw CantidadInvalidaException("La cantidad debe ser mayor a 0.")
        }
        if (merma.cantidad > lote.cantidadDisponible) {
            throw CantidadInvalidaException(
                "La cantidad (${merma.cantidad}) supera las existencias del lote (${lote.cantidadDisponible})."
            )
        }

        val nuevaCantidad = lote.cantidadDisponible - merma.cantidad
        val guardada = if (mermaRepositorio is RegistroMermaAtomico) {
            mermaRepositorio.registrarMermaYDescontarLote(merma, nuevaCantidad).also {
                // La copia en memoria del lote se ajusta ya; el listener la confirma después
                lote.cantidadDisponible = nuevaCantidad
            }
        } else {
            lote.cantidadDisponible = nuevaCantidad
            loteRepositorio.actualizar(lote.id, lote)
            mermaRepositorio.crear(merma)
        }

        return aplicarMerma(guardada, ahora)
    }

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
