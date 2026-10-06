package com.ctrlcafe.scrapp.modelo

import java.time.LocalDate

data class Lote(
    val id: String,
    val productoId: String,
    val cantidadInicial: Double,
    var cantidadDisponible: Double,
    val costoUnitario: Double,
    val fechaIngreso: LocalDate,
    val vidaUtilDias: Int,
    val fechaCaducidad: LocalDate = fechaIngreso.plusDays(vidaUtilDias.toLong()),
    var estado: EstadoCaducidad = EstadoCaducidad.Vigente
)