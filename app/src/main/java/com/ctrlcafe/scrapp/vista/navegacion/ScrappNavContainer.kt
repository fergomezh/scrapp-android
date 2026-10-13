package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ctrlcafe.scrapp.modelo.PerfilUsuario

@Composable
fun ScrappNavContainer(
    // TODO: Conectar con el perfil real desde AuthViewModel al integrar el Login
    usuarioActual: PerfilUsuario? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Ocultamos la barra si estamos en Login o Splash
            if (currentRoute != Rutas.Login.route && currentRoute != Rutas.Splash.route) {
                BarraInferior(
                    navController = navController,
                    esAdministrador = usuarioActual?.esAdministrador == true
                )
            }
        }
    ) { innerPadding ->
        ScrappNavHost(
            navController = navController,
            usuarioActual = usuarioActual,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
