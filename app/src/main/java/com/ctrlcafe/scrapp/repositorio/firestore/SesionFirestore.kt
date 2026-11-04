package com.ctrlcafe.scrapp.repositorio.firestore

import android.os.Handler
import android.os.Looper
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

/**
 * Conecta los listeners de Firestore a la sesión de Firebase Auth.
 *
 * `ContenedorDatos` se crea antes del login (lo usa `AuthViewModel`), y las reglas
 * exigen un usuario activo para leer. Un listener abierto sin sesión falla con
 * PERMISSION_DENIED y Firestore lo cierra para siempre. Por eso cada repositorio
 * (re)abre sus listeners aquí, cada vez que entra, sale o cambia el usuario.
 */
object SesionFirestore {

    private val auth = FirebaseAuth.getInstance()

    /**
     * Ejecuta [accion] con el uid actual al registrarse y luego cada vez que cambia
     * (`null` al cerrar sesión). Corre en el hilo principal.
     */
    fun alCambiarUsuario(accion: (uid: String?) -> Unit) {
        var ultimoUid: String? = null
        var primeraVez = true
        auth.addAuthStateListener { estado ->
            val uid = estado.currentUser?.uid
            if (primeraVez || uid != ultimoUid) {
                primeraVez = false
                ultimoUid = uid
                accion(uid)
            }
        }
    }
}

/**
 * Un listener que se puede reabrir con otra consulta y que reintenta ante
 * PERMISSION_DENIED: justo después de registrarse, la sesión existe pero el
 * documento `usuarios/{uid}` todavía no, y las reglas rechazan la lectura.
 */
class EscuchaFirestore<T>(
    private val nombre: String,
    private val alRecibir: (T) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registro: ListenerRegistration? = null

    // Cada inicio o detención invalida las respuestas y reintentos de la conexión anterior
    private var generacion = 0

    fun iniciar(conectar: (alSnapshot: (T?, FirebaseFirestoreException?) -> Unit) -> ListenerRegistration) {
        detener()
        val gen = generacion
        abrir(conectar, gen, intento = 0)
    }

    fun detener() {
        generacion++
        handler.removeCallbacksAndMessages(null)
        registro?.remove()
        registro = null
    }

    private fun abrir(
        conectar: (alSnapshot: (T?, FirebaseFirestoreException?) -> Unit) -> ListenerRegistration,
        gen: Int,
        intento: Int
    ) {
        registro = conectar { valor, error ->
            if (gen != generacion) return@conectar
            if (error != null) {
                Logger.registrar(
                    NivelLog.WARN,
                    EscuchaFirestore::class.java,
                    "Listener de $nombre falló (intento ${intento + 1}): ${error.code}"
                )
                registro?.remove()
                registro = null
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && intento < MAX_REINTENTOS) {
                    handler.postDelayed(
                        { if (gen == generacion) abrir(conectar, gen, intento + 1) },
                        ESPERA_BASE_MS * (intento + 1)
                    )
                }
                return@conectar
            }
            if (valor != null) alRecibir(valor)
        }
    }

    companion object {
        private const val MAX_REINTENTOS = 5
        private const val ESPERA_BASE_MS = 1_500L
    }
}

/** Abre la escucha sobre una consulta o colección. */
fun EscuchaFirestore<QuerySnapshot>.iniciar(consulta: Query) =
    iniciar { alSnapshot -> consulta.addSnapshotListener { snap, error -> alSnapshot(snap, error) } }

/** Abre la escucha sobre un documento. */
fun EscuchaFirestore<DocumentSnapshot>.iniciar(documento: DocumentReference) =
    iniciar { alSnapshot -> documento.addSnapshotListener { snap, error -> alSnapshot(snap, error) } }
