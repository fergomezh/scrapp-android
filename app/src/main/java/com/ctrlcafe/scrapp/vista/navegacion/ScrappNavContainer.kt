package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ctrlcafe.scrapp.controlador.AuthEstadoUi
import com.ctrlcafe.scrapp.controlador.AuthViewModel

// Pantallas donde no se muestra la barra inferior
private val rutasSinBarra = setOf(Rutas.Splash.route, Rutas.Login.route, Rutas.Registro.route)

@Composable
fun ScrappNavContainer(
    authViewModel: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // El perfil (y su rol) sale de la sesión real de Firebase
    val estadoAuth by authViewModel.estado.collectAsState()
    val usuarioActual = (estadoAuth as? AuthEstadoUi.Autenticado)?.perfil

    Scaffold(
        bottomBar = {
            if (currentRoute != null && currentRoute !in rutasSinBarra) {
                BarraInferior(
                    navController = navController,
                    esAdministrador = usuarioActual?.esAdministrador == true
                )
            }
        }
    ) { innerPadding ->
        ScrappNavHost(
            navController = navController,
            authViewModel = authViewModel,
            usuarioActual = usuarioActual,
            // consumeWindowInsets evita que las TopBar de cada pantalla vuelvan a sumar la barra de estado
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}
