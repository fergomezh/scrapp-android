package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun ScrappNavContainer() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // TODO: Conectar con el rol real desde GerenciaViewModel o AuthViewModel para que se oculte en algunas vistas
    val rolUsuarioSimulado = "Admin"

    Scaffold(
        bottomBar = {
            // Ocultamos la barra si estamos en Login o Splash
            if (currentRoute != Rutas.Login.route && currentRoute != Rutas.Splash.route) {
                BarraInferior(navController = navController, rolUsuario = rolUsuarioSimulado)
            }
        }
    ) { innerPadding ->
        ScrappNavHost(
            navController = navController,
            usuarioActual = null, // Se reemplazará con el objeto real
            modifier = Modifier.padding(innerPadding)
        )
    }
}
