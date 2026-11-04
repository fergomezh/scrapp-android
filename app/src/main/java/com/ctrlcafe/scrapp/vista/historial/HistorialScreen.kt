package com.ctrlcafe.scrapp.vista.historial

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ctrlcafe.scrapp.controlador.HistorialViewModel
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp

/**
 * Pestaña Historial (Integrante 3, requerimiento 4). Disponible para los dos roles.
 *
 * Agrupa [HistorialMermasScreen] y [HistorialLotesScreen] bajo un mismo ViewModel, así las
 * dos pestañas comparten los listeners de Firestore en lugar de abrir cada una los suyos.
 *
 * Conexión en `ScrappNavHost.kt`:
 *   `composable(RutasBottom.Historial.route) { HistorialScreen(usuario = usuarioActual) }`
 */
@Composable
fun HistorialScreen(
    usuario: PerfilUsuario?,
    modifier: Modifier = Modifier,
    viewModel: HistorialViewModel = viewModel(factory = HistorialViewModel.provideFactory(usuario))
) {
    val uiState by viewModel.uiState.collectAsState()
    var pestana by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBarScrapp(
                title = "Historial",
                subtitle = if (uiState.esAdministrador) {
                    "Registros de todo el equipo"
                } else {
                    "Mermas registradas por usted"
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = pestana) {
                Tab(
                    selected = pestana == 0,
                    onClick = { pestana = 0 },
                    text = { Text("Mermas (${uiState.mermasFiltradas.size})") }
                )
                Tab(
                    selected = pestana == 1,
                    onClick = { pestana = 1 },
                    text = { Text("Lotes (${uiState.lotes.size})") }
                )
            }

            when (pestana) {
                0 -> HistorialMermasScreen(
                    uiState = uiState,
                    onRangoFechas = viewModel::seleccionarRangoFechas,
                    onProducto = viewModel::seleccionarProducto,
                    onCausa = viewModel::seleccionarCausa,
                    onLimpiarFiltros = viewModel::limpiarFiltros,
                    onAbrirDetalle = viewModel::abrirDetalle,
                    onCerrarDetalle = viewModel::cerrarDetalle
                )

                else -> HistorialLotesScreen(
                    uiState = uiState,
                    onFiltro = viewModel::seleccionarFiltroLotes
                )
            }
        }
    }
}
