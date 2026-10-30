package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.RegistroVenta
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class FirestoreVentaRepositorio : Repositorio<RegistroVenta> {

    private val db = FirebaseFirestore.getInstance()
    private val coleccion = db.collection("ventas")

    private val _ventasFlow = MutableStateFlow<List<RegistroVenta>>(emptyList())
    val ventasFlow: StateFlow<List<RegistroVenta>> = _ventasFlow.asStateFlow()

    init {
        coleccion.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            val lista = snapshot.documents.mapNotNull { doc ->
                RegistroVenta(
                    id = doc.id,
                    productoId = doc.getString("productoId") ?: "",
                    cantidad = doc.getDouble("cantidad") ?: 0.0,
                    fecha = doc.getTimestamp("fecha")?.aLocalDate() ?: LocalDate.now(),
                    creadoPor = doc.getString("creadoPor") ?: ""
                )
            }
            _ventasFlow.value = lista
        }
    }

    override fun listar(): List<RegistroVenta> = _ventasFlow.value
    override fun buscarPorId(id: String): RegistroVenta? = _ventasFlow.value.find { it.id == id }

    override fun crear(entidad: RegistroVenta): RegistroVenta {
        val ref = if (entidad.id.isBlank()) coleccion.document() else coleccion.document(entidad.id)
        val nueva = entidad.copy(id = ref.id)
        ref.set(hashMapOf(
            "productoId" to nueva.productoId,
            "cantidad" to nueva.cantidad,
            "fecha" to nueva.fecha.aTimestamp(),
            "creadoPor" to nueva.creadoPor,
            "creadoEn" to FieldValue.serverTimestamp()
        ))
        return nueva
    }

    override fun actualizar(id: String, entidad: RegistroVenta): Boolean = false
    override fun eliminar(id: String): Boolean = false
}