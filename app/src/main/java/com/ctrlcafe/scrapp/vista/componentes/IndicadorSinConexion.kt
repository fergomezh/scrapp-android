package com.ctrlcafe.scrapp.vista.componentes

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * `true` mientras el dispositivo tiene una red con salida a internet.
 * Se actualiza con un NetworkCallback mientras el composable está en pantalla.
 */
@Composable
fun rememberHayConexion(): Boolean {
    val context = LocalContext.current
    val gestor = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var hayConexion by remember { mutableStateOf(gestor.tieneInternet()) }

    DisposableEffect(gestor) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { hayConexion = gestor.tieneInternet() }
            override fun onLost(network: Network) { hayConexion = gestor.tieneInternet() }
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                hayConexion = gestor.tieneInternet()
            }
        }
        val solicitud = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        gestor.registerNetworkCallback(solicitud, callback)
        onDispose { gestor.unregisterNetworkCallback(callback) }
    }
    return hayConexion
}

private fun ConnectivityManager.tieneInternet(): Boolean {
    val capacidades = getNetworkCapabilities(activeNetwork) ?: return false
    return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Franja que avisa que la app está sin conexión. Firestore sigue funcionando con su
 * caché local y sincroniza al volver la red, así que solo informa, no bloquea.
 */
@Composable
fun IndicadorSinConexion(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Sin conexión: los cambios se guardan en el dispositivo y se sincronizan al volver la red",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
