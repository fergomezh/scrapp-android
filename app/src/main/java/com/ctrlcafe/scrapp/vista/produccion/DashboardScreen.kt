package com.ctrlcafe.scrapp.vista.produccion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ctrlcafe.scrapp.controlador.ProduccionUiState
import com.ctrlcafe.scrapp.controlador.ProduccionViewModel
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.servicio.ProyeccionProducto
import com.ctrlcafe.scrapp.vista.componentes.BotonCerrarSesion
import com.ctrlcafe.scrapp.vista.componentes.EstadoVacio
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla principal del operativo: Dashboard de Producción diaria (Integrante 3).
 *
 * Muestra la cantidad sugerida para mañana calculada por [MotorProyeccion], junto con
 * la demanda base y el margen aplicado. Cuando no existe historial suficiente,
 * presenta un estado vacío informativo.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: ProduccionViewModel = viewModel(factory = ProduccionViewModel.provideFactory()),
    onIrAMonitorLotes: (() -> Unit)? = null,
    onCerrarSesion: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Recalcula automáticamente al abrir o regresar a la pantalla (ej. tras registrar una merma)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.cargarProyecciones()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DashboardContent(
        uiState = uiState,
        onRefrescar = { viewModel.cargarProyecciones() },
        onAlternarFiltroHistorial = { viewModel.alternarFiltroSinHistorial() },
        onIrAMonitorLotes = onIrAMonitorLotes,
        onCerrarSesion = onCerrarSesion,
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    uiState: ProduccionUiState,
    onRefrescar: () -> Unit,
    onAlternarFiltroHistorial: () -> Unit,
    modifier: Modifier = Modifier,
    onIrAMonitorLotes: (() -> Unit)? = null,
    onCerrarSesion: (() -> Unit)? = null
) {
    val formatoFecha = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("es", "SV"))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBarScrapp(
                title = "Dashboard de Producción",
                subtitle = "Proyección para ${uiState.fechaObjetivo.format(formatoFecha)}",
                actions = {
                    if (onIrAMonitorLotes != null) {
                        IconButton(onClick = onIrAMonitorLotes) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Ir a Monitor de Lotes"
                            )
                        }
                    }
                    IconButton(onClick = onRefrescar) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recalcular proyecciones"
                        )
                    }
                    if (onCerrarSesion != null) {
                        BotonCerrarSesion(onCerrarSesion = onCerrarSesion)
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.cargando -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.mensajeError != null -> {
                EstadoVacio(
                    titulo = "Error al cargar la proyección",
                    mensaje = uiState.mensajeError,
                    icono = Icons.Default.Info,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            uiState.sinHistorialSuficiente || uiState.proyeccionesVisibles.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                ) {
                    ResumenGeneralProyeccion(
                        uiState = uiState,
                        onAlternarFiltroHistorial = onAlternarFiltroHistorial
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    EstadoVacio(
                        titulo = "Sin historial suficiente",
                        mensaje = "No hay registros de ventas en las últimas 4 semanas equivalentes para calcular la producción sugerida de mañana.",
                        icono = Icons.Default.Info
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "resumen_cabecera") {
                        ResumenGeneralProyeccion(
                            uiState = uiState,
                            onAlternarFiltroHistorial = onAlternarFiltroHistorial
                        )
                    }

                    items(
                        items = uiState.proyeccionesVisibles,
                        key = { it.productoId }
                    ) { proyeccion ->
                        val producto = uiState.productosPorId[proyeccion.productoId]
                        // Tarjeta propia: TarjetaProducto (componentes) solo muestra nombre y cantidad,
                        // y aquí se necesitan demanda base, margen y tasa de merma.
                        TarjetaProductoProyeccion(
                            proyeccion = proyeccion,
                            producto = producto
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumenGeneralProyeccion(
    uiState: ProduccionUiState,
    onAlternarFiltroHistorial: () -> Unit
) {
    val margenPorcentaje = (uiState.margenSeguridadActual * 100).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Parámetros del Motor de Proyección",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("Margen base: +$margenPorcentaje%") }
                )
            }

            Text(
                text = "Productos con sugerencia activa: ${uiState.proyeccionesConHistorial.size} de ${uiState.proyecciones.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (uiState.proyeccionesSinHistorial.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilterChip(
                        selected = uiState.ocultarSinHistorial,
                        onClick = onAlternarFiltroHistorial,
                        label = {
                            Text(
                                if (uiState.ocultarSinHistorial) {
                                    "Mostrando solo con historial (${uiState.proyeccionesConHistorial.size})"
                                } else {
                                    "Ocultar sin historial (${uiState.proyeccionesSinHistorial.size})"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaProductoProyeccion(
    proyeccion: ProyeccionProducto,
    producto: Producto?
) {
    val unidad = producto?.unidadMedida ?: "Unid."
    val categoria = producto?.categoria ?: "General"
    val tieneSugerencia = proyeccion.cantidadSugerida > 0.0

    val margenBasePct = (proyeccion.margenSeguridad * 100).roundToInt()
    val tasaMermaPct = String.format(Locale.US, "%.1f%%", proyeccion.tasaMerma * 100)
    val ajusteNetoPct = String.format(
        Locale.US,
        "%+.1f%%",
        (proyeccion.margenSeguridad - proyeccion.factorCorreccion) * 100
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Encabezado: Nombre del producto + Cantidad sugerida destacada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = proyeccion.nombreProducto,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${proyeccion.productoId} · $categoria ($unidad)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (tieneSugerencia) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Sugerido mañana",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tieneSugerencia) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "${formatearCantidad(proyeccion.cantidadSugerida)} $unidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tieneSugerencia) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Métricas solicitadas: Demanda base, Margen aplicado y Tasa de merma (14d)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricaProyeccionItem(
                    etiqueta = "Demanda base",
                    valor = "${formatearCantidad(proyeccion.demandaBase)} $unidad",
                    modifier = Modifier.weight(1f)
                )
                MetricaProyeccionItem(
                    etiqueta = "Margen aplicado",
                    valor = "$ajusteNetoPct (base +$margenBasePct%)",
                    modifier = Modifier.weight(1f)
                )
                MetricaProyeccionItem(
                    etiqueta = "Merma (14d)",
                    valor = tasaMermaPct,
                    modifier = Modifier.weight(0.8f)
                )
            }

            // Advertencia cuando no hay historial o cuando el historial es parcial
            if (proyeccion.advertencia != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = proyeccion.advertencia,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricaProyeccionItem(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatearCantidad(valor: Double): String {
    return if (valor % 1.0 == 0.0) {
        valor.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", valor)
    }
}