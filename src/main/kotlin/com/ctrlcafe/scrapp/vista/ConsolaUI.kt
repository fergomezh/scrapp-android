package com.ctrlcafe.scrapp.vista

import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.servicio.EstadoLote
import com.ctrlcafe.scrapp.servicio.ResultadoRecalculo
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ConsolaUI {
    private val moneda = NumberFormat.getCurrencyInstance(Locale.US)
    private val fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun limpiar() {
        repeat(2) { println() }
    }

    fun titulo(texto: String) {
        println()
        println("=".repeat(78))
        println(texto.uppercase().padStart((78 + texto.length) / 2).take(78))
        println("=".repeat(78))
    }

    val lineaSeparadora = "-".repeat(78)

    fun separador() = println(lineaSeparadora)

    fun pausa() {
        print("\nPresione ENTER para continuar...")
        readLine()
    }

    fun moneda(valor: Double): String = moneda.format(valor)
    fun fecha(valor: LocalDate): String = valor.format(fecha)

    fun mostrarProductos(productos: List<Producto>) {
        titulo("Productos")
        if (productos.isEmpty()) {
            println("No hay productos registrados.")
            return
        }
        println("%-10s %-30s %-18s %12s".format("ID", "NOMBRE", "CATEGORÍA", "COSTO"))
        separador()
        productos.forEach {
            println("%-10s %-30s %-18s %12s".format(
                it.id, it.nombre.take(30), it.categoria.take(18), moneda(it.costoUnitario)
            ))
        }
    }

    /** Recibe los lotes ya evaluados por `MotorSemaforo`, ordenados por urgencia. */
    fun mostrarLotes(estados: List<EstadoLote>) {
        titulo("Monitor de lotes")
        if (estados.isEmpty()) {
            println("No hay lotes registrados.")
            return
        }
        println("%-10s %-28s %10s %12s %-10s %12s".format(
            "LOTE", "PRODUCTO", "DISP.", "CADUCIDAD", "ESTADO", "COSTO"
        ))
        separador()
        estados.forEach { estado ->
            val lote = estado.lote
            val valor = lote.cantidadDisponible * lote.costoUnitario
            println("%-10s %-28s %10.2f %12s %-10s %12s".format(
                lote.id, estado.nombreProducto.take(28), lote.cantidadDisponible,
                fecha(lote.fechaCaducidad), estado.estado.nivelAlerta, moneda(valor)
            ))
        }
    }

    fun mostrarMermas(mermas: List<Merma>, productos: List<Producto>) {
        titulo("Registro de mermas")
        if (mermas.isEmpty()) {
            println("No hay mermas registradas.")
            return
        }
        val mapa = productos.associateBy { it.id }
        println("%-10s %-26s %10s %15s %-24s".format(
            "ID", "PRODUCTO", "CANT.", "PÉRDIDA", "CAUSA"
        ))
        separador()
        mermas.forEach {
            println("%-10s %-26s %10.2f %15s %-24s".format(
                it.id, (mapa[it.productoId]?.nombre ?: it.productoId).take(26),
                it.cantidad, moneda(it.cantidad * it.costoUnitarioCongelado),
                it.causa.descripcion.take(24)
            ))
        }
    }

    fun mostrarRecalculo(resultado: ResultadoRecalculo) {
        val merma = resultado.merma
        titulo("Merma registrada")
        println("Merma ${merma.id} | ${resultado.nombreProducto} | Lote ${resultado.loteId}")
        println("Causa: ${merma.causa.descripcion} | Pérdida: ${moneda(resultado.costoPerdida)}")
        println()
        println("%-30s %20s %20s".format("INDICADOR", "ANTES", "DESPUÉS"))
        separador()
        filaRecalculo("Stock del lote",
            "%.2f".format(resultado.cantidadLoteAntes), "%.2f".format(resultado.cantidadLoteDespues))
        filaRecalculo("Estado del lote",
            resultado.estadoLoteAntes.nivelAlerta, resultado.estadoLoteDespues.nivelAlerta)
        filaRecalculo("Pérdida del día",
            moneda(resultado.perdidaDiaAntes), moneda(resultado.perdidaDiaDespues))
        filaRecalculo("Tasa de merma (14 días)",
            "%.2f%%".format(resultado.tasaMermaAntes * 100), "%.2f%%".format(resultado.tasaMermaDespues * 100))
        filaRecalculo("Producción sugerida mañana",
            "%.2f".format(resultado.sugeridaAntes), "%.2f".format(resultado.sugeridaDespues))

        // Se compara el nivel y no el objeto: Critico/ProximoAVencer cambian con las horas restantes.
        val nivelAntes = resultado.estadoLoteAntes.nivelAlerta
        val nivelDespues = resultado.estadoLoteDespues.nivelAlerta
        if (resultado.loteQuedoVacio) println("\nAviso: el lote ${resultado.loteId} quedó sin existencias.")
        if (nivelAntes != nivelDespues) println("Aviso: el lote pasó de $nivelAntes a $nivelDespues.")
    }

    private fun filaRecalculo(indicador: String, antes: String, despues: String) =
        println("%-30s %20s %20s".format(indicador, antes, despues))

    fun barra(nombre: String, valor: Double, maximo: Double, ancho: Int = 35): String {
        val n = if (maximo <= 0) 0 else ((valor / maximo) * ancho).toInt().coerceIn(0, ancho)
        return "%-28s |%s %s".format(nombre.take(28), "#".repeat(n), moneda(valor))
    }
}
