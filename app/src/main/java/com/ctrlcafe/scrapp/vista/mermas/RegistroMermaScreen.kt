package com.ctrlcafe.scrapp.vista.mermas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ctrlcafe.scrapp.controlador.MermaEstadoUi
import com.ctrlcafe.scrapp.controlador.MermaViewModel
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.EstadoSubida
import com.ctrlcafe.scrapp.vista.componentes.MiniaturaEvidencia
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp
import com.ctrlcafe.scrapp.vista.mermas.CamaraEvidenciaScreen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroMermaScreen(
    viewModel: MermaViewModel,
    usuarioId: String,
    productoPreseleccionadoId: String? = null,
    lotePreseleccionadoId: String? = null,
    onRegistroCompletado: () -> Unit = {},
    onVolver: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val estadoUi by viewModel.estadoUi.collectAsState()
    val productos by viewModel.productos.collectAsState()
    val lotesDisponibles by viewModel.lotesDisponibles.collectAsState()

    var mostrarCamaraHardware by remember { mutableStateOf(false) }
    val estadoSubidaEvidencia = viewModel.estadoSubidaEvidencia.value
    val urlEvidenciaCloudinary = viewModel.urlEvidenciaCloudinary.value

    var productoSeleccionado by remember { mutableStateOf<Producto?>(null) }
    var expandirProductos by remember { mutableStateOf(false) }

    var loteSeleccionado by remember { mutableStateOf<Lote?>(null) }
    var expandirLotes by remember { mutableStateOf(false) }

    var causaSeleccionada by remember { mutableStateOf(CausaMerma.VENCIMIENTO) }
    var expandirCausas by remember { mutableStateOf(false) }

    var cantidadTexto by remember { mutableStateOf("") }

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

    var lotePreseleccionAplicada by remember { mutableStateOf(false) }
    LaunchedEffect(lotesDisponibles, lotePreseleccionadoId) {
        if (lotePreseleccionadoId != null && !lotePreseleccionAplicada) {
            val pre = lotesDisponibles.firstOrNull { it.id == lotePreseleccionadoId }
            if (pre != null) {
                loteSeleccionado = pre
                lotePreseleccionAplicada = true
            }
        }
    }

    LaunchedEffect(estadoUi) {
        if (estadoUi is MermaEstadoUi.Error) {
            snackbarHostState.showSnackbar((estadoUi as MermaEstadoUi.Error).mensaje)
        }
    }

    if (mostrarCamaraHardware) {
        CamaraEvidenciaScreen(
            onImagenConfirmada = { archivoLocal ->
                mostrarCamaraHardware = false
                viewModel.procesarYSubirFotoEvidencia(archivoLocal)
            },
            onCancelar = { mostrarCamaraHardware = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopBarScrapp(
                    title = "Registrar Merma",
                    showBackButton = onVolver != null,
                    onBackClick = { onVolver?.invoke() }
                )
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
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        viewModel.reiniciarFormulario()
                        viewModel.cancelarYRemoverEvidencia()
                        cantidadTexto = ""
                        loteSeleccionado = null
                        productoSeleccionado?.let { viewModel.seleccionarProducto(it.id) }
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
                        onExpandedChange = { expandirProductos = it }
                    ) {
                        OutlinedTextField(
                            value = productoSeleccionado?.nombre ?: "Seleccione Producto",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirProductos) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expandirProductos,
                            onDismissRequest = { expandirProductos = false }
                        ) {
                            productos.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text(prod.nombre) },
                                    onClick = {
                                        productoSeleccionado = prod
                                        loteSeleccionado = null
                                        expandirProductos = false
                                        viewModel.seleccionarProducto(prod.id)
                                    }
                                )
                            }
                        }
                    }

                    // Selector de Lote (Continuación y Cierre)
                    ExposedDropdownMenuBox(
                        expanded = expandirLotes,
                        onExpandedChange = { if (productoSeleccionado != null) expandirLotes = it }
                    ) {
                        OutlinedTextField(
                            value = loteSeleccionado?.let { "Lote #${it.id} (Disp: ${it.cantidadDisponible})" } ?: "Seleccione Lote",
                            onValueChange = {},
                            readOnly = true,
                            enabled = productoSeleccionado != null,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirLotes) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expandirLotes,
                            onDismissRequest = { expandirLotes = false }
                        ) {
                            lotesDisponibles.forEach { lote ->
                                DropdownMenuItem(
                                    text = { Text("Lote #${lote.id} - Cánt: ${lote.cantidadDisponible}") },
                                    onClick = {
                                        loteSeleccionado = lote
                                        expandirLotes = false
                                    }
                                )
                            }
                        }
                    }

                    // Selector de Causa de Merma
                    ExposedDropdownMenuBox(
                        expanded = expandirCausas,
                        onExpandedChange = { expandirCausas = it }
                    ) {
                        OutlinedTextField(
                            value = causaSeleccionada.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirCausas) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expandirCausas,
                            onDismissRequest = { expandirCausas = false }
                        ) {
                            CausaMerma.values().forEach { causa ->
                                DropdownMenuItem(
                                    text = { Text(causa.name) },
                                    onClick = {
                                        causaSeleccionada = causa
                                        expandirCausas = false
                                    }
                                )
                            }
                        }
                    }

                    // Input de Cantidad Mermada
                    OutlinedTextField(
                        value = cantidadTexto,
                        onValueChange = { cantidadTexto = it },
                        label = { Text("Cantidad Desperdiciada") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // --- SECCIÓN DE EVIDENCIA FOTOGRÁFICA INTERACTIVA ---
                    Text(
                        text = "Evidencia Física Obligatoria",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (estadoSubidaEvidencia) {
                            is EstadoSubida.Procesando -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(progress = { estadoSubidaEvidencia.progreso })
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Subiendo a Cloudinary...", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            else -> {
                                if (urlEvidenciaCloudinary != null) {
                                    MiniaturaEvidencia(
                                        urlImagen = urlEvidenciaCloudinary,
                                        onEliminarClick = { viewModel.cancelarYRemoverEvidencia() }
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { mostrarCamaraHardware = true },
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Tomar Foto de Evidencia", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón de Envío Validado por Evidencia
                    Button(
                        onClick = {
                            val cantidadVal = cantidadTexto.toDoubleOrNull()
                            if (loteSeleccionado != null && cantidadVal != null && productoSeleccionado != null) {
                                val mermaAEnviar = com.ctrlcafe.scrapp.modelo.Merma(
                                    id = "",
                                    loteId = loteSeleccionado!!.id,
                                    productoId = productoSeleccionado!!.id,
                                    usuarioId = usuarioId,
                                    cantidad = cantidadVal,
                                    causa = causaSeleccionada,
                                    fecha = LocalDate.now(),
                                    costoUnitarioCongelado = 0.0,
                                    rutaEvidencia = urlEvidenciaCloudinary ?: ""
                                )
                                viewModel.intentarGuardarMermaConValidacion(mermaAEnviar)
                            }
                        },
                        enabled = urlEvidenciaCloudinary != null && loteSeleccionado != null && cantidadTexto.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Confirmar y Guardar Registro")
                    }
                }
            }
        }
    }
}
