package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.ctrlcafe.scrapp.servicio.RegistroMermaAtomico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

class FirestoreMermaRepositorio : Repositorio<Merma>, RegistroMermaAtomico {

    private val db = FirebaseFirestore.getInstance()
    private val coleccion = db.collection("mermas")

    private val _mermasFlow = MutableStateFlow<List<Merma>>(emptyList())
    val mermasFlow: StateFlow<List<Merma>> = _mermasFlow.asStateFlow()

    private val escucha = EscuchaFirestore<QuerySnapshot>("mermas") { snapshot ->
        _mermasFlow.value = snapshot.documents.map { it.aMerma() }
    }

    init {
        // Las reglas solo dejan al operativo leer sus propias mermas, y Firestore rechaza
        // completa una consulta sin ese filtro. Por eso la consulta depende del rol.
        SesionFirestore.alCambiarUsuario { uid ->
            escucha.detener()
            _mermasFlow.value = emptyList()
            if (uid == null) return@alCambiarUsuario

            db.collection("usuarios").document(uid).get()
                .addOnCompleteListener { tarea ->
                    // Si el perfil todavía no existe (recién registrado) se asume el rol con menos permisos
                    val esAdmin = tarea.isSuccessful && tarea.result?.getString("rol") == "ADMINISTRADOR"
                    // Si mientras tanto cambió la sesión, esta respuesta ya no aplica
                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@addOnCompleteListener
                    escucha.iniciar(if (esAdmin) coleccion else coleccion.whereEqualTo("usuarioId", uid))
                }
        }
    }

    /**
     * Mermas en tiempo real para el historial (Integrante 3).
     *
     * Con [usuarioId] se consulta solo lo registrado por ese usuario: las reglas no
     * dejan al operativo leer mermas ajenas, y una consulta sin ese filtro se rechaza
     * completa. Con `null` (administrador) se escucha toda la colección.
     * El orden por fecha se hace en el cliente para no requerir un índice compuesto.
     */
    fun observarMermas(usuarioId: String?): Flow<List<Merma>> = callbackFlow {
        val consulta = if (usuarioId == null) coleccion else coleccion.whereEqualTo("usuarioId", usuarioId)
        val listener = consulta.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map { it.aMerma() }.orEmpty())
        }
        awaitClose { listener.remove() }
    }

    override fun listar(): List<Merma> = _mermasFlow.value
    override fun buscarPorId(id: String): Merma? = _mermasFlow.value.find { it.id == id }

    override fun crear(entidad: Merma): Merma {
        val ref = if (entidad.id.isBlank()) coleccion.document() else coleccion.document(entidad.id)
        val nueva = entidad.copy(id = ref.id)
        ref.set(mapearDatos(nueva))
        agregarACopiaLocal(nueva)
        return nueva
    }

    /**
     * Transacción acordada con el Integrante 3 para OrquestadorMerma.
     * Garantiza que la merma se guarda Y el lote se descuenta al mismo tiempo.
     */
    override fun registrarMermaYDescontarLote(merma: Merma, nuevaCantidadDisponibleLote: Double): Merma {
        val batch = db.batch()

        // Respeta el id que ya trae la merma: OrquestadorMerma la busca por ese id justo después
        val refMerma = if (merma.id.isBlank()) coleccion.document() else coleccion.document(merma.id)
        val mermaFinal = merma.copy(id = refMerma.id)
        batch.set(refMerma, mapearDatos(mermaFinal))

        val refLote = db.collection("lotes").document(merma.loteId)
        batch.update(refLote, "cantidadDisponible", nuevaCantidadDisponibleLote)

        batch.commit()
        agregarACopiaLocal(mermaFinal)
        return mermaFinal
    }

    /**
     * El listener tarda un ciclo en reflejar la escritura, y los motores leen la copia
     * en memoria en el mismo instante. Se agrega aquí y el listener la reemplaza al llegar.
     */
    private fun agregarACopiaLocal(merma: Merma) {
        if (_mermasFlow.value.none { it.id == merma.id }) {
            _mermasFlow.value = _mermasFlow.value + merma
        }
    }

    private fun DocumentSnapshot.aMerma(): Merma {
        // Usamos runCatching por seguridad al mapear el Enum de la base de datos
        val causaDb = getString("causa") ?: ""
        val causaMapeada = runCatching { CausaMerma.valueOf(causaDb) }.getOrElse { CausaMerma.values().first() }

        return Merma(
            id = id,
            loteId = getString("loteId") ?: "",
            productoId = getString("productoId") ?: "",
            cantidad = getDouble("cantidad") ?: 0.0,
            costoUnitarioCongelado = getDouble("costoUnitarioCongelado") ?: 0.0,
            fecha = getTimestamp("fecha")?.aLocalDate() ?: LocalDate.now(),
            causa = causaMapeada,
            rutaEvidencia = getString("urlEvidencia") ?: "", // Firestore usa urlEvidencia
            usuarioId = getString("usuarioId") ?: "",
            nombreUsuario = getString("nombreUsuario") ?: "",
            creadoEn = getTimestamp("creadoEn")?.aLocalDate()
        )
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
