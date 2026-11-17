package com.ctrlcafe.scrapp.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ctrlcafe.scrapp.servicio.MermaPorCausa
import com.ctrlcafe.scrapp.servicio.ResumenFinanciero
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

object ExportadorReporte {

    fun compartirResumenCsv(
        context: Context,
        resumen: ResumenFinanciero,
        desgloseCausas: List<MermaPorCausa>
    ) {
        val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
        val formatoFecha = DateTimeFormatter.ISO_LOCAL_DATE
        val nombreArchivo = "scrapp_reporte_${resumen.desde.format(formatoFecha)}_${resumen.hasta.format(formatoFecha)}.csv"
        val archivo = File(carpeta, nombreArchivo)

        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("REPORTE GERENCIAL DE MERMAS - SCRAPP (CTRL+CAFE)")
        sb.appendLine("Periodo,${resumen.desde},${resumen.hasta}")
        sb.appendLine()
        sb.appendLine("INDICADOR,VALOR")
        sb.appendLine("Costo Total de Mermas (USD),${String.format(Locale.US, "%.2f", resumen.costoMermas)}")
        sb.appendLine("Costo Total de Produccion (USD),${String.format(Locale.US, "%.2f", resumen.costoProduccionTotal)}")
        sb.appendLine("Indice de Merma (%),${String.format(Locale.US, "%.2f", resumen.indiceMerma)}")
        sb.appendLine("Unidades Perdidas,${String.format(Locale.US, "%.2f", resumen.unidadesPerdidas)}")
        sb.appendLine()

        sb.appendLine("TOP PRODUCTOS CRITICOS")
        sb.appendLine("Producto,Unidades Perdidas,Costo Perdida (USD),Participacion (%)")
        resumen.criticos.forEach { item ->
            val nombreLimpio = item.nombreProducto.replace(",", " ")
            sb.appendLine(
                "$nombreLimpio," +
                        "${String.format(Locale.US, "%.2f", item.unidadesPerdidas)}," +
                        "${String.format(Locale.US, "%.2f", item.costoPerdida)}," +
                        String.format(Locale.US, "%.2f", item.porcentajeDelTotal)
            )
        }
        sb.appendLine()

        sb.appendLine("DESGLOSE POR CAUSA DE MERMA")
        sb.appendLine("Causa,Unidades Perdidas,Costo Perdida (USD),Participacion (%)")
        desgloseCausas.forEach { item ->
            val causaLimpia = item.causa.descripcion.replace(",", " ")
            sb.appendLine(
                "$causaLimpia," +
                        "${String.format(Locale.US, "%.2f", item.unidadesPerdidas)}," +
                        "${String.format(Locale.US, "%.2f", item.costoPerdida)}," +
                        String.format(Locale.US, "%.2f", item.porcentajeDelTotal)
            )
        }

        archivo.writeText(sb.toString(), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte Gerencial Scrapp (${resumen.desde} a ${resumen.hasta})")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Compartir reporte CSV")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}