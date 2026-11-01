package com.ctrlcafe.scrapp.vista.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ctrlcafe.scrapp.controlador.AuthEstadoUi
import com.ctrlcafe.scrapp.controlador.AuthViewModel
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.util.Validador

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginExitoso: (PerfilUsuario) -> Unit,
    onIrARegistro: () -> Unit,
    onIrARecuperarContrasena: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val estadoUi by viewModel.estado.collectAsState()

    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mostrarContrasena by remember { mutableStateOf(false) }

    var errorCorreo by remember { mutableStateOf<String?>(null) }
    var errorContrasena by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(estadoUi) {
        if (estadoUi is AuthEstadoUi.Autenticado) {
            onLoginExitoso((estadoUi as AuthEstadoUi.Autenticado).perfil)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Scrapp",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Control de mermas e inventario",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            when (estadoUi) {
                is AuthEstadoUi.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = (estadoUi as AuthEstadoUi.Error).mensaje,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is AuthEstadoUi.CuentaDesactivada -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Tu cuenta ha sido desactivada por un administrador.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                else -> {}
            }

            OutlinedTextField(
                value = correo,
                onValueChange = {
                    correo = it
                    if (errorCorreo != null) errorCorreo = Validador.validarCorreo(it)
                },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                isError = errorCorreo != null,
                supportingText = { errorCorreo?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = contrasena,
                onValueChange = {
                    contrasena = it
                    if (errorContrasena != null) errorContrasena = Validador.validarContrasena(it)
                },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { mostrarContrasena = !mostrarContrasena }) {
                        Icon(
                            imageVector = if (mostrarContrasena) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (mostrarContrasena) "Ocultar" else "Mostrar"
                        )
                    }
                },
                visualTransformation = if (mostrarContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                isError = errorContrasena != null,
                supportingText = { errorContrasena?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onIrARecuperarContrasena) {
                    Text("¿Olvidaste tu contraseña?", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val cargando = estadoUi is AuthEstadoUi.Cargando

            Button(
                onClick = {
                    errorCorreo = Validador.validarCorreo(correo)
                    errorContrasena = Validador.validarContrasena(contrasena)

                    if (errorCorreo == null && errorContrasena == null) {
                        viewModel.limpiarEstado()
                        viewModel.iniciarSesion(correo, contrasena)
                    }
                },
                enabled = !cargando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (cargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Iniciar Sesión")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "O bien",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón oficial de Google Sign-In con Credential Manager
            OutlinedButton(
                onClick = {
                    viewModel.limpiarEstado()
                    viewModel.iniciarSesionConGoogle(context)
                },
                enabled = !cargando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Continuar con Google")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onIrARegistro,
                enabled = !cargando
            ) {
                Text("¿No tienes cuenta? Regístrate aquí")
            }
        }
    }
}