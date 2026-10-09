package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ctrlcafe.scrapp.modelo.Usuario
import com.ctrlcafe.scrapp.vista.gerencia.PanelGerenciaScreen
import kotlinx.coroutines.delay

@Composable
fun ScrappNavHost(
    navController: NavHostController,
    usuarioActual: Usuario?,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        // Arranca en el Splash nativo de Compose de forma limpia
        startDestination = Rutas.Splash.route,
        modifier = modifier
    ) {
        // --- PANTALLA SPLASH EN COMPOSE (PENDIENTE LOGO EQUIPO) ---
        composable(Rutas.Splash.route) {
            LaunchedEffect(key1 = true) {
                delay(1500) // Se muestra por 1.5 segundos en pantalla
                // CORREGIDO: Salta al Login por defecto para no forzar la UI administrativa
                navController.navigate(Rutas.Login.route) {
                    popUpTo(Rutas.Splash.route) { inclusive = true } // Remueve el Splash del historial
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Scrapp",
                    fontSize = 42.sp,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- DESTINOS DEFINITIVOS DE LA APLICACIÓN ---
        composable(Rutas.Login.route) {
            // Aquí tu equipo integrará la LoginScreen() del Avance 1
        }

        composable(RutasBottom.Produccion.route) {
            // Aquí el Integrante 1 conectará su PantallaProduccionScreen()
        }

        composable(RutasBottom.Lotes.route) {
            // Aquí el Integrante 2 conectará su PantallaLotesScreen()
        }

        composable(RutasBottom.Mermas.route) {
            // Aquí el Integrante 3 conectará su PantallaMermasScreen()
        }

        // --- PANTALLA PROTEGIDA POR ROL (SOLO ADMINISTRADOR) ---
        composable(RutasBottom.Gerencia.route) {
            // Filtro de seguridad estratégico por correo corporativo o rol enum
            val esAdmin = usuarioActual?.correo == "admin@scrapp.com" || usuarioActual?.perfil?.name == "ADMINISTRADOR"

            if (esAdmin) {
                PanelGerenciaScreen(navController = navController)
            } else {
                // Redirección forzada inmediata si un rol operativo intenta saltarse la UI
                navController.navigate(RutasBottom.Produccion.route) {
                    popUpTo(RutasBottom.Produccion.route) { inclusive = true }
                }
            }
        }
    }
}
