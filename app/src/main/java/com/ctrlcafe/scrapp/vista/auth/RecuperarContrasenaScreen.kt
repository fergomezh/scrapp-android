package com.ctrlcafe.scrapp.vista.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ctrlcafe.scrapp.controlador.AuthEstadoUi
import com.ctrlcafe.scrapp.controlador.AuthViewModel
import com.ctrlcafe.scrapp.util.Validador

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecuperarContrasenaScreen(
    viewModel: AuthViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estadoUi by viewModel.estado.collectAsState()

    var correo by remember { mutableStateOf("") }
    var errorCorreo by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar Contraseña") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Restablece tu acceso",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ingresa tu correo institucional registrado y te enviaremos un enlace oficial para restablecer tu clave.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                is AuthEstadoUi.MensajeInfo -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = (estadoUi as AuthEstadoUi.MensajeInfo).mensaje,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            val cargando = estadoUi is AuthEstadoUi.Cargando

            Button(
                onClick = {
                    errorCorreo = Validador.validarCorreo(correo)
                    if (errorCorreo == null) {
                        viewModel.recuperarContrasena(correo)
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
                    Text("Enviar Correo de Recuperación")
                }
            }
        }
    }
}