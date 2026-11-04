package com.ctrlcafe.scrapp.vista.historial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ctrlcafe.scrapp.controlador.FilaHistorialMerma
import com.ctrlcafe.scrapp.controlador.HistorialUiState
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.vista.componentes.EstadoVacio
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Historial de mermas (Integrante 3, requerimiento 4).
 *
 * Lista en tiempo real ordenada por fecha, con producto, cantidad, causa, pérdida en dinero,
 * quién la registró y miniatura de la evidencia. Se filtra por rango de fechas, producto y
 * causa. Al tocar una fila se abre el detalle con la foto completa.
 */
@Composable
fun HistorialMermasScreen(
    uiState: HistorialUiState,
    onRangoFechas: (LocalDate?, LocalDate?) -> Unit,
    onProducto: (String?) -> Unit,
    onCausa: (CausaMerma?) -> Unit,
    onLimpiarFiltros: () -> Unit,
    onAbrirDetalle: (FilaHistorialMerma) -> Unit,
    onCerrarDetalle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        BarraFiltrosMermas(
            uiState = uiState,
            onRangoFechas = onRangoFechas,
            onProducto = onProducto,
            onCausa = onCausa,
            onLimpiarFiltros = onLimpiarFiltros
        )

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
                    icono = Icons.Default.History
                )
            }

            uiState.mermasFiltradas.isEmpty() -> {
                EstadoVacio(
                    titulo = if (uiState.filtros.activos) "Sin resultados" else "Sin mermas registradas",
                    mensaje = if (uiState.filtros.activos) {
                        "Ninguna merma coincide con los filtros seleccionados."
                    } else {
                        "Las mermas que se registren aparecerán aquí al instante."
                    },
                    icono = Icons.Default.History
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
                            text = "${uiState.mermasFiltradas.size} mermas · Pérdida total ${formatearDinero(uiState.perdidaFiltrada)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(items = uiState.mermasFiltradas, key = { it.merma.id }) { fila ->
                        TarjetaMermaHistorial(fila = fila, onClick = { onAbrirDetalle(fila) })
                    }
                }
            }
        }
    }

    uiState.mermaSeleccionada?.let { fila ->
        DetalleMermaDialog(fila = fila, onCerrar = onCerrarDetalle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraFiltrosMermas(
    uiState: HistorialUiState,
    onRangoFechas: (LocalDate?, LocalDate?) -> Unit,
    onProducto: (String?) -> Unit,
    onCausa: (CausaMerma?) -> Unit,
    onLimpiarFiltros: () -> Unit
) {
    val filtros = uiState.filtros
    var mostrarRango by remember { mutableStateOf(false) }
    var menuProducto by remember { mutableStateOf(false) }
    var menuCausa by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filtros.desde != null || filtros.hasta != null,
            onClick = { mostrarRango = true },
            label = { Text(textoRango(filtros.desde, filtros.hasta)) },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

        Box {
            FilterChip(
                selected = filtros.productoId != null,
                onClick = { menuProducto = true },
                label = {
                    Text(
                        text = uiState.productos.find { it.id == filtros.productoId }?.nombre ?: "Producto",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = menuProducto, onDismissRequest = { menuProducto = false }) {
                DropdownMenuItem(
                    text = { Text("Todos los productos") },
                    onClick = { onProducto(null); menuProducto = false }
                )
                uiState.productos.forEach { producto ->
                    DropdownMenuItem(
                        text = { Text(producto.nombre) },
                        onClick = { onProducto(producto.id); menuProducto = false }
                    )
                }
            }
        }

        Box {
            FilterChip(
                selected = filtros.causa != null,
                onClick = { menuCausa = true },
                label = { Text(filtros.causa?.let { etiquetaCausa(it) } ?: "Causa") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = menuCausa, onDismissRequest = { menuCausa = false }) {
                DropdownMenuItem(
                    text = { Text("Todas las causas") },
                    onClick = { onCausa(null); menuCausa = false }
                )
                CausaMerma.entries.forEach { causa ->
                    DropdownMenuItem(
                        text = { Text(etiquetaCausa(causa)) },
                        onClick = { onCausa(causa); menuCausa = false }
                    )
                }
            }
        }

        if (filtros.activos) {
            FilterChip(
                selected = false,
                onClick = onLimpiarFiltros,
                label = { Text("Limpiar") },
                leadingIcon = { Icon(Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }

    if (mostrarRango) {
        // El DateRangePicker trabaja en milisegundos UTC; se convierte a LocalDate sin zona
        val estado = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filtros.desde?.aMillisUtc(),
            initialSelectedEndDateMillis = filtros.hasta?.aMillisUtc()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarRango = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val desde = estado.selectedStartDateMillis?.aLocalDateUtc()
                        // Si solo se elige un día, el rango es ese día
                        val hasta = estado.selectedEndDateMillis?.aLocalDateUtc() ?: desde
                        onRangoFechas(desde, hasta)
                        mostrarRango = false
                    }
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onRangoFechas(null, null)
                        mostrarRango = false
                    }
                ) { Text("Quitar rango") }
            }
        ) {
            DateRangePicker(
                state = estado,
                title = { Text("Rango de fechas", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TarjetaMermaHistorial(
    fila: FilaHistorialMerma,
    onClick: () -> Unit
) {
    val merma = fila.merma
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniaturaHistorial(url = fila.urlEvidencia)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = fila.nombreProducto,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatearCantidad(merma.cantidad)} ${fila.unidadMedida} · ${etiquetaCausa(merma.causa)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${merma.fecha.format(FORMATO_FECHA)} · ${fila.registradoPor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatearDinero(fila.perdida),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MiniaturaHistorial(url: String?) {
    val forma = RoundedCornerShape(8.dp)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = "Evidencia de la merma",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(forma)
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(forma)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HideImage,
                contentDescription = "Sin evidencia",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Detalle de una merma con la foto de evidencia a tamaño completo. */
@Composable
private fun DetalleMermaDialog(
    fila: FilaHistorialMerma,
    onCerrar: () -> Unit
) {
    val merma = fila.merma
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fila.nombreProducto,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar detalle")
                    }
                }

                val url = fila.urlEvidencia
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Foto de evidencia de la merma",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.HideImage,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Esta merma no tiene foto de evidencia",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                FilaDetalle("Pérdida", formatearDinero(fila.perdida))
                FilaDetalle("Cantidad", "${formatearCantidad(merma.cantidad)} ${fila.unidadMedida}")
                FilaDetalle("Costo unitario", formatearDinero(merma.costoUnitarioCongelado))
                FilaDetalle("Causa", merma.causa.descripcion)
                FilaDetalle("Fecha", merma.fecha.format(FORMATO_FECHA))
                FilaDetalle("Lote", merma.loteId)
                FilaDetalle("Registrada por", fila.registradoPor)
            }
        }
    }
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Etiqueta corta para chips y filas; la descripción completa va en el detalle. */
private fun etiquetaCausa(causa: CausaMerma): String = when (causa) {
    CausaMerma.VENCIMIENTO -> "Vencimiento"
    CausaMerma.DETERIORO_ALMACENAMIENTO -> "Deterioro"
    CausaMerma.SOBREPRODUCCION -> "Sobreproducción"
    CausaMerma.ERROR_PREPARACION -> "Error de preparación"
    CausaMerma.NO_VENDIDO -> "No vendido"
}

private fun textoRango(desde: LocalDate?, hasta: LocalDate?): String {
    val corto = DateTimeFormatter.ofPattern("dd/MM", Locale("es", "SV"))
    return when {
        desde == null && hasta == null -> "Fechas"
        desde == hasta -> desde!!.format(corto)
        else -> "${desde?.format(corto) ?: "…"} – ${hasta?.format(corto) ?: "…"}"
    }
}

private fun LocalDate.aMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.aLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "SV"))

internal fun formatearDinero(valor: Double): String = String.format(Locale.US, "$%.2f", valor)

internal fun formatearCantidad(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toInt().toString() else String.format(Locale.US, "%.1f", valor)
