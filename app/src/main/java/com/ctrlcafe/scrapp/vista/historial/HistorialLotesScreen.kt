package com.ctrlcafe.scrapp.vista.historial

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ctrlcafe.scrapp.controlador.FilaHistorialLote
import com.ctrlcafe.scrapp.controlador.FiltroHistorialLote
import com.ctrlcafe.scrapp.controlador.HistorialUiState
import com.ctrlcafe.scrapp.controlador.MotivoCierreLote
import com.ctrlcafe.scrapp.vista.componentes.EstadoVacio

/**
 * Historial de lotes (Integrante 3): lotes agotados y vencidos con su fecha y cuánto se
 * perdió de cada uno. Un lote vencido con existencias muestra además lo pendiente de
 * registrar como merma, valorizado al costo del lote.
 */
@Composable
fun HistorialLotesScreen(
    uiState: HistorialUiState,
    onFiltro: (FiltroHistorialLote) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FiltroHistorialLote.entries.forEach { filtro ->
                val cantidad = if (filtro.motivo == null) {
                    uiState.lotes.size
                } else {
                    uiState.lotes.count { it.motivo == filtro.motivo }
                }
                FilterChip(
                    selected = uiState.filtroLotes == filtro,
                    onClick = { onFiltro(filtro) },
                    label = { Text("${filtro.etiqueta} ($cantidad)") }
                )
            }
        }

        when {
            uiState.cargando -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.mensajeError != null -> {
                EstadoVacio(
                    titulo = "Error al cargar el historial",
                    mensaje = uiState.mensajeError,
                    icono = Icons.Default.Inventory
                )
            }

            uiState.lotesFiltrados.isEmpty() -> {
                EstadoVacio(
                    titulo = "Sin lotes cerrados",
                    mensaje = "Aquí aparecen los lotes que se agotaron o vencieron.",
                    icono = Icons.Default.Inventory
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "Pérdida total: ${formatearDinero(uiState.lotesFiltrados.sumOf { it.perdidaTotal })}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (!uiState.esAdministrador) {
                            Text(
                                text = "La pérdida registrada incluye solo las mermas que usted registró.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(items = uiState.lotesFiltrados, key = { it.lote.id }) { fila ->
                        TarjetaLoteHistorial(fila)
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaLoteHistorial(fila: FilaHistorialLote) {
    val lote = fila.lote
    val esVencido = fila.motivo == MotivoCierreLote.VENCIDO

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fila.nombreProducto,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lote ${lote.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(fila.motivo.etiqueta) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (esVencido) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        labelColor = if (esVencido) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                )
            }

            Text(
                text = if (esVencido) {
                    "Venció el ${fila.fechaCierre.format(FORMATO_FECHA)}"
                } else {
                    "Agotado el ${fila.fechaCierre.format(FORMATO_FECHA)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DatoLote("Inicial", "${formatearCantidad(lote.cantidadInicial)} ${fila.unidadMedida}")
                DatoLote("Mermado", "${formatearCantidad(fila.unidadesMermadas)} ${fila.unidadMedida}")
                DatoLote("Ingreso", lote.fechaIngreso.format(FORMATO_FECHA))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DatoLote("Pérdida registrada", formatearDinero(fila.perdidaRegistrada))
                if (fila.cantidadPendiente > 0.0) {
                    DatoLote(
                        "Pendiente de registrar",
                        "${formatearCantidad(fila.cantidadPendiente)} ${fila.unidadMedida} · ${formatearDinero(fila.perdidaPendiente)}",
                        resaltar = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DatoLote(etiqueta: String, valor: String, resaltar: Boolean = false) {
    Column {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (resaltar) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
