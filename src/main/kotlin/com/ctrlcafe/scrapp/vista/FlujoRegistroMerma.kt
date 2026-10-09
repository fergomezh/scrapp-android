package com.ctrlcafe.scrapp.vista

import com.ctrlcafe.scrapp.controlador.MermaController
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.servicio.EstadoLote
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.servicio.OrquestadorMerma
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.ScrappException
import com.ctrlcafe.scrapp.util.Validador

/**
 * Registro de merma desde consola, compartido por el menú operativo y el de administrador.
 *
 * La vista solo recoge los datos: `MermaController` valida, congela el costo y descuenta
 * el stock; después `OrquestadorMerma` recalcula y se muestra el antes y el después.
 */
class FlujoRegistroMerma(
    private val mermaController: MermaController,
    private val orquestador: OrquestadorMerma,
    private val semaforo: MotorSemaforo,
    private val productoRepo: ProductoRepositorio,
    private val mermaRepo: MermaRepositorio
) {
    fun ejecutar() {
        ConsolaUI.titulo("Registrar merma")

        val estadoLote = elegirLote() ?: return cancelar()
        val lote = estadoLote.lote

        val cantidad = Validador.leerDecimal("\nCantidad a descontar (disponible: %.2f): ".format(lote.cantidadDisponible))
        val causa = elegirCausa(if (estadoLote.bloqueado) CausaMerma.VENCIMIENTO else null) ?: return cancelar()
        val evidencia = Validador.leerTexto("\nEvidencia (ruta o descripción, ENTER para omitir): ", permitirVacio = true)
            .ifBlank { SIN_EVIDENCIA }

        val costoUnitario = productoRepo.buscarPorId(lote.productoId)?.costoUnitario ?: lote.costoUnitario
        println("\nResumen de la merma")
        ConsolaUI.separador()
        println("Lote             : ${lote.id} - ${estadoLote.nombreProducto}")
        println("Cantidad         : %.2f".format(cantidad))
        println("Causa            : ${causa.descripcion}")
        println("Evidencia        : $evidencia")
        println("Pérdida estimada : ${ConsolaUI.moneda(cantidad * costoUnitario)}")
        if (Validador.leerOpcion("\n1. Confirmar  |  0. Cancelar: ", 0..1) == 0) return cancelar()

        val merma = intentar("No se registró la merma") {
            mermaController.registrarMerma(siguienteId(), lote.id, cantidad, causa, evidencia)
        } ?: return
        Logger.info(FlujoRegistroMerma::class.java, "Merma ${merma.id} registrada en el lote ${lote.id}")

        val resultado = intentar("La merma ${merma.id} se registró, pero no se pudo recalcular") {
            orquestador.aplicarMerma(merma)
        } ?: return
        ConsolaUI.mostrarRecalculo(resultado)
    }

    /** Lotes con existencias ordenados por urgencia; `null` si el usuario cancela o no hay lotes. */
    private fun elegirLote(): EstadoLote? {
        val lotes = semaforo.recalcularTodos()
        if (lotes.isEmpty()) {
            println("No hay lotes con existencias para registrar mermas.")
            return null
        }

        println("%-4s %-10s %-26s %10s %12s %-10s".format("#", "LOTE", "PRODUCTO", "DISP.", "CADUCIDAD", "ESTADO"))
        ConsolaUI.separador()
        lotes.forEachIndexed { i, estado ->
            val sugerido = if (estado.bloqueado) "  <- vencido, registrar merma" else ""
            println("%-4d %-10s %-26s %10.2f %12s %-10s%s".format(
                i + 1, estado.lote.id, estado.nombreProducto.take(26), estado.lote.cantidadDisponible,
                ConsolaUI.fecha(estado.lote.fechaCaducidad), estado.estado.nivelAlerta, sugerido
            ))
        }

        val opcion = Validador.leerOpcion("\nSeleccione el lote (0 para cancelar): ", 0..lotes.size)
        return if (opcion == 0) null else lotes[opcion - 1]
    }

    private fun elegirCausa(sugerida: CausaMerma?): CausaMerma? {
        val causas = CausaMerma.entries
        println("\nCausa de la merma")
        causas.forEachIndexed { i, causa ->
            val marca = if (causa == sugerida) "  (sugerida)" else ""
            println("${i + 1}. ${causa.descripcion}$marca")
        }
        val opcion = Validador.leerOpcion("Seleccione la causa (0 para cancelar): ", 0..causas.size)
        return if (opcion == 0) null else causas[opcion - 1]
    }

    /** Siguiente ID a partir del mayor existente, para no repetir IDs si se elimina una merma. */
    private fun siguienteId(): String {
        val ultimo = mermaRepo.listar()
            .map(Merma::id)
            .filter { it.startsWith(PREFIJO_ID) }
            .mapNotNull { it.removePrefix(PREFIJO_ID).toIntOrNull() }
            .maxOrNull() ?: 0
        return PREFIJO_ID + (ultimo + 1).toString().padStart(3, '0')
    }

    /** Ejecuta [accion]; ante un error de negocio lo muestra, lo registra en el log y devuelve `null`. */
    private fun <T> intentar(contexto: String, accion: () -> T): T? {
        return try {
            accion()
        } catch (e: Exception) {
            if (e !is ScrappException && e !is IllegalArgumentException && e !is IllegalStateException) throw e
            Logger.error(FlujoRegistroMerma::class.java, contexto, e)
            println("\n$contexto. ${e.message}")
            null
        }
    }

    private fun cancelar() = println("\nRegistro de merma cancelado.")

    companion object {
        private const val PREFIJO_ID = "MER-"
        private const val SIN_EVIDENCIA = "Sin evidencia"
    }
}
