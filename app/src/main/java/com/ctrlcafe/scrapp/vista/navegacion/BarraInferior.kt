package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BarraInferior(navController: NavHostController, esAdministrador: Boolean) {
    val itemsBase = listOf(
        RutasBottom.Produccion,
        RutasBottom.Lotes,
        RutasBottom.Mermas
    )

    // Agrega Gerencia solo si el rol del usuario es Administrador
    val itemsMenu = if (esAdministrador) itemsBase + RutasBottom.Gerencia else itemsBase

    NavigationBar {
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry.value?.destination?.route

        itemsMenu.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navegarAPestana(navController, item.route)
                    }
                }
            )
        }
    }
}

/**
 * Cambia de pestaña sin apilar pantallas. La raíz es Producción y no el destino
 * inicial (Splash), porque el Splash ya se sacó del historial al ir al Login.
 */
fun navegarAPestana(navController: NavHostController, ruta: String) {
    navController.navigate(ruta) {
        popUpTo(RutasBottom.Produccion.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
