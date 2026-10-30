package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class FirestoreLoteRepositorio : Repositorio<Lote> {

    private val db = FirebaseFirestore.getInstance()
    private val coleccion = db.collection("lotes")

    private val _lotesFlow = MutableStateFlow<List<Lote>>(emptyList())
    val lotesFlow: StateFlow<List<Lote>> = _lotesFlow.asStateFlow()

    init {
        coleccion.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            val lista = snapshot.documents.mapNotNull { doc ->
                Lote(
                    id = doc.id,
                    productoId = doc.getString("productoId") ?: "",
                    cantidadInicial = doc.getDouble("cantidadInicial") ?: 0.0,
                    cantidadDisponible = doc.getDouble("cantidadDisponible") ?: 0.0,
                    costoUnitario = doc.getDouble("costoUnitario") ?: 0.0,
                    fechaIngreso = doc.getTimestamp("fechaIngreso")?.aLocalDate() ?: LocalDate.now(),
                    vidaUtilDias = doc.getLong("vidaUtilDias")?.toInt() ?: 0,
                    fechaCaducidad = doc.getTimestamp("fechaCaducidad")?.aLocalDate() ?: LocalDate.now(),
                    creadoPor = doc.getString("creadoPor") ?: "",
                    creadoEn = doc.getTimestamp("creadoEn")?.aLocalDate()
                )
            }
            _lotesFlow.value = lista
        }
    }

    override fun listar(): List<Lote> {
        return _lotesFlow.value
    }

    override fun buscarPorId(id: String): Lote? {
        return _lotesFlow.value.find { it.id == id }
    }

    override fun crear(entidad: Lote): Lote {
        val ref = if (entidad.id.isBlank()) coleccion.document() else coleccion.document(entidad.id)
        val nuevoLote = entidad.copy(id = ref.id)

        val datos = hashMapOf(
            "productoId" to nuevoLote.productoId,
            "cantidadInicial" to nuevoLote.cantidadInicial,
            "cantidadDisponible" to nuevoLote.cantidadDisponible,
            "costoUnitario" to nuevoLote.costoUnitario,
            "fechaIngreso" to nuevoLote.fechaIngreso.aTimestamp(),
            "vidaUtilDias" to nuevoLote.vidaUtilDias,
            "fechaCaducidad" to nuevoLote.fechaCaducidad.aTimestamp(),
            "creadoPor" to nuevoLote.creadoPor,
            "creadoEn" to FieldValue.serverTimestamp()
        )

        ref.set(datos)
        return nuevoLote
    }

    override fun actualizar(id: String, entidad: Lote): Boolean {
        val datos = mapOf(
            "cantidadDisponible" to entidad.cantidadDisponible
            // Nota: Según la rúbrica (sección 3.4), el operativo solo puede actualizar la cantidadDisponible del lote al registrar merma.
        )
        coleccion.document(id).update(datos)
        return true
    }

    override fun eliminar(id: String): Boolean {
        coleccion.document(id).delete()
        return true
    }
}