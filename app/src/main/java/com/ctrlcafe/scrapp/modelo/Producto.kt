package com.ctrlcafe.scrapp.modelo

data class Producto(
    val id: String,
    val nombre: String,
    val categoria: String,
    val costoUnitario: Double,
    val precioVenta: Double,
    val unidadMedida: String
)