package com.ctrlcafe.scrapp.controlador

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

class GerenciaViewModel {
    // --- DATOS CONTROLADOS FIJOS PARA TU PREVIEW ---
    private val _perdidaDia = mutableStateOf(120.50)
    val perdidaDia: State<Double> = _perdidaDia

    private val _perdidaMes = mutableStateOf(1450.00)
    val perdidaMes: State<Double> = _perdidaMes

    private val _indiceMerma = mutableStateOf(0.085) // 8.5%
    val indiceMerma: State<Double> = _indiceMerma

    private val _productosCriticos = mutableStateOf(
        listOf(
            Pair("Leche Entera", 0.85f),
            Pair("Queso Mozzarella", 0.62f),
            Pair("Harina Suave", 0.45f),
            Pair("Café en Grano", 0.30f),
            Pair("Azúcar Blanco", 0.15f)
        )
    )
    val productosCriticos: State<List<Pair<String, Float>>> = _productosCriticos

    fun cargarDatosFinancieros() {
        // Reservado para la integración final del Avance 2
    }
}
