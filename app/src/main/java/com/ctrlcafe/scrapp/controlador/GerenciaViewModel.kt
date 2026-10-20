package com.ctrlcafe.scrapp.controlador

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.servicio.MotorFinanciero
import com.ctrlcafe.scrapp.util.Logger
import java.time.LocalDate

/**
 * Panel de Gerencia: lee las cifras de [MotorFinanciero] sobre los mismos
 * repositorios que usa MermaViewModel, así una merma nueva se refleja aquí.
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
            _productosCriticos.value = resumen.criticos.map {
                it.nombreProducto to (it.porcentajeDelTotal / 100.0).toFloat()
            }
        } catch (e: Exception) {
            Logger.error(GerenciaViewModel::class.java, "Error al cargar datos financieros: ${e.message}", e)
        }
    }
}
