package com.ctrlcafe.scrapp.modelo

import java.time.LocalDate

data class RegistroVenta(
    val id: String,
    val productoId: String,
    val cantidad: Double,
    val fecha: LocalDate
)