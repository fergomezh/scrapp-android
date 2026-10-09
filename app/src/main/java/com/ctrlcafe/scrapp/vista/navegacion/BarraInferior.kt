package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BarraInferior(navController: NavHostController, rolUsuario: String) {
    val itemsBase = listOf(
        RutasBottom.Produccion,
        RutasBottom.Lotes,
        RutasBottom.Mermas
    )

    // Agrega Gerencia solo si el rol del usuario es Administrador
    val itemsMenu = if (rolUsuario == "Admin") itemsBase + RutasBottom.Gerencia else itemsBase

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
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
