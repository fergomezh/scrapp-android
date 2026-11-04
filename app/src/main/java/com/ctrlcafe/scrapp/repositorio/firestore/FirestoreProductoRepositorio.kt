package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirestoreProductoRepositorio : Repositorio<Producto> {

    private val db = FirebaseFirestore.getInstance()
    private val coleccion = db.collection("productos")

    // Copia local reactiva (StateFlow) exigida por el requerimiento 3.2
    private val _productosFlow = MutableStateFlow<List<Producto>>(emptyList())
    val productosFlow: StateFlow<List<Producto>> = _productosFlow.asStateFlow()

    // Listener en tiempo real: actualiza la copia en memoria automáticamente
    private val escucha = EscuchaFirestore<QuerySnapshot>("productos") { snapshot ->
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

    init {
        // Se reabre con cada sesión: sin usuario activo las reglas rechazan la lectura
        SesionFirestore.alCambiarUsuario { uid ->
            if (uid == null) {
                escucha.detener()
                _productosFlow.value = emptyList()
            } else {
                escucha.iniciar(coleccion)
            }
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

    /**
     * Crea o actualiza el producto y suspende hasta que el servidor confirma, para que
     * CatalogoProductosScreen muestre la carga y los errores. Sin conexión no termina
     * nunca: quien lo llama debe ponerle un tiempo límite.
     */
    suspend fun guardarConfirmado(entidad: Producto, esNuevo: Boolean): Producto {
        val ref = if (entidad.id.isBlank()) coleccion.document() else coleccion.document(entidad.id)
        val producto = entidad.copy(id = ref.id)
        val datos = hashMapOf<String, Any>(
            "nombre" to producto.nombre,
            "categoria" to producto.categoria,
            "costoUnitario" to producto.costoUnitario,
            "precioVenta" to producto.precioVenta,
            "unidadMedida" to producto.unidadMedida,
            "activo" to producto.activo,
            "actualizadoEn" to FieldValue.serverTimestamp()
        )
        if (esNuevo) {
            datos["creadoPor"] = producto.creadoPor
            ref.set(datos).await()
        } else {
            ref.update(datos).await()
        }
        return producto
    }

    override fun eliminar(id: String): Boolean {
        // Según el documento (3.4 y tareas I3), los productos se desactivan, no se borran físicamente si tienen mermas.
        // Pero mantenemos la firma de la interfaz funcional.
        coleccion.document(id).delete()
        return true
    }
}