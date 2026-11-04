package com.ctrlcafe.scrapp.controlador

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ctrlcafe.scrapp.R
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.repositorio.AuthRepositorio
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.util.Logger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthEstadoUi {
    object Inactivo : AuthEstadoUi
    object Cargando : AuthEstadoUi
    data class Autenticado(val perfil: PerfilUsuario) : AuthEstadoUi
    data class Error(val mensaje: String) : AuthEstadoUi
    data class MensajeInfo(val mensaje: String) : AuthEstadoUi
    object CuentaDesactivada : AuthEstadoUi
}

class AuthViewModel(
    private val authRepositorio: AuthRepositorio = ContenedorDatos.authRepositorio
) : ViewModel() {

    private val _estado = MutableStateFlow<AuthEstadoUi>(AuthEstadoUi.Inactivo)
    val estado: StateFlow<AuthEstadoUi> = _estado.asStateFlow()

    private var jobObservadorCuenta: Job? = null

    init {
        verificarSesionActiva()
    }

    fun verificarSesionActiva() {
        viewModelScope.launch {
            _estado.value = AuthEstadoUi.Cargando
            val resultado = authRepositorio.perfilActual()
            resultado.fold(
                onSuccess = { perfil ->
                    if (perfil != null) {
                        _estado.value = AuthEstadoUi.Autenticado(perfil)
                        iniciarMonitoreoCuentaActiva()
                    } else {
                        _estado.value = AuthEstadoUi.Inactivo
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
                    iniciarMonitoreoCuentaActiva()
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
                    iniciarMonitoreoCuentaActiva()
                },
                onFailure = { error ->
                    _estado.value = AuthEstadoUi.Error(error.message ?: "Fallo al registrar usuario.")
                }
            )
        }
    }

    fun iniciarSesionConGoogle(contexto: Context) {
        viewModelScope.launch {
            _estado.value = AuthEstadoUi.Cargando
            try {
                val credManager = CredentialManager.create(contexto)
                val serverClientId = contexto.getString(R.string.default_web_client_id)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val respuesta = credManager.getCredential(contexto, request)
                val credencial = respuesta.credential

                if (credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credencial.data)
                    val idToken = googleCredential.idToken

                    val resultado = authRepositorio.autenticarConGoogle(idToken)
                    resultado.fold(
                        onSuccess = { perfil ->
                            _estado.value = AuthEstadoUi.Autenticado(perfil)
                            iniciarMonitoreoCuentaActiva()
                        },
                        onFailure = { error ->
                            _estado.value = AuthEstadoUi.Error(error.message ?: "Error al procesar cuenta de Google.")
                        }
                    )
                } else {
                    _estado.value = AuthEstadoUi.Error("Tipo de credencial no soportado.")
                }
            } catch (e: GetCredentialCancellationException) {
                _estado.value = AuthEstadoUi.Inactivo
            } catch (e: Exception) {
                Logger.error(AuthViewModel::class.java, "Error en Google Sign-In: ${e.message}", e)
                _estado.value = AuthEstadoUi.Error("Fallo en inicio con Google: ${e.message ?: "Error desconocido"}")
            }
        }
    }

    fun recuperarContrasena(correo: String) {
        viewModelScope.launch {
            _estado.value = AuthEstadoUi.Cargando
            val resultado = authRepositorio.recuperarContrasena(correo)
            resultado.fold(
                onSuccess = {
                    _estado.value = AuthEstadoUi.MensajeInfo("Se envió un correo de recuperación a $correo.")
                },
                onFailure = { error ->
                    _estado.value = AuthEstadoUi.Error(error.message ?: "Error al enviar correo de recuperación.")
                }
            )
        }
    }

    private fun iniciarMonitoreoCuentaActiva() {
        jobObservadorCuenta?.cancel()
        jobObservadorCuenta = viewModelScope.launch {
            authRepositorio.observarEstadoUsuarioActual().collect { activo ->
                if (!activo) {
                    authRepositorio.cerrarSesion()
                    _estado.value = AuthEstadoUi.CuentaDesactivada
                }
            }
        }
    }

    fun cerrarSesion(contexto: Context? = null) {
        viewModelScope.launch {
            jobObservadorCuenta?.cancel()
            authRepositorio.cerrarSesion(contexto)
            _estado.value = AuthEstadoUi.Inactivo
        }
    }

    fun limpiarError() {
        if (_estado.value is AuthEstadoUi.Error) {
            _estado.value = AuthEstadoUi.Inactivo
        }
    }

    fun limpiarEstado() {
        if (_estado.value !is AuthEstadoUi.Autenticado) {
            _estado.value = AuthEstadoUi.Inactivo
        }
    }
}