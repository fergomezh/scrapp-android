package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.repositorio.firestore.FirestoreProductoRepositorio
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import com.ctrlcafe.scrapp.util.Validador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Formulario de crear/editar producto, con el error de cada campo. */
data class ProductoFormState(
    /** `null` al crear; el id del producto al editar. */
    val idEditando: String? = null,
    val nombre: String = "",
    val categoria: String = "",
    val costoUnitarioTexto: String = "",
    val precioVentaTexto: String = "",
    val unidadMedida: String = "",
    val errorNombre: String? = null,
    val errorCategoria: String? = null,
    val errorCosto: String? = null,
    val errorPrecio: String? = null,
    val errorUnidad: String? = null
) {
    val esNuevo: Boolean get() = idEditando == null
}

data class CatalogoUiState(
    val productos: List<Producto> = emptyList(),
    val mostrarInactivos: Boolean = true,
    /** `null` cuando el formulario está cerrado. */
    val formulario: ProductoFormState? = null,
    /** Producto que espera confirmación para activarse o desactivarse. */
    val productoPorAlternar: Producto? = null,
    val guardando: Boolean = false,
    val cargando: Boolean = true,
    val mensaje: String? = null
) {
    val productosVisibles: List<Producto>
        get() = if (mostrarInactivos) productos else productos.filter { it.activo }
}

/**
 * Controlador del Catálogo de productos (Integrante 3, solo administrador).
 *
 * Crea, edita y desactiva productos. No se borran: hay lotes y mermas que los
 * referencian por id y el historial dejaría de mostrar su nombre.
 */
class ProductoViewModel(
    private val productoRepositorio: FirestoreProductoRepositorio = ContenedorDatos.productoRepositorio
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogoUiState())
    val uiState: StateFlow<CatalogoUiState> = _uiState.asStateFlow()

    init {
        // Tiempo real: lo que cambie otro administrador aparece sin recargar
        viewModelScope.launch {
            productoRepositorio.productosFlow.collect { productos ->
                _uiState.update {
                    it.copy(
                        productos = productos.sortedWith(compareByDescending<Producto> { p -> p.activo }.thenBy { p -> p.nombre }),
                        cargando = false
                    )
                }
            }
        }
    }

    fun alternarMostrarInactivos() {
        _uiState.update { it.copy(mostrarInactivos = !it.mostrarInactivos) }
    }

    // ------------------------------------------------------------- Formulario

    fun abrirNuevo() {
        _uiState.update { it.copy(formulario = ProductoFormState()) }
    }

    fun abrirEdicion(producto: Producto) {
        _uiState.update {
            it.copy(
                formulario = ProductoFormState(
                    idEditando = producto.id,
                    nombre = producto.nombre,
                    categoria = producto.categoria,
                    costoUnitarioTexto = producto.costoUnitario.toString(),
                    precioVentaTexto = producto.precioVenta.toString(),
                    unidadMedida = producto.unidadMedida
                )
            )
        }
    }

    fun cerrarFormulario() {
        if (_uiState.value.guardando) return
        _uiState.update { it.copy(formulario = null) }
    }

    fun onNombreChange(valor: String) = editarFormulario { it.copy(nombre = valor, errorNombre = null) }
    fun onCategoriaChange(valor: String) = editarFormulario { it.copy(categoria = valor, errorCategoria = null) }
    fun onCostoChange(valor: String) = editarFormulario { it.copy(costoUnitarioTexto = valor, errorCosto = null) }
    fun onPrecioChange(valor: String) = editarFormulario { it.copy(precioVentaTexto = valor, errorPrecio = null) }
    fun onUnidadChange(valor: String) = editarFormulario { it.copy(unidadMedida = valor, errorUnidad = null) }

    private fun editarFormulario(cambio: (ProductoFormState) -> ProductoFormState) {
        _uiState.update { estado -> estado.copy(formulario = estado.formulario?.let(cambio)) }
    }

    /** Valida con [Validador] y guarda en Firestore; [adminUid] queda como `creadoPor` al crear. */
    fun guardar(adminUid: String) {
        val form = _uiState.value.formulario ?: return
        if (_uiState.value.guardando) return

        val nombreDuplicado = _uiState.value.productos.any {
            it.id != form.idEditando && it.nombre.trim().equals(form.nombre.trim(), ignoreCase = true)
        }
        val errorPrecioBase = Validador.validarNumeroPositivo(form.precioVentaTexto, "El precio de venta")
        val validado = form.copy(
            errorNombre = Validador.validarCampoRequerido(form.nombre, "El nombre")
                ?: if (nombreDuplicado) "Ya existe un producto con ese nombre." else null,
            errorCategoria = Validador.validarCampoRequerido(form.categoria, "La categoría"),
            errorCosto = Validador.validarNumeroPositivo(form.costoUnitarioTexto, "El costo unitario"),
            errorPrecio = errorPrecioBase ?: validarPrecioContraCosto(form),
            errorUnidad = Validador.validarCampoRequerido(form.unidadMedida, "La unidad de medida")
        )
        val hayErrores = listOf(
            validado.errorNombre, validado.errorCategoria, validado.errorCosto,
            validado.errorPrecio, validado.errorUnidad
        ).any { it != null }
        if (hayErrores) {
            _uiState.update { it.copy(formulario = validado) }
            return
        }

        val existente = form.idEditando?.let { id -> _uiState.value.productos.find { it.id == id } }
        val producto = Producto(
            id = form.idEditando ?: generarSiguienteId(),
            nombre = form.nombre.trim(),
            categoria = form.categoria.trim(),
            costoUnitario = form.costoUnitarioTexto.trim().toDouble(),
            precioVenta = form.precioVentaTexto.trim().toDouble(),
            unidadMedida = form.unidadMedida.trim(),
            activo = existente?.activo ?: true,
            creadoPor = existente?.creadoPor ?: adminUid
        )

        ejecutarEscritura(
            producto = producto,
            esNuevo = form.esNuevo,
            mensajeExito = if (form.esNuevo) "Producto ${producto.nombre} creado" else "Producto ${producto.nombre} actualizado",
            cerrarFormulario = true
        )
    }

    private fun validarPrecioContraCosto(form: ProductoFormState): String? {
        val costo = form.costoUnitarioTexto.trim().toDoubleOrNull() ?: return null
        val precio = form.precioVentaTexto.trim().toDoubleOrNull() ?: return null
        return if (precio < costo) "El precio de venta no puede ser menor que el costo." else null
    }

    // ------------------------------------------------------ Activar / desactivar

    fun pedirConfirmacionAlternar(producto: Producto) {
        _uiState.update { it.copy(productoPorAlternar = producto) }
    }

    fun cancelarAlternar() {
        _uiState.update { it.copy(productoPorAlternar = null) }
    }

    fun confirmarAlternar() {
        val producto = _uiState.value.productoPorAlternar ?: return
        _uiState.update { it.copy(productoPorAlternar = null) }
        val nuevo = producto.copy(activo = !producto.activo)
        ejecutarEscritura(
            producto = nuevo,
            esNuevo = false,
            mensajeExito = if (nuevo.activo) "${nuevo.nombre} activado" else "${nuevo.nombre} desactivado",
            cerrarFormulario = false
        )
    }

    fun consumirMensaje() {
        _uiState.update { it.copy(mensaje = null) }
    }

    private fun ejecutarEscritura(
        producto: Producto,
        esNuevo: Boolean,
        mensajeExito: String,
        cerrarFormulario: Boolean
    ) {
        _uiState.update { it.copy(guardando = true) }
        viewModelScope.launch {
            var exito = false
            val mensaje = try {
                // Sin conexión la escritura queda en caché y Firestore la sube al volver la red
                val confirmado = withTimeoutOrNull(TIEMPO_ESPERA_GUARDADO_MS) {
                    productoRepositorio.guardarConfirmado(producto, esNuevo)
                } != null
                Logger.info(ProductoViewModel::class.java, "Producto ${producto.id} guardado (confirmado=$confirmado)")
                exito = true
                if (confirmado) mensajeExito else "$mensajeExito sin conexión; se sincronizará al volver la red"
            } catch (e: Exception) {
                Logger.registrar(NivelLog.ERROR, ProductoViewModel::class.java, "Error al guardar producto: ${e.message}")
                if (e.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Solo un administrador activo puede modificar el catálogo."
                } else {
                    "No se pudo guardar el producto. ${e.message.orEmpty()}"
                }
            }
            _uiState.update {
                it.copy(
                    guardando = false,
                    formulario = if (exito && cerrarFormulario) null else it.formulario,
                    mensaje = mensaje
                )
            }
        }
    }

    /** Sigue la numeración de los datos de ejemplo: PROD-01, PROD-02, ... */
    private fun generarSiguienteId(): String {
        val maxNumero = _uiState.value.productos
            .mapNotNull { it.id.removePrefix("PROD-").toIntOrNull() }
            .maxOrNull() ?: 0
        return "PROD-${(maxNumero + 1).toString().padStart(2, '0')}"
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProductoViewModel() }
        }

        private const val TIEMPO_ESPERA_GUARDADO_MS = 8_000L
    }
}
