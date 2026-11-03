package com.ctrlcafe.scrapp.vista.administracion

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ctrlcafe.scrapp.controlador.CatalogoUiState
import com.ctrlcafe.scrapp.controlador.ProductoFormState
import com.ctrlcafe.scrapp.controlador.ProductoViewModel
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.vista.componentes.BotonPrimario
import com.ctrlcafe.scrapp.vista.componentes.DialogoConfirmacion
import com.ctrlcafe.scrapp.vista.componentes.EstadoVacio
import com.ctrlcafe.scrapp.vista.componentes.ScrappTextField
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp
import java.util.Locale

/**
 * Catálogo de productos (Integrante 3, solo administrador).
 *
 * Crear, editar y desactivar productos, con validaciones de `Validador`. Un producto
 * desactivado deja de ofrecerse en el ingreso de lotes y en la proyección, pero sigue
 * existiendo para que el historial de lotes y mermas conserve su nombre.
 *
 * Conexión en `ScrappNavHost.kt` (ruta protegida por rol):
 *   `CatalogoProductosScreen(adminUid = usuarioActual.uid, onVolver = { navController.popBackStack() })`
 */
@Composable
fun CatalogoProductosScreen(
    adminUid: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductoViewModel = viewModel(factory = ProductoViewModel.provideFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.mensaje) {
        uiState.mensaje?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensaje()
        }
    }

    CatalogoProductosContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onVolver = onVolver,
        onNuevo = viewModel::abrirNuevo,
        onEditar = viewModel::abrirEdicion,
        onAlternarActivo = viewModel::pedirConfirmacionAlternar,
        onAlternarMostrarInactivos = viewModel::alternarMostrarInactivos,
        modifier = modifier
    )

    uiState.formulario?.let { form ->
        FormularioProductoSheet(
            form = form,
            guardando = uiState.guardando,
            onNombre = viewModel::onNombreChange,
            onCategoria = viewModel::onCategoriaChange,
            onCosto = viewModel::onCostoChange,
            onPrecio = viewModel::onPrecioChange,
            onUnidad = viewModel::onUnidadChange,
            onGuardar = { viewModel.guardar(adminUid) },
            onCerrar = viewModel::cerrarFormulario
        )
    }

    uiState.productoPorAlternar?.let { producto ->
        DialogoConfirmacion(
            titulo = if (producto.activo) "Desactivar producto" else "Activar producto",
            mensaje = if (producto.activo) {
                "${producto.nombre} dejará de aparecer en el ingreso de lotes y en la proyección. " +
                    "Su historial se conserva."
            } else {
                "${producto.nombre} volverá a estar disponible para ingresar lotes."
            },
            onConfirm = viewModel::confirmarAlternar,
            onDismiss = viewModel::cancelarAlternar
        )
    }
}

@Composable
fun CatalogoProductosContent(
    uiState: CatalogoUiState,
    snackbarHostState: SnackbarHostState,
    onVolver: () -> Unit,
    onNuevo: () -> Unit,
    onEditar: (Producto) -> Unit,
    onAlternarActivo: (Producto) -> Unit,
    onAlternarMostrarInactivos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activos = uiState.productos.count { it.activo }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopBarScrapp(
                title = "Catálogo de Productos",
                subtitle = "$activos activos de ${uiState.productos.size}",
                showBackButton = true,
                onBackClick = onVolver,
                actions = {
                    IconButton(onClick = onAlternarMostrarInactivos) {
                        Icon(
                            imageVector = if (uiState.mostrarInactivos) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (uiState.mostrarInactivos) "Ocultar inactivos" else "Mostrar inactivos"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNuevo,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo producto") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.guardando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                uiState.cargando -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.productosVisibles.isEmpty() -> {
                    EstadoVacio(
                        titulo = "Catálogo vacío",
                        mensaje = "Agregue el primer producto con el botón \"Nuevo producto\".",
                        icono = Icons.Default.LocalCafe
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = uiState.productosVisibles, key = { it.id }) { producto ->
                            TarjetaProductoCatalogo(
                                producto = producto,
                                habilitado = !uiState.guardando,
                                onEditar = { onEditar(producto) },
                                onAlternarActivo = { onAlternarActivo(producto) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaProductoCatalogo(
    producto: Producto,
    habilitado: Boolean,
    onEditar: () -> Unit,
    onAlternarActivo: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (producto.activo) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (producto.activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${producto.id} · ${producto.categoria} · ${producto.unidadMedida}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Costo ${dinero(producto.costoUnitario)} · Venta ${dinero(producto.precioVenta)}" +
                        if (producto.activo) "" else " · Inactivo",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onEditar, enabled = habilitado) {
                Icon(Icons.Default.Edit, contentDescription = "Editar ${producto.nombre}")
            }
            Switch(
                checked = producto.activo,
                onCheckedChange = { onAlternarActivo() },
                enabled = habilitado
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioProductoSheet(
    form: ProductoFormState,
    guardando: Boolean,
    onNombre: (String) -> Unit,
    onCategoria: (String) -> Unit,
    onCosto: (String) -> Unit,
    onPrecio: (String) -> Unit,
    onUnidad: (String) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (form.esNuevo) "Nuevo producto" else "Editar ${form.idEditando}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ScrappTextField(
                value = form.nombre,
                onValueChange = onNombre,
                label = "Nombre",
                placeholder = "Ej. Croissant de Almendra",
                isError = form.errorNombre != null,
                errorMessage = form.errorNombre
            )
            ScrappTextField(
                value = form.categoria,
                onValueChange = onCategoria,
                label = "Categoría",
                placeholder = "Ej. Panadería",
                isError = form.errorCategoria != null,
                errorMessage = form.errorCategoria
            )
            ScrappTextField(
                value = form.unidadMedida,
                onValueChange = onUnidad,
                label = "Unidad de medida",
                placeholder = "Ej. Unidad, Litro, Porción",
                isError = form.errorUnidad != null,
                errorMessage = form.errorUnidad
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScrappTextField(
                    value = form.costoUnitarioTexto,
                    onValueChange = onCosto,
                    label = "Costo ($)",
                    placeholder = "0.65",
                    isError = form.errorCosto != null,
                    errorMessage = form.errorCosto,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                ScrappTextField(
                    value = form.precioVentaTexto,
                    onValueChange = onPrecio,
                    label = "Precio venta ($)",
                    placeholder = "1.75",
                    isError = form.errorPrecio != null,
                    errorMessage = form.errorPrecio,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            if (guardando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            BotonPrimario(
                text = if (guardando) "Guardando..." else "Guardar",
                onClick = onGuardar,
                enabled = !guardando,
                icono = Icons.Default.Save
            )
        }
    }
}

private fun dinero(valor: Double): String = String.format(Locale.US, "$%.2f", valor)
