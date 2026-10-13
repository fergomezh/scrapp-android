package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.repositorio.AuthRepositorio
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthEstadoUi {
    object Inactivo : AuthEstadoUi
    object Cargando : AuthEstadoUi
    data class Autenticado(val perfil: PerfilUsuario) : AuthEstadoUi
    data class Error(val mensaje: String) : AuthEstadoUi
}

class AuthViewModel(
    private val authRepositorio: AuthRepositorio = ContenedorDatos.authRepositorio
) : ViewModel() {

    private val _estado = MutableStateFlow<AuthEstadoUi>(AuthEstadoUi.Inactivo)
    val estado: StateFlow<AuthEstadoUi> = _estado.asStateFlow()

    init {
        verificarSesionActiva()
    }

    fun verificarSesionActiva() {
        viewModelScope.launch {
            _estado.value = AuthEstadoUi.Cargando
            val resultado = authRepositorio.perfilActual()
            resultado.fold(
                onSuccess = { perfil ->
                    _estado.value = if (perfil != null) {
                        AuthEstadoUi.Autenticado(perfil)
                    } else {
                        AuthEstadoUi.Inactivo
                    }
                },
                onFailure = { error ->
                    _estado.value = AuthEstadoUi.Error(error.message ?: "Error al restaurar sesión.")
                }
            )
        }
    }

    fun iniciarSesion(correo: String, contrasena: String) {
        viewModelScope.launch {
            _estado.value = AuthEstadoUi.Cargando
            val resultado = authRepositorio.iniciarSesion(correo, contrasena)
            resultado.fold(
                onSuccess = { perfil ->
                    _estado.value = AuthEstadoUi.Autenticado(perfil)
                },
                onFailure = { error ->
                    _estado.value = AuthEstadoUi.Error(error.message ?: "Fallo al iniciar sesión.")
                }
            )
        }
    }

    fun registrar(nombre: String, correo: String, contrasena: String) {
        viewModelScope.launch {
            _estado.value = AuthEstadoUi.Cargando
            val resultado = authRepositorio.registrar(nombre, correo, contrasena)
            resultado.fold(
                onSuccess = { perfil ->
                    _estado.value = AuthEstadoUi.Autenticado(perfil)
                },
                onFailure = { error ->
                    _estado.value = AuthEstadoUi.Error(error.message ?: "Fallo al registrar usuario.")
                }
            )
        }
    }

    fun cerrarSesion() {
        authRepositorio.cerrarSesion()
        _estado.value = AuthEstadoUi.Inactivo
    }

    fun limpiarError() {
        if (_estado.value is AuthEstadoUi.Error) {
            _estado.value = AuthEstadoUi.Inactivo
        }
    }
}