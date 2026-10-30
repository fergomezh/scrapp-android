package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirestoreProductoRepositorio : Repositorio<Producto> {

    private val db = FirebaseFirestore.getInstance()
    private val coleccion = db.collection("productos")

    // Copia local reactiva (StateFlow) exigida por el requerimiento 3.2
    private val _productosFlow = MutableStateFlow<List<Producto>>(emptyList())
    val productosFlow: StateFlow<List<Producto>> = _productosFlow.asStateFlow()

    init {
        // Listener en tiempo real: actualiza la copia en memoria automáticamente
        coleccion.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            val lista = snapshot.documents.mapNotNull { doc ->
                Producto(
                    id = doc.id,
                    nombre = doc.getString("nombre") ?: "",
                    categoria = doc.getString("categoria") ?: "",
                    costoUnitario = doc.getDouble("costoUnitario") ?: 0.0,
                    precioVenta = doc.getDouble("precioVenta") ?: 0.0,
                    unidadMedida = doc.getString("unidadMedida") ?: "",
                    activo = doc.getBoolean("activo") ?: true,
                    creadoPor = doc.getString("creadoPor") ?: "",
                    actualizadoEn = doc.getTimestamp("actualizadoEn")?.aLocalDate()
                )
            }
            _productosFlow.value = lista
        }
    }

    // Métodos síncronos de lectura que leen de la copia en memoria
    override fun listar(): List<Producto> {
        return _productosFlow.value
    }

    override fun buscarPorId(id: String): Producto? {
        return _productosFlow.value.find { it.id == id }
    }

    // Métodos de escritura asíncronos hacia Firestore
    override fun crear(entidad: Producto): Producto {
        val ref = if (entidad.id.isBlank()) coleccion.document() else coleccion.document(entidad.id)
        val nuevoProducto = entidad.copy(id = ref.id)

        val datos = hashMapOf(
            "nombre" to nuevoProducto.nombre,
            "categoria" to nuevoProducto.categoria,
            "costoUnitario" to nuevoProducto.costoUnitario,
            "precioVenta" to nuevoProducto.precioVenta,
            "unidadMedida" to nuevoProducto.unidadMedida,
            "activo" to nuevoProducto.activo,
            "creadoPor" to nuevoProducto.creadoPor,
            "actualizadoEn" to FieldValue.serverTimestamp() // Fecha del servidor solicitada
        )

        ref.set(datos)
        return nuevoProducto // Se retorna inmediato; el snapshot lo actualizará luego
    }

    override fun actualizar(id: String, entidad: Producto): Boolean {
        val datos = mapOf(
            "nombre" to entidad.nombre,
            "categoria" to entidad.categoria,
            "costoUnitario" to entidad.costoUnitario,
            "precioVenta" to entidad.precioVenta,
            "unidadMedida" to entidad.unidadMedida,
            "activo" to entidad.activo,
            "actualizadoEn" to FieldValue.serverTimestamp()
        )
        coleccion.document(id).update(datos)
        return true
    }

    override fun eliminar(id: String): Boolean {
        // Según el documento (3.4 y tareas I3), los productos se desactivan, no se borran físicamente si tienen mermas.
        // Pero mantenemos la firma de la interfaz funcional.
        coleccion.document(id).delete()
        return true
    }
}