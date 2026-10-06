package com.ctrlcafe.scrapp.repositorio

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
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Autenticación con Firebase Auth + perfil y rol en Firestore (`usuarios/{uid}`).
 *
 * Todas las operaciones devuelven [Result]. En caso de fallo, la excepción es
 * siempre una [ScrappException] con el mensaje listo para mostrar en pantalla,
 * así el ViewModel no necesita conocer las clases de Firebase.
 *
 * La sesión la persiste Firebase: si [usuarioActual] no es null al abrir la app,
 * no hace falta volver a pedir login.
 */
class AuthRepositorio(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usuarios get() = db.collection(COLECCION_USUARIOS)

    /** Usuario con sesión abierta, o null si nadie ha iniciado sesión. */
    val usuarioActual: FirebaseUser? get() = auth.currentUser

    val haySesion: Boolean get() = auth.currentUser != null

    /**
     * Inicia sesión y devuelve el perfil. Si el usuario está desactivado en
     * Firestore, cierra la sesión y falla con [UsuarioDesactivadoException].
     */
    suspend fun iniciarSesion(correo: String, contrasena: String): Result<PerfilUsuario> = ejecutar("iniciarSesion") {
        val resultado = auth.signInWithEmailAndPassword(correo.trim(), contrasena).await()
        val uid = resultado.user?.uid ?: throw CredencialesInvalidasException()
        val perfil = leerPerfil(uid)
        if (!perfil.activo) {
            auth.signOut()
            throw UsuarioDesactivadoException()
        }
        Logger.info(AuthRepositorio::class.java, "Sesión iniciada: $uid (${perfil.rol})")
        perfil
    }

    /**
     * Crea la cuenta y su documento en Firestore. Todo usuario nuevo es
     * [Rol.OPERATIVO]; solo un administrador puede cambiar el rol después.
     */
    suspend fun registrar(nombre: String, correo: String, contrasena: String): Result<PerfilUsuario> = ejecutar("registrar") {
        val resultado = auth.createUserWithEmailAndPassword(correo.trim(), contrasena).await()
        val usuario = resultado.user ?: throw ErrorAutenticacionException("no se creó el usuario")

        val perfil = PerfilUsuario(
            uid = usuario.uid,
            nombre = nombre.trim(),
            correo = correo.trim(),
            rol = Rol.OPERATIVO,
            activo = true
        )

        try {
            usuarios.document(perfil.uid).set(aMapa(perfil)).await()
            usuario.updateProfile(userProfileChangeRequest { displayName = perfil.nombre }).await()
        } catch (e: Exception) {
            // Sin documento en Firestore la cuenta quedaría sin rol: la borramos
            // para que el usuario pueda volver a registrarse con el mismo correo.
            runCatching { usuario.delete().await() }
            throw e
        }

        Logger.info(AuthRepositorio::class.java, "Usuario registrado: ${perfil.uid}")
        perfil
    }

    fun cerrarSesion() {
        Logger.info(AuthRepositorio::class.java, "Sesión cerrada: ${auth.currentUser?.uid}")
        auth.signOut()
    }

    /** Perfil completo del usuario indicado. */
    suspend fun obtenerPerfil(uid: String): Result<PerfilUsuario> = ejecutar("obtenerPerfil") {
        leerPerfil(uid)
    }

    /** Rol guardado en `usuarios/{uid}`. */
    suspend fun obtenerRol(uid: String): Result<Rol> = ejecutar("obtenerRol") {
        leerPerfil(uid).rol
    }

    /**
     * Perfil del usuario con sesión abierta, para decidir la pantalla inicial
     * al abrir la app. Devuelve éxito con null si no hay sesión.
     */
    suspend fun perfilActual(): Result<PerfilUsuario?> {
        val uid = auth.currentUser?.uid ?: return Result.success(null)
        return obtenerPerfil(uid)
    }

    private suspend fun leerPerfil(uid: String): PerfilUsuario {
        val doc = usuarios.document(uid).get().await()
        if (!doc.exists()) throw PerfilNoEncontradoException()
        return desdeDocumento(doc)
    }

    /** Ejecuta [bloque] y convierte cualquier error en una [ScrappException]. */
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
        // WeakPassword hereda de InvalidCredentials: debe ir antes.
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
