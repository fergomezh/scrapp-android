package com.ctrlcafe.scrapp.repositorio

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.modelo.Rol
import com.ctrlcafe.scrapp.util.ContrasenaDebilException
import com.ctrlcafe.scrapp.util.CorreoEnUsoException
import com.ctrlcafe.scrapp.util.CredencialesInvalidasException
import com.ctrlcafe.scrapp.util.ErrorAutenticacionException
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.PerfilNoEncontradoException
import com.ctrlcafe.scrapp.util.ScrappException
import com.ctrlcafe.scrapp.util.SinConexionException
import com.ctrlcafe.scrapp.util.UsuarioDesactivadoException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Autenticación con Firebase Auth + perfil y rol en Firestore (`usuarios/{uid}`).
 * Actualizado para Etapa 3, Avance 2: soporte de Google Sign-In, recuperación
 * de clave y sincronización en tiempo real de cuentas activas/inactivas.
 */
class AuthRepositorio(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usuarios get() = db.collection(COLECCION_USUARIOS)

    val usuarioActual: FirebaseUser? get() = auth.currentUser
    val haySesion: Boolean get() = auth.currentUser != null

    /**
     * Inicia sesión tradicional con correo y contraseña.
     */
    suspend fun iniciarSesion(correo: String, contrasena: String): Result<PerfilUsuario> = ejecutar("iniciarSesion") {
        val resultado = auth.signInWithEmailAndPassword(correo.trim(), contrasena).await()
        val uid = resultado.user?.uid ?: throw CredencialesInvalidasException()
        val perfil = leerPerfil(uid)
        if (!perfil.activo) {
            auth.signOut()
            throw UsuarioDesactivadoException()
        }
        Logger.info(AuthRepositorio::class.java, "Sesión iniciada con correo: $uid (${perfil.rol})")
        perfil
    }

    /**
     * Registra un nuevo usuario con correo y contraseña. Todo usuario nuevo inicia como OPERATIVO.
     */
    suspend fun registrar(nombre: String, correo: String, contrasena: String): Result<PerfilUsuario> = ejecutar("registrar") {
        val resultado = auth.createUserWithEmailAndPassword(correo.trim(), contrasena).await()
        val usuario = resultado.user ?: throw ErrorAutenticacionException("No se creó el usuario")

        val perfil = PerfilUsuario(
            uid = usuario.uid,
            nombre = nombre.trim(),
            correo = correo.trim(),
            rol = Rol.OPERATIVO,
            activo = true
        )

        try {
            val mapa = aMapa(perfil).toMutableMap().apply {
                put(CAMPO_PROVEEDOR, "password")
                put(CAMPO_CREADO_EN, FieldValue.serverTimestamp())
            }
            usuarios.document(perfil.uid).set(mapa).await()
            usuario.updateProfile(userProfileChangeRequest { displayName = perfil.nombre }).await()
        } catch (e: Exception) {
            runCatching { usuario.delete().await() }
            throw e
        }

        Logger.info(AuthRepositorio::class.java, "Usuario registrado: ${perfil.uid}")
        perfil
    }

    /**
     * Autenticación con Google vía Credential Manager. Si no existe en Firestore,
     * crea el perfil con rol OPERATIVO y proveedor "google".
     */
    suspend fun autenticarConGoogle(idToken: String): Result<PerfilUsuario> = ejecutar("autenticarConGoogle") {
        val credencial = GoogleAuthProvider.getCredential(idToken, null)
        val resultado = auth.signInWithCredential(credencial).await()
        val usuario = resultado.user ?: throw ErrorAutenticacionException("Fallo al autenticar con Google")

        val docRef = usuarios.document(usuario.uid)
        val doc = docRef.get().await()

        val perfil: PerfilUsuario
        if (!doc.exists()) {
            perfil = PerfilUsuario(
                uid = usuario.uid,
                nombre = usuario.displayName ?: "Usuario Google",
                correo = usuario.email ?: "",
                rol = Rol.OPERATIVO,
                activo = true
            )
            val mapa = mapOf(
                CAMPO_NOMBRE to perfil.nombre,
                CAMPO_CORREO to perfil.correo,
                CAMPO_ROL to perfil.rol.name,
                CAMPO_ACTIVO to perfil.activo,
                CAMPO_PROVEEDOR to "google",
                CAMPO_CREADO_EN to FieldValue.serverTimestamp()
            )
            docRef.set(mapa).await()
            Logger.info(AuthRepositorio::class.java, "Perfil nuevo creado desde Google: ${usuario.uid}")
        } else {
            perfil = desdeDocumento(doc)
            if (!perfil.activo) {
                auth.signOut()
                throw UsuarioDesactivadoException()
            }
        }
        perfil
    }

    /**
     * Envía correo de recuperación de contraseña de Firebase.
     */
    suspend fun recuperarContrasena(correo: String): Result<Unit> = ejecutar("recuperarContrasena") {
        auth.sendPasswordResetEmail(correo.trim()).await()
        Logger.info(AuthRepositorio::class.java, "Correo de recuperación enviado a: $correo")
    }

    /**
     * Cierra la sesión en Firebase y limpia el estado del Credential Manager para Google.
     */
    suspend fun cerrarSesion(contexto: Context? = null) {
        val uid = auth.currentUser?.uid
        auth.signOut()
        if (contexto != null) {
            runCatching {
                val credManager = CredentialManager.create(contexto)
                credManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
        Logger.info(AuthRepositorio::class.java, "Sesión cerrada: $uid")
    }

    /**
     * Escucha en tiempo real si el usuario actual fue desactivado por el administrador.
     */
    fun observarEstadoUsuarioActual(): Flow<Boolean> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = usuarios.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val activo = snapshot?.getBoolean(CAMPO_ACTIVO) ?: true
            trySend(activo)
        }

        awaitClose { listener.remove() }
    }

    suspend fun obtenerPerfil(uid: String): Result<PerfilUsuario> = ejecutar("obtenerPerfil") {
        leerPerfil(uid)
    }

    suspend fun obtenerRol(uid: String): Result<Rol> = ejecutar("obtenerRol") {
        leerPerfil(uid).rol
    }

    suspend fun perfilActual(): Result<PerfilUsuario?> {
        val uid = auth.currentUser?.uid ?: return Result.success(null)
        return obtenerPerfil(uid)
    }

    private suspend fun leerPerfil(uid: String): PerfilUsuario {
        val doc = usuarios.document(uid).get().await()
        if (!doc.exists()) throw PerfilNoEncontradoException()
        return desdeDocumento(doc)
    }

    private suspend fun <T> ejecutar(operacion: String, bloque: suspend () -> T): Result<T> =
        try {
            Result.success(bloque())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = traducir(e)
            Logger.error(AuthRepositorio::class.java, "Falló $operacion: ${error.message}", e)
            Result.failure(error)
        }

    private fun traducir(e: Exception): ScrappException = when (e) {
        is ScrappException -> e
        is FirebaseNetworkException -> SinConexionException()
        is FirebaseAuthWeakPasswordException -> ContrasenaDebilException()
        is FirebaseAuthInvalidCredentialsException -> CredencialesInvalidasException()
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") UsuarioDesactivadoException()
            else CredencialesInvalidasException()
        is FirebaseAuthUserCollisionException -> CorreoEnUsoException()
        is FirebaseFirestoreException ->
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) SinConexionException()
            else ErrorAutenticacionException(e.message)
        else -> ErrorAutenticacionException(e.message)
    }

    companion object {
        const val COLECCION_USUARIOS = "usuarios"
        private const val CAMPO_NOMBRE = "nombre"
        private const val CAMPO_CORREO = "correo"
        private const val CAMPO_ROL = "rol"
        private const val CAMPO_ACTIVO = "activo"
        private const val CAMPO_PROVEEDOR = "proveedor"
        private const val CAMPO_CREADO_EN = "creadoEn"

        private fun aMapa(perfil: PerfilUsuario): Map<String, Any> = mapOf(
            CAMPO_NOMBRE to perfil.nombre,
            CAMPO_CORREO to perfil.correo,
            CAMPO_ROL to perfil.rol.name,
            CAMPO_ACTIVO to perfil.activo
        )

        private fun desdeDocumento(doc: DocumentSnapshot) = PerfilUsuario(
            uid = doc.id,
            nombre = doc.getString(CAMPO_NOMBRE).orEmpty(),
            correo = doc.getString(CAMPO_CORREO).orEmpty(),
            rol = Rol.desdeTexto(doc.getString(CAMPO_ROL)),
            activo = doc.getBoolean(CAMPO_ACTIVO) ?: true
        )
    }
}