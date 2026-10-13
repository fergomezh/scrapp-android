package com.ctrlcafe.scrapp.vista.lotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ctrlcafe.scrapp.controlador.LoteUiState
import com.ctrlcafe.scrapp.controlador.LoteViewModel
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.vista.componentes.BotonPrimario
import com.ctrlcafe.scrapp.vista.componentes.ScrappTextField
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de formulario para el Ingreso de Lotes (Integrante 3).
 *
 * Permite seleccionar producto, ingresar cantidad, costo, fecha de ingreso y vida útil
 * en días; calcula y muestra en vivo la fecha de caducidad antes de guardar y valida
 * cada campo mostrando errores en `supportingText`.
 */
@Composable
fun IngresoLoteScreen(
    modifier: Modifier = Modifier,
    viewModel: LoteViewModel = viewModel(factory = LoteViewModel.provideFactory()),
    onVolver: () -> Unit = {},
    onLoteGuardado: () -> Unit = onVolver
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.formulario.guardadoExitoso) {
        if (uiState.formulario.guardadoExitoso) {
            val idCreado = uiState.formulario.ultimoLoteCreadoId ?: ""
            snackbarHostState.showSnackbar("Lote $idCreado registrado correctamente")
            viewModel.consumirEventoGuardado()
            onLoteGuardado()
        }
    }

    IngresoLoteContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onProductoSeleccionado = viewModel::onProductoSeleccionado,
        onCantidadChange = viewModel::onCantidadChange,
        onCostoUnitarioChange = viewModel::onCostoUnitarioChange,
        onFechaIngresoChange = viewModel::onFechaIngresoChange,
        onVidaUtilDiasChange = viewModel::onVidaUtilDiasChange,
        onGuardarClick = { viewModel.guardarLote() },
        onLimpiarClick = viewModel::limpiarFormulario,
        onVolver = onVolver,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngresoLoteContent(
    uiState: LoteUiState,
    snackbarHostState: SnackbarHostState,
    onProductoSeleccionado: (String) -> Unit,
    onCantidadChange: (String) -> Unit,
    onCostoUnitarioChange: (String) -> Unit,
    onFechaIngresoChange: (String) -> Unit,
    onVidaUtilDiasChange: (String) -> Unit,
    onGuardarClick: () -> Unit,
    onLimpiarClick: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val form = uiState.formulario
    val unidadMedida = uiState.productoSeleccionado?.unidadMedida ?: "Unidades"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopBarScrapp(
                title = "Ingreso de Lote",
                subtitle = "Registro y cálculo de caducidad",
                showBackButton = true,
                onBackClick = onVolver
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Selector desplegable de Producto
            SelectorProductoDropdown(
                productos = uiState.productosCatalogo,
                productoSeleccionado = uiState.productoSeleccionado,
                error = form.errorProducto,
                onProductoSeleccionado = onProductoSeleccionado
            )

            // 2. Campo de Cantidad Inicial
            ScrappTextField(
                value = form.cantidadTexto,
                onValueChange = onCantidadChange,
                label = "Cantidad de ingreso ($unidadMedida)",
                placeholder = "Ej. 25.0",
                isError = form.errorCantidad != null,
                errorMessage = form.errorCantidad,
                supportingText = "Ingrese la cantidad total recibida o producida",
                keyboardType = KeyboardType.Decimal
            )

            // 3. Campo de Costo Unitario
            ScrappTextField(
                value = form.costoUnitarioTexto,
                onValueChange = onCostoUnitarioChange,
                label = "Costo unitario ($ USD)",
                placeholder = "Ej. 1.80",
                isError = form.errorCosto != null,
                errorMessage = form.errorCosto,
                supportingText = "Costo por unidad de medida ($unidadMedida)",
                keyboardType = KeyboardType.Decimal
            )

            // 4. Campo de Fecha de Ingreso
            ScrappTextField(
                value = form.fechaIngresoTexto,
                onValueChange = onFechaIngresoChange,
                label = "Fecha de ingreso (AAAA-MM-DD)",
                placeholder = LocalDate.now().toString(),
                isError = form.errorFechaIngreso != null,
                errorMessage = form.errorFechaIngreso,
                supportingText = "No se permiten fechas futuras",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null
                    )
                }
            )

            // 5. Campo de Vida Útil en días
            ScrappTextField(
                value = form.vidaUtilDiasTexto,
                onValueChange = onVidaUtilDiasChange,
                label = "Vida útil (días)",
                placeholder = "Ej. 7",
                isError = form.errorVidaUtil != null,
                errorMessage = form.errorVidaUtil,
                supportingText = "Número entero de días antes del vencimiento",
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Tarjeta de vista previa: Fecha de caducidad calculada antes de guardar
            TarjetaCaducidadPrevia(
                fechaCaducidad = form.fechaCaducidadCalculada,
                vidaUtilDiasTexto = form.vidaUtilDiasTexto
            )

            if (uiState.mensajeError != null) {
                Text(
                    text = uiState.mensajeError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onLimpiarClick,
                    // Misma altura que BotonPrimario para que queden alineados
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Limpiar")
                }

                BotonPrimario(
                    text = "Guardar lote",
                    onClick = onGuardarClick,
                    icono = Icons.Default.Save,
                    modifier = Modifier.weight(1.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorProductoDropdown(
    productos: List<Producto>,
    productoSeleccionado: Producto?,
    error: String?,
    onProductoSeleccionado: (String) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = !expandido },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = productoSeleccionado?.let { "${it.nombre} (${it.id})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Producto") },
            placeholder = { Text("Seleccione un producto del catálogo") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido)
            },
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("El costo sugerido se autocompleta según el producto")
                }
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false }
        ) {
            productos.forEach { producto ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = producto.nombre,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${producto.id} · ${producto.categoria} ·$${producto.costoUnitario} /${producto.unidadMedida}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onProductoSeleccionado(producto.id)
                        expandido = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TarjetaCaducidadPrevia(
    fechaCaducidad: LocalDate?,
    vidaUtilDiasTexto: String
) {
    val formatoLargo = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("es", "SV"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fechaCaducidad != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (fechaCaducidad != null) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.EventAvailable
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (fechaCaducidad != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fecha de caducidad estimada",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (fechaCaducidad != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (fechaCaducidad != null) {
                    Text(
                        text = fechaCaducidad.format(formatoLargo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Vence al finalizar el día ($fechaCaducidad) · +$vidaUtilDiasTexto días",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = "Complete una fecha de ingreso válida y la vida útil en días para ver la fecha de caducidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
