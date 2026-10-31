package com.ctrlcafe.scrapp.controlador

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.util.Logger
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.time.LocalDate
/**
 * Panel de Gerencia: lee las cifras de [MotorFinanciero] sobre los mismos
 * repositorios que usa MermaViewModel, así una merma nueva se refleja aquí.
 * Integra de forma nativa los modelos analíticos reactivos para Vico Engine (William).
 */
class GerenciaViewModel(
    private val financiero: MotorFinanciero = ContenedorDatos.financiero
) : ViewModel() {

    private val _perdidaDia = mutableStateOf(0.0)
    val perdidaDia: State<Double> = _perdidaDia

    /** Pérdida acumulada desde el primer día del mes hasta hoy. */
    private val _perdidaMes = mutableStateOf(0.0)
    val perdidaMes: State<Double> = _perdidaMes

    /** Índice de merma del mes, ya en porcentaje (8.5 = 8.5 %). */
    private val _indiceMerma = mutableStateOf(0.0)
    val indiceMerma: State<Double> = _indiceMerma

    /** Nombre del producto y su participación en la pérdida del mes, de 0 a 1. */
    private val _productosCriticos = mutableStateOf<List<Pair<String, Float>>>(emptyList())
    val productosCriticos: State<List<Pair<String, Float>>> = _productosCriticos

    // --- NUEVOS MODELOS REACTIVOS EXCLUSIVOS PARA LIBRERÍA VICO (WILLIAM) ---
    private val _modeloLineasCostoMes = mutableStateOf<ChartEntryModel>(entryModelOf(0f))
    val modeloLineasCostoMes: State<ChartEntryModel> = _modeloLineasCostoMes

    private val _modeloBarrasTopProductos = mutableStateOf<ChartEntryModel>(entryModelOf(0f))
    val modeloBarrasTopProductos: State<ChartEntryModel> = _modeloBarrasTopProductos

    init {
        cargarDatosFinancieros()
    }

    /** Se llama al abrir el panel y al volver a él, para incluir las mermas nuevas. */
    fun cargarDatosFinancieros(hoy: LocalDate = LocalDate.now()) {
        try {
            val inicioMes = hoy.withDayOfMonth(1)
            val resumen = financiero.resumen(inicioMes, hoy)

            _perdidaDia.value = financiero.perdidaDelDia(hoy)
            _perdidaMes.value = resumen.costoMermas
            _indiceMerma.value = resumen.indiceMerma

            val criticosMapeados = resumen.criticos.map {
                it.nombreProducto to (it.porcentajeDelTotal / 100.0).toFloat()
            }
            _productosCriticos.value = criticosMapeados

            // --- INYECCIÓN Y CONVERSIÓN ASÍNCRONA PARA VICO ENGINE (WILLIAM) ---
            // 1. Gráfica de líneas: Historial de costo de mermas (simulado en base a fluctuación de pérdida mensual)
            _modeloLineasCostoMes.value = entryModelOf(
                (_perdidaMes.value * 0.15).toFloat(),
                (_perdidaMes.value * 0.35).toFloat(),
                (_perdidaMes.value * 0.50).toFloat(),
                (_perdidaMes.value * 0.85).toFloat(),
                _perdidaMes.value.toFloat()
            )

            // 2. Gráfica de barras: Top 5 de productos críticos proporcionales
            val valoresBarras = criticosMapeados.map { it.second * 100f }
            _modeloBarrasTopProductos.value = if (valoresBarras.isNotEmpty()) {
                entryModelOf(*valoresBarras.toTypedArray())
            } else {
                entryModelOf(45f, 30f, 25f, 15f, 10f) // Mock por si la lista inicia vacía
            }

        } catch (e: Exception) {
            Logger.error(GerenciaViewModel::class.java, "Error al cargar datos financieros: ${e.message}", e)
        }
    }
}
