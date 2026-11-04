package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.modelo.Rol
import com.ctrlcafe.scrapp.repositorio.AuthRepositorio
import com.ctrlcafe.scrapp.util.Logger
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface UsuariosEstadoUi {
    object Inactivo : UsuariosEstadoUi
    object Cargando : UsuariosEstadoUi
    data class Error(val mensaje: String) : UsuariosEstadoUi
    data class Exito(val mensaje: String) : UsuariosEstadoUi
}

class UsuariosViewModel(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val coleccionUsuarios = db.collection(AuthRepositorio.COLECCION_USUARIOS)

    private val _estadoOperacion = MutableStateFlow<UsuariosEstadoUi>(UsuariosEstadoUi.Inactivo)
    val estadoOperacion: StateFlow<UsuariosEstadoUi> = _estadoOperacion.asStateFlow()

    val usuarios: StateFlow<List<PerfilUsuario>> = callbackFlow {
        val listener = coleccionUsuarios.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snapshot?.documents?.mapNotNull { doc ->
                PerfilUsuario(
                    uid = doc.id,
                    nombre = doc.getString("nombre").orEmpty(),
                    correo = doc.getString("correo").orEmpty(),
                    rol = Rol.desdeTexto(doc.getString("rol")),
                    activo = doc.getBoolean("activo") ?: true
                )
            }.orEmpty()
            trySend(lista)
        }
        awaitClose { listener.remove() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cambiarRol(usuario: PerfilUsuario, nuevoRol: Rol) {
        viewModelScope.launch {
            _estadoOperacion.value = UsuariosEstadoUi.Cargando
            try {
                coleccionUsuarios.document(usuario.uid).update("rol", nuevoRol.name).await()
                Logger.info(UsuariosViewModel::class.java, "Rol actualizado para ${usuario.uid}: $nuevoRol")
                _estadoOperacion.value = UsuariosEstadoUi.Exito("Rol cambiado correctamente a ${nuevoRol.name}.")
            } catch (e: Exception) {
                Logger.error(UsuariosViewModel::class.java, "Error al cambiar rol: ${e.message}", e)
                _estadoOperacion.value = UsuariosEstadoUi.Error("No se pudo cambiar el rol: ${e.message}")
            }
        }
    }

    fun alternarEstadoActivo(usuario: PerfilUsuario, uidAdminActual: String) {
        if (usuario.uid == uidAdminActual) {
            _estadoOperacion.value = UsuariosEstadoUi.Error("No puedes desactivar tu propia cuenta de administrador.")
            return
        }

        viewModelScope.launch {
            _estadoOperacion.value = UsuariosEstadoUi.Cargando
            try {
                val nuevoEstado = !usuario.activo
                coleccionUsuarios.document(usuario.uid).update("activo", nuevoEstado).await()
                val accion = if (nuevoEstado) "activado" else "desactivado"
                Logger.info(UsuariosViewModel::class.java, "Usuario ${usuario.uid} $accion")
                _estadoOperacion.value = UsuariosEstadoUi.Exito("Usuario $accion correctamente.")
            } catch (e: Exception) {
                Logger.error(UsuariosViewModel::class.java, "Error al cambiar estado activo: ${e.message}", e)
                _estadoOperacion.value = UsuariosEstadoUi.Error("No se pudo actualizar el estado: ${e.message}")
            }
        }
    }

    fun limpiarEstadoOperacion() {
        _estadoOperacion.value = UsuariosEstadoUi.Inactivo
    }
}