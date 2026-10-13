package com.ctrlcafe.scrapp.vista.mermas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ctrlcafe.scrapp.controlador.MermaEstadoUi
import com.ctrlcafe.scrapp.controlador.MermaViewModel
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.util.Validador
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroMermaScreen(
    viewModel: MermaViewModel,
    usuarioId: String,
    productoPreseleccionadoId: String? = null,
    onRegistroCompletado: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val estadoUi by viewModel.estadoUi.collectAsState()
    val productos by viewModel.productos.collectAsState()
    val lotesDisponibles by viewModel.lotesDisponibles.collectAsState()

    var productoSeleccionado by remember { mutableStateOf<Producto?>(null) }
    var expandirProductos by remember { mutableStateOf(false) }

    var loteSeleccionado by remember { mutableStateOf<Lote?>(null) }
    var expandirLotes by remember { mutableStateOf(false) }

    var causaSeleccionada by remember { mutableStateOf(CausaMerma.VENCIMIENTO) }
    var expandirCausas by remember { mutableStateOf(false) }

    var cantidadTexto by remember { mutableStateOf("") }
    var errorCantidad by remember { mutableStateOf<String?>(null) }

    var rutaEvidencia by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(productos, productoPreseleccionadoId) {
        if (productoPreseleccionadoId != null && productoSeleccionado == null) {
            val pre = productos.firstOrNull { it.id == productoPreseleccionadoId }
            if (pre != null) {
                productoSeleccionado = pre
                viewModel.seleccionarProducto(pre.id)
            }
        }
    }

    LaunchedEffect(estadoUi) {
        if (estadoUi is MermaEstadoUi.Error) {
            snackbarHostState.showSnackbar((estadoUi as MermaEstadoUi.Error).mensaje)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Registrar Merma") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (estadoUi is MermaEstadoUi.Exito) {
            val res = (estadoUi as MermaEstadoUi.Exito).resultado
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Merma Registrada Exitosamente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lote: ${res.loteId} | Desperdicio: $${"%.2f".format(res.costoPerdida)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Stock restante: ${res.cantidadLoteDespues}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    viewModel.reiniciarFormulario()
                    cantidadTexto = ""
                    loteSeleccionado = null
                    onRegistroCompletado()
                }) {
                    Text("Registrar otra merma")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Selector de Producto
                ExposedDropdownMenuBox(
                    expanded = expandirProductos,
                    onExpandedChange = { expandirProductos = !expandirProductos }
                ) {
                    OutlinedTextField(
                        value = productoSeleccionado?.nombre ?: "Seleccione un producto",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Producto") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirProductos) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandirProductos,
                        onDismissRequest = { expandirProductos = false }
                    ) {
                        productos.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.nombre} (${prod.unidadMedida})") },
                                onClick = {
                                    productoSeleccionado = prod
                                    loteSeleccionado = null
                                    viewModel.seleccionarProducto(prod.id)
                                    expandirProductos = false
                                }
                            )
                        }
                    }
                }

                // Selector de Lote
                ExposedDropdownMenuBox(
                    expanded = expandirLotes,
                    onExpandedChange = { if (productoSeleccionado != null) expandirLotes = !expandirLotes }
                ) {
                    OutlinedTextField(
                        value = loteSeleccionado?.let { "${it.id} (Disp: ${it.cantidadDisponible})" } ?: "Seleccione el lote",
                        onValueChange = {},
                        readOnly = true,
                        enabled = productoSeleccionado != null && lotesDisponibles.isNotEmpty(),
                        label = { Text("Lote de origen") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirLotes) },
                        supportingText = {
                            if (productoSeleccionado != null && lotesDisponibles.isEmpty()) {
                                Text("No hay lotes con existencias para este producto")
                            }
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandirLotes,
                        onDismissRequest = { expandirLotes = false }
                    ) {
                        lotesDisponibles.forEach { lote ->
                            DropdownMenuItem(
                                text = { Text("${lote.id} | Disp: ${lote.cantidadDisponible} | Vence: ${lote.fechaCaducidad}") },
                                onClick = {
                                    loteSeleccionado = lote
                                    expandirLotes = false
                                }
                            )
                        }
                    }
                }

                // Campo Cantidad
                OutlinedTextField(
                    value = cantidadTexto,
                    onValueChange = {
                        cantidadTexto = it
                        errorCantidad = Validador.validarNumeroPositivo(it)
                    },
                    label = {
                        Text(productoSeleccionado?.let { "Cantidad a mermar (${it.unidadMedida})" } ?: "Cantidad")
                    },
                    isError = errorCantidad != null,
                    supportingText = { errorCantidad?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Causa de Merma
                ExposedDropdownMenuBox(
                    expanded = expandirCausas,
                    onExpandedChange = { expandirCausas = !expandirCausas }
                ) {
                    OutlinedTextField(
                        value = causaSeleccionada.descripcion,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Causa de merma") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirCausas) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandirCausas,
                        onDismissRequest = { expandirCausas = false }
                    ) {
                        CausaMerma.entries.forEach { causa ->
                            DropdownMenuItem(
                                text = { Text(causa.descripcion) },
                                onClick = {
                                    causaSeleccionada = causa
                                    expandirCausas = false
                                }
                            )
                        }
                    }
                }

                // Placeholder / Selector de Evidencia Fotográfica
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            rutaEvidencia = "evidencia_${System.currentTimeMillis()}.jpg"
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Evidencia",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (rutaEvidencia.isEmpty()) "Tocar para adjuntar evidencia fotográfica"
                            else "Evidencia adjunta: $rutaEvidencia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val cargando = estadoUi is MermaEstadoUi.Guardando

                Button(
                    onClick = {
                        errorCantidad = Validador.validarNumeroPositivo(cantidadTexto)
                        val lote = loteSeleccionado
                        val cantNum = cantidadTexto.toDoubleOrNull()

                        if (lote == null) {
                            errorCantidad = "Debe seleccionar un lote."
                            return@Button
                        }
                        if (cantNum == null || cantNum <= 0.0) {
                            errorCantidad = "Ingrese una cantidad válida."
                            return@Button
                        }
                        if (cantNum > lote.cantidadDisponible) {
                            errorCantidad = "Cantidad supera la disponibilidad (${lote.cantidadDisponible})."
                            return@Button
                        }

                        if (errorCantidad == null) {
                            viewModel.registrarMerma(
                                loteId = lote.id,
                                cantidad = cantNum,
                                causa = causaSeleccionada,
                                fecha = LocalDate.now(),
                                rutaEvidencia = rutaEvidencia,
                                usuarioId = usuarioId
                            )
                        }
                    },
                    enabled = !cargando && loteSeleccionado != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Registrar Merma")
                    }
                }
            }
        }
    }
}
