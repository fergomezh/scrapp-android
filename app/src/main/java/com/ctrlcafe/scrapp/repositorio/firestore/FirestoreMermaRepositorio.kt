package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class FirestoreMermaRepositorio : Repositorio<Merma> {

    private val db = FirebaseFirestore.getInstance()
    private val coleccion = db.collection("mermas")

    private val _mermasFlow = MutableStateFlow<List<Merma>>(emptyList())
    val mermasFlow: StateFlow<List<Merma>> = _mermasFlow.asStateFlow()

    init {
        coleccion.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            val lista = snapshot.documents.mapNotNull { doc ->
                // Usamos runCatching por seguridad al mapear el Enum de la base de datos
                val causaDb = doc.getString("causa") ?: ""
                val causaMapeada = runCatching { CausaMerma.valueOf(causaDb) }.getOrElse { CausaMerma.values().first() }

                Merma(
                    id = doc.id,
                    loteId = doc.getString("loteId") ?: "",
                    productoId = doc.getString("productoId") ?: "",
                    cantidad = doc.getDouble("cantidad") ?: 0.0,
                    costoUnitarioCongelado = doc.getDouble("costoUnitarioCongelado") ?: 0.0,
                    fecha = doc.getTimestamp("fecha")?.aLocalDate() ?: LocalDate.now(),
                    causa = causaMapeada,
                    rutaEvidencia = doc.getString("urlEvidencia") ?: "", // Firestore usa urlEvidencia
                    usuarioId = doc.getString("usuarioId") ?: "",
                    nombreUsuario = doc.getString("nombreUsuario") ?: "",
                    creadoEn = doc.getTimestamp("creadoEn")?.aLocalDate()
                )
            }
            _mermasFlow.value = lista
        }
    }

    override fun listar(): List<Merma> = _mermasFlow.value
    override fun buscarPorId(id: String): Merma? = _mermasFlow.value.find { it.id == id }

    override fun crear(entidad: Merma): Merma {
        val ref = if (entidad.id.isBlank()) coleccion.document() else coleccion.document(entidad.id)
        val nueva = entidad.copy(id = ref.id)
        ref.set(mapearDatos(nueva))
        return nueva
    }

    /**
     * Transacción acordada con el Integrante 3 para OrquestadorMerma.
     * Garantiza que la merma se guarda Y el lote se descuenta al mismo tiempo.
     */
    fun registrarMermaYDescontarLote(merma: Merma, nuevaCantidadDisponibleLote: Double) {
        val batch = db.batch()

        val refMerma = coleccion.document()
        val mermaFinal = merma.copy(id = refMerma.id)
        batch.set(refMerma, mapearDatos(mermaFinal))

        val refLote = db.collection("lotes").document(merma.loteId)
        batch.update(refLote, "cantidadDisponible", nuevaCantidadDisponibleLote)

        batch.commit()
    }

    private fun mapearDatos(merma: Merma) = hashMapOf(
        "loteId" to merma.loteId,
        "productoId" to merma.productoId,
        "cantidad" to merma.cantidad,
        "costoUnitarioCongelado" to merma.costoUnitarioCongelado,
        "fecha" to merma.fecha.aTimestamp(),
        "causa" to merma.causa.name,
        "urlEvidencia" to merma.rutaEvidencia,
        "usuarioId" to merma.usuarioId,
        "nombreUsuario" to merma.nombreUsuario,
        "creadoEn" to FieldValue.serverTimestamp()
    )

    override fun actualizar(id: String, entidad: Merma): Boolean = false
    override fun eliminar(id: String): Boolean = false
}