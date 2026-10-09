package com.ctrlcafe.scrapp.vista

import com.ctrlcafe.scrapp.modelo.Usuario
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.servicio.MotorProyeccion
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.Validador
import java.io.File
import java.time.LocalDate

class MenuAdministrador(
    private val productoRepo: ProductoRepositorio,
    private val loteRepo: LoteRepositorio,
    private val mermaRepo: MermaRepositorio,
    private val semaforo: MotorSemaforo,
    private val financiero: MotorFinanciero,
    private val proyeccion: MotorProyeccion,
    private val registroMerma: FlujoRegistroMerma
) {
    fun mostrar(usuario: Usuario) {
        while (true) {
            ConsolaUI.titulo("Menú Administrador")
            println("Usuario: ${usuario.nombreCompleto}")
            println("1. Ver productos")
            println("2. Monitor de lotes")
            println("3. Ver mermas")
            println("4. Resumen / reporte")
            println("5. Registrar merma")
            println("0. Cerrar sesión")

            when (Validador.leerOpcion("Seleccione una opción: ", 0..5)) {
                1 -> ConsolaUI.mostrarProductos(productoRepo.listar())
                2 -> ConsolaUI.mostrarLotes(semaforo.recalcularTodos(incluirAgotados = true))
                3 -> ConsolaUI.mostrarMermas(mermaRepo.listar(), productoRepo.listar())
                4 -> mostrarReporte()
                5 -> registroMerma.ejecutar()
                0 -> return
            }
            ConsolaUI.pausa()
        }
    }

    /** Resumen del periodo con los motores de cálculo; se muestra en consola y se exporta igual a un .txt. */
    private fun mostrarReporte() {
        ConsolaUI.titulo("Resumen de control de mermas")
        val hoy = LocalDate.now()
        val reporte = construirReporte(hoy.minusDays(DIAS_REPORTE - 1), hoy)
        print(reporte)
        exportarReporte(reporte, hoy)
    }

    private fun construirReporte(desde: LocalDate, hasta: LocalDate): String = buildString {
        val resumen = financiero.resumen(desde, hasta)
        val semaforoTexto = semaforo.conteoPorEstado().entries.joinToString(" | ") { (nivel, total) -> "$nivel=$total" }

        appendLine("Periodo             : ${ConsolaUI.fecha(desde)} - ${ConsolaUI.fecha(hasta)} ($DIAS_REPORTE días)")
        appendLine("Productos activos   : ${productoRepo.listar().size}")
        appendLine("Lotes registrados   : ${loteRepo.listar().size}")
        appendLine("Semáforo            : $semaforoTexto (lotes con existencias)")
        appendLine("Pérdida del periodo : ${ConsolaUI.moneda(resumen.costoMermas)} (%.2f unidades)".format(resumen.unidadesPerdidas))
        appendLine("Costo de producción : ${ConsolaUI.moneda(resumen.costoProduccionTotal)}")
        appendLine("Índice de merma     : %.2f%%".format(resumen.indiceMerma))
        resumen.advertencia?.let { appendLine("Aviso: $it") }

        appendLine("\nTop 5 productos críticos:")
        if (resumen.criticos.isEmpty()) appendLine("Sin mermas registradas en el periodo.")
        val maximo = resumen.criticos.maxOfOrNull { it.costoPerdida } ?: 0.0
        resumen.criticos.forEachIndexed { i, critico ->
            appendLine("${i + 1}. ${ConsolaUI.barra(critico.nombreProducto, critico.costoPerdida, maximo)}" +
                " (%.1f%% del total)".format(critico.porcentajeDelTotal))
        }

        val manana = hasta.plusDays(1)
        val proyecciones = proyeccion.proyectarTodos(manana)
        appendLine("\nProducción sugerida para ${ConsolaUI.fecha(manana)}:")
        appendLine("%-28s %14s %12s %12s".format("PRODUCTO", "DEMANDA BASE", "TASA MERMA", "SUGERIDA"))
        appendLine(ConsolaUI.lineaSeparadora)
        proyecciones.forEach { p ->
            appendLine("%-28s %14.2f %11.2f%% %12.2f".format(
                p.nombreProducto.take(28), p.demandaBase, p.tasaMerma * 100, p.cantidadSugerida
            ))
        }
        val conAdvertencia = proyecciones.filter { it.advertencia != null }
        if (conAdvertencia.isNotEmpty()) {
            appendLine("\nNotas de la proyección:")
            conAdvertencia.forEach { appendLine("- ${it.nombreProducto}: ${it.advertencia}") }
        }
    }

    private fun exportarReporte(reporte: String, fecha: LocalDate) {
        try {
            val dir = File("reportes")
            if (!dir.exists()) dir.mkdirs()
            val archivo = File(dir, "resumen_$fecha.txt")
            archivo.writeText("SCRAPP - RESUMEN DE CONTROL DE MERMAS\nGenerado: ${ConsolaUI.fecha(fecha)}\n\n$reporte")
            println("\nReporte exportado en: ${archivo.path}")
        } catch (e: Exception) {
            Logger.error(MenuAdministrador::class.java, "No se pudo exportar el reporte", e)
            println("No fue posible exportar el reporte. Revise logs/errores.log.")
        }
    }

    companion object {
        /** Ventana del resumen: los últimos 30 días, incluido hoy. */
        private const val DIAS_REPORTE = 30L
    }
}
