package com.ctrlcafe.scrapp.vista.gerencia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ctrlcafe.scrapp.controlador.GerenciaViewModel
import com.ctrlcafe.scrapp.ui.theme.AlertaCritica
import com.ctrlcafe.scrapp.ui.theme.TextoSecundario
import com.ctrlcafe.scrapp.vista.componentes.BotonCerrarSesion
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp
import com.ctrlcafe.scrapp.vista.navegacion.Rutas
import com.ctrlcafe.scrapp.vista.navegacion.RutasBottom
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter

/** Etiquetas 1, 2, 3… en el eje X para que coincidan con las leyendas numeradas. */
private val ejeDesdeUno = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { valor, _ ->
    (valor.toInt() + 1).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelGerenciaScreen(
    navController: NavHostController,
    viewModel: GerenciaViewModel = viewModel(),
    onCerrarSesion: (() -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val formatoFecha = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.cargarDatosFinancieros()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val fechaInicio = viewModel.fechaInicio.value
    val fechaFin = viewModel.fechaFin.value
    val perdidaDia = viewModel.perdidaDia.value
    val perdidaMes = viewModel.perdidaMes.value
    val indiceMerma = viewModel.indiceMerma.value
    val productosCriticos = viewModel.productosCriticos.value
    val desgloseCausas = viewModel.desgloseCausas.value
    val detalleCriticos = viewModel.detalleCriticos.value
    // Vico numera el eje X desde 0; las leyendas numeran desde 1
    val ejeNumerado = rememberBottomAxis(valueFormatter = ejeDesdeUno)

    val modeloLineas = viewModel.modeloLineasCostoMes.value
    val modeloBarras = viewModel.modeloBarrasTopProductos.value
    val modeloCausas = viewModel.modeloBarrasCausas.value

    var mostrarPickerInicio by remember { mutableStateOf(false) }
    var mostrarPickerFin by remember { mutableStateOf(false) }

    if (mostrarPickerInicio) {
        val stateInicio = rememberDatePickerState(
            initialSelectedDateMillis = fechaInicio.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarPickerInicio = false },
            confirmButton = {
                TextButton(onClick = {
                    stateInicio.selectedDateMillis?.let { millis ->
                        val elegida = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.actualizarRangoFechas(elegida, fechaFin)
                    }
                    mostrarPickerInicio = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarPickerInicio = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = stateInicio)
        }
    }

    if (mostrarPickerFin) {
        val stateFin = rememberDatePickerState(
            initialSelectedDateMillis = fechaFin.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarPickerFin = false },
            confirmButton = {
                TextButton(onClick = {
                    stateFin.selectedDateMillis?.let { millis ->
                        val elegida = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.actualizarRangoFechas(fechaInicio, elegida)
                    }
                    mostrarPickerFin = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarPickerFin = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = stateFin)
        }
    }

    Scaffold(
        topBar = {
            TopBarScrapp(
                title = "Panel de Gerencia",
                showBackButton = false,
                actions = {
                    IconButton(onClick = { viewModel.exportarReporteCsv(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar reporte CSV")
                    }
                    IconButton(onClick = { navController.navigate(RutasBottom.GestionUsuarios.route) }) {
                        Icon(Icons.Default.People, contentDescription = "Gestión de usuarios")
                    }
                    IconButton(onClick = { navController.navigate(RutasBottom.CatalogoProductos.route) }) {
                        Icon(Icons.Default.LocalCafe, contentDescription = "Catálogo de productos")
                    }
                    IconButton(onClick = { navController.navigate(Rutas.ConfigurarMotores.route) }) {
                        Icon(Icons.Default.Tune, contentDescription = "Configuración de motores")
                    }
                    if (onCerrarSesion != null) {
                        BotonCerrarSesion(onCerrarSesion = onCerrarSesion)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Periodo del reporte",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = {
                                val hoy = LocalDate.now()
                                viewModel.actualizarRangoFechas(hoy.withDayOfMonth(1), hoy)
                            }) {
                                Text("Este mes")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { mostrarPickerInicio = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Fecha inicio", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Desde: ${fechaInicio.format(formatoFecha)}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = { mostrarPickerFin = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Fecha fin", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hasta: ${fechaFin.format(formatoFecha)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item { Text(text = "Resumen Financiero", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaFinanciera("Pérdida de Hoy", String.format(Locale.US, "$%.2f", perdidaDia), Modifier.weight(1f))
                    TarjetaFinanciera("Pérdida Periodo", String.format(Locale.US, "$%.2f", perdidaMes), Modifier.weight(1f))
                }
            }
            item { TarjetaFinanciera("Índice de Merma del Periodo", String.format(Locale.US, "%.1f%%", indiceMerma), Modifier.fillMaxWidth()) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tendencia Diaria de Costo de Mermas ($)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Chart(
                            chart = lineChart(),
                            model = modeloLineas,
                            startAxis = rememberStartAxis(),
                            bottomAxis = ejeNumerado,
                            modifier = Modifier.height(160.dp)
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Distribución del Desperdicio Crítico (%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Chart(
                            chart = columnChart(),
                            model = modeloBarras,
                            startAxis = rememberStartAxis(),
                            bottomAxis = ejeNumerado,
                            modifier = Modifier.height(160.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Leyenda: el número coincide con el orden de las barras
                        detalleCriticos.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${index + 1}. ${item.nombreProducto}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextoSecundario
                                )
                                Text(
                                    text = String.format(Locale.US, "$%.2f (%.0f%%)", item.costoPerdida, item.porcentajeDelTotal),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Mermas por Causa ($)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Chart(
                            chart = columnChart(),
                            model = modeloCausas,
                            startAxis = rememberStartAxis(),
                            bottomAxis = ejeNumerado,
                            modifier = Modifier.height(160.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        desgloseCausas.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${index + 1}. ${item.causa.descripcion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextoSecundario
                                )
                                Text(
                                    text = String.format(Locale.US, "$%.2f (%.0f%%)", item.costoPerdida, item.porcentajeDelTotal),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Top 5 Productos Críticos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (productosCriticos.isEmpty()) {
                item {
                    Text(
                        text = "No hay mermas registradas en el periodo seleccionado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario
                    )
                }
            }
            items(productosCriticos) { (nombre, porcentaje) ->
                BarraProgresoProducto(
                    nombre = nombre,
                    porcentaje = porcentaje,
                    textoPorcentaje = String.format(Locale.US, "%.0f%%", porcentaje * 100)
                )
            }
        }
    }
}

@Composable
fun TarjetaFinanciera(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            Spacer(modifier = Modifier.height(6.dp))
            Text(valor, style = MaterialTheme.typography.headlineMedium, color = AlertaCritica, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BarraProgresoProducto(nombre: String, porcentaje: Float, textoPorcentaje: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(textoPorcentaje, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(6.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(porcentaje.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(AlertaCritica, RoundedCornerShape(6.dp))
            )
        }
    }
}