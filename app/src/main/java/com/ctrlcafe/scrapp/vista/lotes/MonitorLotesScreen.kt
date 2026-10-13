package com.ctrlcafe.scrapp.vista.lotes

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ctrlcafe.scrapp.controlador.FiltroEstadoLote
import com.ctrlcafe.scrapp.controlador.LoteUiState
import com.ctrlcafe.scrapp.controlador.LoteViewModel
import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.servicio.EstadoLote
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de Monitor de Lotes (Integrante 3).
 *
 * Muestra los lotes en una [LazyColumn] ordenada por urgencia con su chip de semáforo
 * (Verde, Amarillo, Rojo, Vencido) y horas restantes, filtro por estado y acción
 * "Registrar merma" en lotes vencidos.
 *
 * @param onRegistrarMerma Callback que navega al formulario de registro de merma (Integrante 2)
 *                         recibiendo el `productoId` ya preseleccionado.
 * @param onIrAIngresoLote Callback que navega a la pantalla `IngresoLoteScreen`.
 */
@Composable
fun MonitorLotesScreen(
    modifier: Modifier = Modifier,
    viewModel: LoteViewModel = viewModel(factory = LoteViewModel.provideFactory()),
    onRegistrarMerma: (productoId: String) -> Unit = {},
    onIrAIngresoLote: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Recalcula el semáforo al abrir el monitor y al volver de otra pantalla (ej. RegistroMermaScreen)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.recalcularSemaforo()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    MonitorLotesContent(
        uiState = uiState,
        onFiltroSeleccionado = { viewModel.seleccionarFiltro(it) },
        onRefrescar = { viewModel.recalcularSemaforo() },
        onRegistrarMerma = onRegistrarMerma,
        onIrAIngresoLote = onIrAIngresoLote,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorLotesContent(
    uiState: LoteUiState,
    onFiltroSeleccionado: (FiltroEstadoLote) -> Unit,
    onRefrescar: () -> Unit,
    onRegistrarMerma: (productoId: String) -> Unit,
    onIrAIngresoLote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // TODO [Integración I4]: Reemplazar por TopBarScrapp cuando esté en vista/componentes/
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Monitor de Lotes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.lotes.size} lotes activos ordenados por urgencia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefrescar) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recalcular semáforo"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onIrAIngresoLote,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text("Ingresar lote") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BarraFiltrosSemaforo(
                filtroActual = uiState.filtroActual,
                conteoPorEstado = uiState.conteoPorEstado,
                totalLotes = uiState.lotes.size,
                onFiltroSeleccionado = onFiltroSeleccionado
            )

            when {
                uiState.cargando -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.mensajeError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EstadoVacioLotesLocal(
                            titulo = "Error al cargar los lotes",
                            descripcion = uiState.mensajeError
                        )
                    }
                }

                uiState.lotesFiltrados.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // TODO [Integración I4]: Reemplazar por EstadoVacio cuando esté en vista/componentes/
                        EstadoVacioLotesLocal(
                            titulo = "No hay lotes en este estado",
                            descripcion = if (uiState.filtroActual == FiltroEstadoLote.TODOS) {
                                "No existen lotes con inventario disponible en este momento."
                            } else {
                                "No se encontraron lotes con estado ${uiState.filtroActual.etiqueta}."
                            }
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.lotesFiltrados,
                            key = { it.lote.id }
                        ) { estadoLote ->
                            // TODO [Integración I4]: Reemplazar por TarjetaLote cuando esté en vista/componentes/
                            TarjetaLoteLocal(
                                estadoLote = estadoLote,
                                onRegistrarMerma = {
                                    onRegistrarMerma(estadoLote.lote.productoId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarraFiltrosSemaforo(
    filtroActual: FiltroEstadoLote,
    conteoPorEstado: Map<String, Int>,
    totalLotes: Int,
    onFiltroSeleccionado: (FiltroEstadoLote) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FiltroEstadoLote.entries.forEach { filtro ->
            val cantidad = if (filtro.nivelAlerta == null) {
                totalLotes
            } else {
                conteoPorEstado[filtro.nivelAlerta] ?: 0
            }

            FilterChip(
                selected = filtroActual == filtro,
                onClick = { onFiltroSeleccionado(filtro) },
                label = {
                    Text("${filtro.etiqueta} ($cantidad)")
                }
            )
        }
    }
}

@Composable
private fun TarjetaLoteLocal(
    estadoLote: EstadoLote,
    onRegistrarMerma: () -> Unit
) {
    val lote = estadoLote.lote
    val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "SV"))
    val esVencido = estadoLote.bloqueado || estadoLote.estado is EstadoCaducidad.Vencido

    val textoHoras = when {
        esVencido && estadoLote.horasRestantes < 0 ->
            "Vencido hace ${kotlin.math.abs(estadoLote.horasRestantes)} h"
        esVencido ->
            "Vencido (0 h restantes)"
        else ->
            "${estadoLote.horasRestantes} h restantes"
    }

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
            // Fila superior: Nombre del producto + Chip de Semáforo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = estadoLote.nombreProducto,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lote ${lote.id} · Producto ${lote.productoId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // TODO [Integración I4]: Reemplazar por ChipSemaforo cuando esté en vista/componentes/
                ChipSemaforoLocal(estado = estadoLote.estado)
            }

            // Indicador de horas restantes y descripción de estado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (esVencido) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = "$textoHoras · ${estadoLote.estado.descripcion}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (esVencido) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            HorizontalDivider()

            // Detalles de inventario y fechas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetalleLoteColumna(
                    etiqueta = "Disponible / Inicial",
                    valor = "${formatearNumero(lote.cantidadDisponible)} /${formatearNumero(lote.cantidadInicial)}"
                )
                DetalleLoteColumna(
                    etiqueta = "Ingreso",
                    valor = lote.fechaIngreso.format(formatoFecha)
                )
                DetalleLoteColumna(
                    etiqueta = "Caducidad",
                    valor = lote.fechaCaducidad.format(formatoFecha)
                )
            }

            // Acción obligatoria cuando el lote está vencido: navegar a Registrar merma con el producto elegido
            if (esVencido) {
                Button(
                    onClick = onRegistrarMerma,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registrar merma",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipSemaforoLocal(estado: EstadoCaducidad) {
    val (colorFondo, colorTexto, textoEtiqueta, icono) = when (estado) {
        is EstadoCaducidad.Vigente -> CuadruploChip(
            colorFondo = Color(0xFFE8F5E9),
            colorTexto = Color(0xFF1B5E20),
            etiqueta = "Verde",
            icono = Icons.Default.CheckCircle
        )
        is EstadoCaducidad.ProximoAVencer -> CuadruploChip(
            colorFondo = Color(0xFFFFF8E1),
            colorTexto = Color(0xFFF57F17),
            etiqueta = "Amarillo",
            icono = Icons.Default.Schedule
        )
        is EstadoCaducidad.Critico -> CuadruploChip(
            colorFondo = Color(0xFFFFEBEE),
            colorTexto = Color(0xFFB71C1C),
            etiqueta = "Rojo",
            icono = Icons.Default.ReportProblem
        )
        is EstadoCaducidad.Vencido -> CuadruploChip(
            colorFondo = Color(0xFF212121),
            colorTexto = Color(0xFFFFFFFF),
            etiqueta = "Vencido",
            icono = Icons.Default.Error
        )
    }

    Surface(
        color = colorFondo,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = colorTexto,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = textoEtiqueta,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorTexto
            )
        }
    }
}

private data class CuadruploChip(
    val colorFondo: Color,
    val colorTexto: Color,
    val etiqueta: String,
    val icono: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun DetalleLoteColumna(
    etiqueta: String,
    valor: String
) {
    Column {
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

@Composable
private fun EstadoVacioLotesLocal(
    titulo: String,
    descripcion: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Inventory,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatearNumero(valor: Double): String {
    return if (valor % 1.0 == 0.0) {
        valor.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", valor)
    }
}