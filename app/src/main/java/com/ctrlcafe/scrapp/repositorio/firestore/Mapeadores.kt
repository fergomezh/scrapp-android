package com.ctrlcafe.scrapp.repositorio.firestore

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Convierte un Timestamp de Firebase al LocalDate que usan los modelos de la app.
 */
fun Timestamp.aLocalDate(): LocalDate {
    return this.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}

/**
 * Convierte un LocalDate de la app al Timestamp que requiere Firebase.
 */
fun LocalDate.aTimestamp(): Timestamp {
    val date = Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
    return Timestamp(date)
}