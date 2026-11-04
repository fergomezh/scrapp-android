package com.ctrlcafe.scrapp.modelo

import java.time.LocalDate

data class Producto(
    val id: String,
    val nombre: String,
    val categoria: String,
    val costoUnitario: Double,
    val precioVenta: Double,
    val unidadMedida: String,
    // Nuevos campos requeridos por Firestore
    val activo: Boolean = true,
    val creadoPor: String = "",
    val actualizadoEn: LocalDate? = null
)