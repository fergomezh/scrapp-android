package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.servicio.EstadoLote
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Filtros disponibles en el Monitor de Lotes según el nivel de alerta del semáforo.
 */
enum class FiltroEstadoLote(val etiqueta: String, val nivelAlerta: String?) {
    TODOS("Todos", null),
    VERDE("Verde", "VERDE"),
    AMARILLO("Amarillo", "AMARILLO"),
    ROJO("Rojo", "ROJO"),
    VENCIDO("Vencido", "NEGRO")
}

/**
 * Estado del formulario de Ingreso de Lote con cálculo previo de caducidad y errores por campo.
 */
data class IngresoLoteFormState(
    val productoId: String = "",
    val cantidadTexto: String = "",
    val costoUnitarioTexto: String = "",
    val fechaIngresoTexto: String = LocalDate.now().toString(),
    val vidaUtilDiasTexto: String = "",
    val fechaCaducidadCalculada: LocalDate? = null,
    val errorProducto: String? = null,
    val errorCantidad: String? = null,
    val errorCosto: String? = null,
    val errorFechaIngreso: String? = null,
    val errorVidaUtil: String? = null,
    val guardadoExitoso: Boolean = false,
    val ultimoLoteCreadoId: String? = null
)

/**
 * Estado general expuesto por LoteViewModel para MonitorLotesScreen e IngresoLoteScreen.
 */
data class LoteUiState(
    val lotes: List<EstadoLote> = emptyList(),
    val filtroActual: FiltroEstadoLote = FiltroEstadoLote.TODOS,
    val conteoPorEstado: Map<String, Int> = emptyMap(),
    val productosCatalogo: List<Producto> = emptyList(),
    val formulario: IngresoLoteFormState = IngresoLoteFormState(),
    val cargando: Boolean = false,
    val mensajeError: String? = null
) {
    /** Lista de lotes ya ordenada por urgencia y filtrada según el chip seleccionado. */
    val lotesFiltrados: List<EstadoLote>
        get() = if (filtroActual.nivelAlerta == null) {
            lotes
        } else {
            lotes.filter { it.estado.nivelAlerta == filtroActual.nivelAlerta }
        }

    /** Producto actualmente seleccionado en el formulario de ingreso. */
    val productoSeleccionado: Producto?
        get() = productosCatalogo.find { it.id == formulario.productoId }
}

/**
 * Controlador (ViewModel) para el Monitor de Lotes e Ingreso de Lotes (Integrante 3).
 */
class LoteViewModel(
    private val motorSemaforo: MotorSemaforo = ProveedorDependenciasModuloPrincipal.motorSemaforo,
    private val loteRepositorio: LoteRepositorio = ProveedorDependenciasModuloPrincipal.loteRepositorio,
    private val productoRepositorio: ProductoRepositorio = ProveedorDependenciasModuloPrincipal.productoRepositorio
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoteUiState(cargando = true))
    val uiState: StateFlow<LoteUiState> = _uiState.asStateFlow()

    init {
        recalcularSemaforo()
    }

    /**
     * Recalcula el estado del semáforo de todos los lotes activos ordenados por urgencia.
     * Se invoca al abrir MonitorLotesScreen y al regresar desde RegistroMermaScreen
     * o IngresoLoteScreen para reflejar cambios en tiempo real.
     */
    fun recalcularSemaforo() {
        _uiState.update { it.copy(cargando = true, mensajeError = null) }
        try {
            val productos = productoRepositorio.listar()
            val lotesActualizados = motorSemaforo.recalcularTodos(incluirAgotados = false)
            val conteo = motorSemaforo.conteoPorEstado()

            _uiState.update { estadoActual ->
                estadoActual.copy(
                    lotes = lotesActualizados,
                    conteoPorEstado = conteo,
                    productosCatalogo = productos,
                    cargando = false,
                    mensajeError = null
                )
            }
        } catch (e: Exception) {
            Logger.registrar(
                NivelLog.ERROR,
                LoteViewModel::class.java,
                "Error al recalcular semáforo de lotes: ${e.message}"
            )
            _uiState.update {
                it.copy(
                    cargando = false,
                    mensajeError = e.message ?: "Error al actualizar el monitor de lotes."
                )
            }
        }
    }

    fun seleccionarFiltro(filtro: FiltroEstadoLote) {
        _uiState.update { it.copy(filtroActual = filtro) }
    }

    // =========================================================================
    // Eventos del Formulario de Ingreso de Lote
    // =========================================================================

    fun onProductoSeleccionado(productoId: String) {
        val producto = _uiState.value.productosCatalogo.find { it.id == productoId }
        _uiState.update { estado ->
            val costoSugerido = if (estado.formulario.costoUnitarioTexto.isBlank() && producto != null) {
                producto.costoUnitario.toString()
            } else {
                estado.formulario.costoUnitarioTexto
            }
            estado.copy(
                formulario = estado.formulario.copy(
                    productoId = productoId,
                    costoUnitarioTexto = costoSugerido,
                    errorProducto = null,
                    guardadoExitoso = false
                )
            )
        }
    }

    fun onCantidadChange(valor: String) {
        _uiState.update { estado ->
            estado.copy(
                formulario = estado.formulario.copy(
                    cantidadTexto = valor,
                    errorCantidad = null,
                    guardadoExitoso = false
                )
            )
        }
    }

    fun onCostoUnitarioChange(valor: String) {
        _uiState.update { estado ->
            estado.copy(
                formulario = estado.formulario.copy(
                    costoUnitarioTexto = valor,
                    errorCosto = null,
                    guardadoExitoso = false
                )
            )
        }
    }

    fun onFechaIngresoChange(valor: String) {
        _uiState.update { estado ->
            val nuevaFechaCaducidad = calcularFechaCaducidadPrevia(
                fechaTexto = valor,
                vidaUtilTexto = estado.formulario.vidaUtilDiasTexto
            )
            estado.copy(
                formulario = estado.formulario.copy(
                    fechaIngresoTexto = valor,
                    fechaCaducidadCalculada = nuevaFechaCaducidad,
                    errorFechaIngreso = null,
                    guardadoExitoso = false
                )
            )
        }
    }

    fun onVidaUtilDiasChange(valor: String) {
        _uiState.update { estado ->
            val nuevaFechaCaducidad = calcularFechaCaducidadPrevia(
                fechaTexto = estado.formulario.fechaIngresoTexto,
                vidaUtilTexto = valor
            )
            estado.copy(
                formulario = estado.formulario.copy(
                    vidaUtilDiasTexto = valor,
                    fechaCaducidadCalculada = nuevaFechaCaducidad,
                    errorVidaUtil = null,
                    guardadoExitoso = false
                )
            )
        }
    }

    /**
     * Valida todos los campos del formulario y, si son correctos, crea el lote en el
     * repositorio y recalcula el semáforo inmediatamente.
     *
     * @return true si el lote fue validado y guardado con éxito.
     */
    fun guardarLote(): Boolean {
        val form = _uiState.value.formulario

        val errorProducto = validarCampoObligatorio(form.productoId, "Seleccione un producto")
        val errorCantidad = validarNumeroPositivoDecimal(form.cantidadTexto, "La cantidad")
        val errorCosto = validarNumeroPositivoDecimal(form.costoUnitarioTexto, "El costo unitario")
        val errorFecha = validarFechaNoFutura(form.fechaIngresoTexto)
        val errorVidaUtil = validarEnteroPositivo(form.vidaUtilDiasTexto, "La vida útil en días")

        val hayErrores = listOf(
            errorProducto,
            errorCantidad,
            errorCosto,
            errorFecha,
            errorVidaUtil
        ).any { it != null }

        if (hayErrores) {
            _uiState.update { estado ->
                estado.copy(
                    formulario = form.copy(
                        errorProducto = errorProducto,
                        errorCantidad = errorCantidad,
                        errorCosto = errorCosto,
                        errorFechaIngreso = errorFecha,
                        errorVidaUtil = errorVidaUtil,
                        guardadoExitoso = false
                    )
                )
            }
            return false
        }

        return try {
            val cantidad = form.cantidadTexto.trim().toDouble()
            val costo = form.costoUnitarioTexto.trim().toDouble()
            val fechaIngreso = LocalDate.parse(form.fechaIngresoTexto.trim())
            val vidaUtil = form.vidaUtilDiasTexto.trim().toInt()
            val fechaCaducidad = fechaIngreso.plusDays(vidaUtil.toLong())
            val nuevoId = generarSiguienteIdLote()

            val nuevoLote = Lote(
                id = nuevoId,
                productoId = form.productoId,
                cantidadInicial = cantidad,
                cantidadDisponible = cantidad,
                costoUnitario = costo,
                fechaIngreso = fechaIngreso,
                vidaUtilDias = vidaUtil,
                fechaCaducidad = fechaCaducidad,
                estado = EstadoCaducidad.Vigente
            )

            loteRepositorio.crear(nuevoLote)
            motorSemaforo.recalcular(nuevoLote)

            Logger.info(
                LoteViewModel::class.java,
                "Nuevo lote registrado: $nuevoId (producto=${form.productoId}, caduca=$fechaCaducidad)"
            )

            // Actualizamos lista de lotes y reiniciamos el formulario marcando éxito
            val lotesActualizados = motorSemaforo.recalcularTodos(incluirAgotados = false)
            val conteoActualizado = motorSemaforo.conteoPorEstado()

            _uiState.update { estado ->
                estado.copy(
                    lotes = lotesActualizados,
                    conteoPorEstado = conteoActualizado,
                    formulario = IngresoLoteFormState(
                        fechaIngresoTexto = LocalDate.now().toString(),
                        guardadoExitoso = true,
                        ultimoLoteCreadoId = nuevoId
                    ),
                    mensajeError = null
                )
            }
            true
        } catch (e: Exception) {
            Logger.registrar(
                NivelLog.ERROR,
                LoteViewModel::class.java,
                "Error al guardar lote: ${e.message}"
            )
            _uiState.update {
                it.copy(mensajeError = e.message ?: "No se pudo guardar el lote.")
            }
            false
        }
    }

    fun limpiarFormulario() {
        _uiState.update {
            it.copy(
                formulario = IngresoLoteFormState(
                    fechaIngresoTexto = LocalDate.now().toString()
                )
            )
        }
    }

    fun consumirEventoGuardado() {
        _uiState.update { estado ->
            estado.copy(
                formulario = estado.formulario.copy(guardadoExitoso = false)
            )
        }
    }

// =========================================================================
    // GUÍA DE INTEGRACIÓN PARA EL COORDINADOR — VALIDACIONES (Integrante 2)
    // =========================================================================
    // Como `util/Validador.kt` (rama feat/auth) aún no estaba fusionado al momento
    // de desarrollar este módulo, se implementaron estas 4 validaciones puras locales
    // para garantizar que `IngresoLoteScreen` valide todos los campos sin errores:
    //   1. validarCampoObligatorio(valor, mensaje)  -> Validador: campo obligatorio
    //   2. validarNumeroPositivoDecimal(valor, ...) -> Validador: número positivo (Double)
    //   3. validarEnteroPositivo(valor, ...)        -> Validador: número positivo (Int)
    //   4. validarFechaNoFutura(valor)              -> Validador: fecha no futura
    //
    // Al fusionar `feat/auth` en `develop`, puede mantener estas funciones o delegar
    // su cuerpo directamente a los métodos de `com.ctrlcafe.scrapp.util.Validador`.
    // =========================================================================

    private fun calcularFechaCaducidadPrevia(fechaTexto: String, vidaUtilTexto: String): LocalDate? {
        return try {
            val fecha = LocalDate.parse(fechaTexto.trim())
            val dias = vidaUtilTexto.trim().toInt()
            if (dias > 0) fecha.plusDays(dias.toLong()) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun validarCampoObligatorio(valor: String, mensaje: String): String? {
        return if (valor.trim().isEmpty()) mensaje else null
    }

    private fun validarNumeroPositivoDecimal(valor: String, nombreCampo: String): String? {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return "$nombreCampo es obligatorio"
        val numero = limpio.toDoubleOrNull() ?: return "Ingrese un número válido (ej. 10.5)"
        if (numero <= 0.0) return "$nombreCampo debe ser mayor a 0"
        return null
    }

    private fun validarEnteroPositivo(valor: String, nombreCampo: String): String? {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return "$nombreCampo es obligatoria"
        val entero = limpio.toIntOrNull() ?: return "Ingrese un número entero de días (ej. 7)"
        if (entero <= 0) return "$nombreCampo debe ser al menos 1 día"
        return null
    }

    private fun validarFechaNoFutura(valor: String): String? {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return "La fecha de ingreso es obligatoria"
        val fecha = try {
            LocalDate.parse(limpio)
        } catch (_: DateTimeParseException) {
            return "Formato inválido. Use AAAA-MM-DD (ej. ${LocalDate.now()})"
        }
        if (fecha.isAfter(LocalDate.now())) {
            return "La fecha de ingreso no puede ser futura"
        }
        return null
    }

    private fun generarSiguienteIdLote(): String {
        val maxNumero = loteRepositorio.listar()
            .mapNotNull { lote ->
                lote.id.removePrefix("LOT-").toIntOrNull()
            }
            .maxOrNull() ?: 0
        return "LOT-${(maxNumero + 1).toString().padStart(3, '0')}"
    }

    companion object {
        fun provideFactory(
            motorSemaforo: MotorSemaforo = ProveedorDependenciasModuloPrincipal.motorSemaforo,
            loteRepositorio: LoteRepositorio = ProveedorDependenciasModuloPrincipal.loteRepositorio,
            productoRepositorio: ProductoRepositorio = ProveedorDependenciasModuloPrincipal.productoRepositorio
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoteViewModel(
                    motorSemaforo = motorSemaforo,
                    loteRepositorio = loteRepositorio,
                    productoRepositorio = productoRepositorio
                )
            }
        }
    }
}