package com.ctrlcafe.scrapp.vista.administracion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ctrlcafe.scrapp.controlador.UsuariosEstadoUi
import com.ctrlcafe.scrapp.controlador.UsuariosViewModel
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.modelo.Rol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionUsuariosScreen(
    viewModel: UsuariosViewModel,
    uidAdminActual: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usuarios by viewModel.usuarios.collectAsState()
    val estadoOperacion by viewModel.estadoOperacion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var usuarioParaCambioRol by remember { mutableStateOf<PerfilUsuario?>(null) }
    var usuarioParaCambioEstado by remember { mutableStateOf<PerfilUsuario?>(null) }

    LaunchedEffect(estadoOperacion) {
        when (estadoOperacion) {
            is UsuariosEstadoUi.Exito -> {
                snackbarHostState.showSnackbar((estadoOperacion as UsuariosEstadoUi.Exito).mensaje)
                viewModel.limpiarEstadoOperacion()
            }
            is UsuariosEstadoUi.Error -> {
                snackbarHostState.showSnackbar((estadoOperacion as UsuariosEstadoUi.Error).mensaje)
                viewModel.limpiarEstadoOperacion()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (usuarios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(usuarios, key = { it.uid }) { usuario ->
                        TarjetaUsuarioItem(
                            usuario = usuario,
                            esElMismoAdmin = usuario.uid == uidAdminActual,
                            onCambiarRolClick = { usuarioParaCambioRol = usuario },
                            onToggleActivo = { usuarioParaCambioEstado = usuario }
                        )
                    }
                }
            }

            if (estadoOperacion is UsuariosEstadoUi.Cargando) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Diálogo modal para confirmar cambio de Rol
    usuarioParaCambioRol?.let { usuario ->
        val nuevoRol = if (usuario.rol == Rol.ADMINISTRADOR) Rol.OPERATIVO else Rol.ADMINISTRADOR
        AlertDialog(
            onDismissRequest = { usuarioParaCambioRol = null },
            title = { Text("Cambiar Rol") },
            text = { Text("¿Deseas cambiar el rol de ${usuario.nombre} a ${nuevoRol.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cambiarRol(usuario, nuevoRol)
                    usuarioParaCambioRol = null
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioParaCambioRol = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo modal para confirmar activación o desactivación de cuenta
    usuarioParaCambioEstado?.let { usuario ->
        val accion = if (usuario.activo) "desactivar" else "activar"
        AlertDialog(
            onDismissRequest = { usuarioParaCambioEstado = null },
            title = { Text("Modificar Estado") },
            text = { Text("¿Estás seguro de que deseas $accion la cuenta de ${usuario.nombre}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.alternarEstadoActivo(usuario, uidAdminActual)
                    usuarioParaCambioEstado = null
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioParaCambioEstado = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TarjetaUsuarioItem(
    usuario: PerfilUsuario,
    esElMismoAdmin: Boolean,
    onCambiarRolClick: () -> Unit,
    onToggleActivo: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usuario.activo) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (usuario.rol == Rol.ADMINISTRADOR) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (usuario.rol == Rol.ADMINISTRADOR) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (usuario.rol == Rol.ADMINISTRADOR) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usuario.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = usuario.correo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Rol: ${usuario.rol.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onCambiarRolClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Cambiar Rol")
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (usuario.activo) "Activo" else "Inactivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (usuario.activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Switch(
                    checked = usuario.activo,
                    onCheckedChange = { onToggleActivo() },
                    enabled = !esElMismoAdmin
                )
            }
        }
    }
}