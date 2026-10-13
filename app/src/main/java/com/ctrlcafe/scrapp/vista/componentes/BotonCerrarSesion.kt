package com.ctrlcafe.scrapp.vista.componentes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Ícono para la TopBar que pide confirmación antes de cerrar la sesión. */
@Composable
fun BotonCerrarSesion(onCerrarSesion: () -> Unit) {
    var mostrarDialogo by remember { mutableStateOf(false) }

    IconButton(onClick = { mostrarDialogo = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = "Cerrar sesión"
        )
    }

    if (mostrarDialogo) {
        DialogoConfirmacion(
            titulo = "Cerrar sesión",
            mensaje = "¿Seguro que deseas salir? Tendrás que volver a iniciar sesión.",
            onConfirm = {
                mostrarDialogo = false
                onCerrarSesion()
            },
            onDismiss = { mostrarDialogo = false }
        )
    }
}
