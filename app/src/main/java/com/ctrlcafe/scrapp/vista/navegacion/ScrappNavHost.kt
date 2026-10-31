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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ctrlcafe.scrapp.controlador.AuthViewModel
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.vista.auth.LoginScreen
import com.ctrlcafe.scrapp.vista.auth.RegistroScreen
import com.ctrlcafe.scrapp.vista.gerencia.PanelGerenciaScreen
import com.ctrlcafe.scrapp.vista.lotes.IngresoLoteScreen
import com.ctrlcafe.scrapp.vista.lotes.MonitorLotesScreen
import com.ctrlcafe.scrapp.vista.mermas.RegistroMermaScreen
import com.ctrlcafe.scrapp.vista.produccion.DashboardScreen
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.ctrlcafe.scrapp.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp



@Composable
fun ScrappNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    usuarioActual: PerfilUsuario?,
    modifier: Modifier = Modifier
) {
    // Cierra la sesión y vacía el historial para que "atrás" no regrese a pantallas protegidas
    val cerrarSesion: () -> Unit = {
        authViewModel.cerrarSesion()
        navController.navigate(Rutas.Login.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        // Arranca en el Splash nativo de Compose de forma limpia
        startDestination = Rutas.Splash.route,
        modifier = modifier
    ) {
        // --- PANTALLA SPLASH ya PUSE EL LOGO ATT WILLIAM
        composable(Rutas.Splash.route) {
            LaunchedEffect(key1 = true) {
                delay(1500) // Se muestra por 1.5 segundos en pantalla
                // Salta al Login por defecto de forma limpia
                navController.navigate(Rutas.Login.route) {
                    popUpTo(Rutas.Splash.route) { inclusive = true } // Lo remueve de la pila
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                // REEMPLAZADO: Muestra el logo físico del equipo centrado y estético
                Image(
                    painter = painterResource(id = R.drawable.scrapp), // <-- Apunta a tu archivo en drawable
                    contentDescription = "Logo Oficial de Scrapp",
                    modifier = Modifier
                        .size(180.dp), // Ajusta el tamaño (ancho y alto) a tu gusto
                    contentScale = ContentScale.Fit
                )
            }
        }

        // --- DESTINOS DEFINITIVOS DE LA APLICACIÓN ---
        // Si Firebase ya tiene una sesión guardada, LoginScreen salta directo a Producción
        composable(Rutas.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginExitoso = { irAInicio(navController) },
                onIrARegistro = {
                    authViewModel.limpiarError()
                    navController.navigate(Rutas.Registro.route)
                }
            )
        }

        composable(Rutas.Registro.route) {
            RegistroScreen(
                viewModel = authViewModel,
                onRegistroExitoso = { irAInicio(navController) },
                onVolverALogin = {
                    authViewModel.limpiarError()
                    navController.popBackStack()
                }
            )
        }

        composable(RutasBottom.Produccion.route) {
            DashboardScreen(
                onIrAMonitorLotes = { navegarAPestana(navController, RutasBottom.Lotes.route) },
                onCerrarSesion = cerrarSesion
            )
        }

        composable(RutasBottom.Lotes.route) {
            MonitorLotesScreen(
                onRegistrarMerma = { productoId, loteId ->
                    navController.navigate(Rutas.RegistroMerma.conLote(productoId, loteId))
                },
                onIrAIngresoLote = { navController.navigate(Rutas.IngresoLote.route) }
            )
        }

        composable(Rutas.IngresoLote.route) {
            IngresoLoteScreen(onVolver = { navController.popBackStack() })
        }

        composable(RutasBottom.Mermas.route) {
            RegistroMermaScreen(
                viewModel = viewModel(),
                usuarioId = usuarioActual?.uid.orEmpty()
            )
        }

        composable(
            route = Rutas.RegistroMerma.route,
            arguments = listOf(
                navArgument(Rutas.RegistroMerma.ARG_PRODUCTO_ID) { type = NavType.StringType },
                navArgument(Rutas.RegistroMerma.ARG_LOTE_ID) { type = NavType.StringType }
            )
        ) { entrada ->
            RegistroMermaScreen(
                viewModel = viewModel(),
                usuarioId = usuarioActual?.uid.orEmpty(),
                productoPreseleccionadoId = entrada.arguments?.getString(Rutas.RegistroMerma.ARG_PRODUCTO_ID),
                lotePreseleccionadoId = entrada.arguments?.getString(Rutas.RegistroMerma.ARG_LOTE_ID),
                onVolver = { navController.popBackStack() }
            )
        }

        // --- PANTALLA PROTEGIDA POR ROL (SOLO ADMINISTRADOR) ---
        composable(RutasBottom.Gerencia.route) {
            // Filtro de seguridad por el rol guardado en Firestore
            val esAdmin = usuarioActual?.esAdministrador == true

            if (esAdmin) {
                PanelGerenciaScreen(navController = navController, onCerrarSesion = cerrarSesion)
            } else {
                // Redirección si un rol operativo intenta saltarse la UI.
                // Va en LaunchedEffect: navegar durante la composición provoca recomposiciones en bucle.
                LaunchedEffect(Unit) {
                    navController.navigate(RutasBottom.Produccion.route) {
                        popUpTo(RutasBottom.Gerencia.route) { inclusive = true }
                    }
                }
            }
        }
    }
}

/** Tras iniciar sesión o registrarse: va a Producción y saca Login/Registro del historial. */
private fun irAInicio(navController: NavHostController) {
    navController.navigate(RutasBottom.Produccion.route) {
        popUpTo(Rutas.Login.route) { inclusive = true }
        launchSingleTop = true
    }
}
