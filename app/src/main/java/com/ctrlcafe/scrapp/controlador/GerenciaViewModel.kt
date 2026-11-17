package com.ctrlcafe.scrapp.controlador

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.servicio.MermaPorCausa
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.servicio.ProductoCritico
import com.ctrlcafe.scrapp.util.ExportadorReporte
import com.ctrlcafe.scrapp.util.Logger
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class GerenciaViewModel(
    private val financiero: MotorFinanciero = ContenedorDatos.financiero
) : ViewModel() {

    private val _fechaInicio = mutableStateOf(LocalDate.now().withDayOfMonth(1))
    val fechaInicio: State<LocalDate> = _fechaInicio

    private val _fechaFin = mutableStateOf(LocalDate.now())
    val fechaFin: State<LocalDate> = _fechaFin

    private val _perdidaDia = mutableStateOf(0.0)
    val perdidaDia: State<Double> = _perdidaDia

    private val _perdidaMes = mutableStateOf(0.0)
    val perdidaMes: State<Double> = _perdidaMes

    private val _indiceMerma = mutableStateOf(0.0)
    val indiceMerma: State<Double> = _indiceMerma

    private val _productosCriticos = mutableStateOf<List<Pair<String, Float>>>(emptyList())
    val productosCriticos: State<List<Pair<String, Float>>> = _productosCriticos

    /** Top 5 con costo y porcentaje, para la leyenda de la gráfica de desperdicio crítico. */
    private val _detalleCriticos = mutableStateOf<List<ProductoCritico>>(emptyList())
    val detalleCriticos: State<List<ProductoCritico>> = _detalleCriticos

    private val _desgloseCausas = mutableStateOf<List<MermaPorCausa>>(emptyList())
    val desgloseCausas: State<List<MermaPorCausa>> = _desgloseCausas

    private val _modeloLineasCostoMes = mutableStateOf<ChartEntryModel>(entryModelOf(0f))
    val modeloLineasCostoMes: State<ChartEntryModel> = _modeloLineasCostoMes

    private val _modeloBarrasTopProductos = mutableStateOf<ChartEntryModel>(entryModelOf(0f))
    val modeloBarrasTopProductos: State<ChartEntryModel> = _modeloBarrasTopProductos

    private val _modeloBarrasCausas = mutableStateOf<ChartEntryModel>(entryModelOf(0f))
    val modeloBarrasCausas: State<ChartEntryModel> = _modeloBarrasCausas

    init {
        cargarDatosFinancieros()
    }

    fun actualizarRangoFechas(nuevaInicio: LocalDate, nuevaFin: LocalDate) {
        val inicioOrdenado = if (nuevaInicio.isAfter(nuevaFin)) nuevaFin else nuevaInicio
        val finOrdenado = if (nuevaFin.isBefore(nuevaInicio)) nuevaInicio else nuevaFin
        _fechaInicio.value = inicioOrdenado
        _fechaFin.value = finOrdenado
        cargarDatosFinancieros()
    }

    fun cargarDatosFinancieros(hoy: LocalDate = LocalDate.now()) {
        try {
            val desde = _fechaInicio.value
            val hasta = _fechaFin.value
            val resumen = financiero.resumen(desde, hasta)
            val causas = financiero.desglosePorCausa(desde, hasta)

            _perdidaDia.value = financiero.perdidaDelDia(hoy)
            _perdidaMes.value = resumen.costoMermas
            _indiceMerma.value = resumen.indiceMerma
            _desgloseCausas.value = causas

            val criticosMapeados = resumen.criticos.map {
                it.nombreProducto to (it.porcentajeDelTotal / 100.0).toFloat()
            }
            _productosCriticos.value = criticosMapeados
            _detalleCriticos.value = resumen.criticos

            val diasRango = ChronoUnit.DAYS.between(desde, hasta).coerceAtMost(13L)
            val inicioSerie = hasta.minusDays(diasRango)
            val puntosLinea = (0..diasRango).map { offset ->
                financiero.perdidaDelDia(inicioSerie.plusDays(offset)).toFloat()
            }
            _modeloLineasCostoMes.value = if (puntosLinea.isNotEmpty()) {
                entryModelOf(*puntosLinea.toTypedArray())
            } else {
                entryModelOf(0f)
            }

            val valoresBarras = criticosMapeados.map { it.second * 100f }
            _modeloBarrasTopProductos.value = if (valoresBarras.isNotEmpty()) {
                entryModelOf(*valoresBarras.toTypedArray())
            } else {
                entryModelOf(0f)
            }

            val valoresCausas = causas.map { it.costoPerdida.toFloat() }
            _modeloBarrasCausas.value = if (valoresCausas.any { it > 0f }) {
                entryModelOf(*valoresCausas.toTypedArray())
            } else {
                entryModelOf(0f, 0f, 0f, 0f, 0f)
            }
        } catch (e: Exception) {
            Logger.error(GerenciaViewModel::class.java, "Error al cargar datos financieros: ${e.message}", e)
        }
    }

    fun exportarReporteCsv(context: Context) {
        try {
            val desde = _fechaInicio.value
            val hasta = _fechaFin.value
            val resumen = financiero.resumen(desde, hasta)
            val causas = financiero.desglosePorCausa(desde, hasta)
            ExportadorReporte.compartirResumenCsv(context, resumen, causas)
        } catch (e: Exception) {
            Logger.error(GerenciaViewModel::class.java, "Error al exportar CSV: ${e.message}", e)
        }
    }
}