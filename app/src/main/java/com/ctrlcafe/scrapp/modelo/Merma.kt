package com.ctrlcafe.scrapp.modelo

import java.time.LocalDate

data class Merma(
    val id: String,
    val loteId: String,
    val productoId: String,
    val cantidad: Double,
    val costoUnitarioCongelado: Double,
    val fecha: LocalDate,
    val causa: CausaMerma,
    val rutaEvidencia: String, // En Firestore se mapeará como urlEvidencia
    val usuarioId: String,
    // Nuevos campos requeridos por Firestore (desnormalización de nombre)
    val nombreUsuario: String = "",
    val creadoEn: LocalDate? = null
)