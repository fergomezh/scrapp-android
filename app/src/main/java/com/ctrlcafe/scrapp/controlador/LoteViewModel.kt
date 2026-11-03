package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.ctrlcafe.scrapp.repositorio.firestore.FirestoreLoteRepositorio
import com.ctrlcafe.scrapp.servicio.EstadoLote
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val guardando: Boolean = false,
    val guardadoExitoso: Boolean = false,
    val ultimoLoteCreadoId: String? = null,
    /** Se guardó sin conexión: queda en el dispositivo y Firestore lo sincroniza al volver la red. */
    val guardadoSinConexion: Boolean = false
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
    // MODIFIED: Replaced explicit repository types with generic Repositorio interface
    private val loteRepositorio: Repositorio<Lote> = ProveedorDependenciasModuloPrincipal.loteRepositorio,
    private val productoRepositorio: Repositorio<Producto> = ProveedorDependenciasModuloPrincipal.productoRepositorio,
    // Emite cada vez que cambian lotes o productos en Firestore (de este u otro dispositivo)
    private val cambiosDatos: Flow<Unit> = ProveedorDependenciasModuloPrincipal.cambiosLotes
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoteUiState(cargando = true))
    val uiState: StateFlow<LoteUiState> = _uiState.asStateFlow()

    init {
        // Tiempo real: el monitor se recalcula solo cuando otro dispositivo registra una merma o un lote
        viewModelScope.launch {
            cambiosDatos.collect { recalcularSemaforo() }
        }
    }

    /**
     * Recalcula el estado del semáforo de todos los lotes activos ordenados por urgencia.
     * Lo dispara el listener de Firestore y también MonitorLotesScreen al volver a primer
     * plano, porque las horas restantes cambian aunque no lleguen datos nuevos.
     */
    fun recalcularSemaforo() {
        // El indicador de carga solo aparece la primera vez; las actualizaciones en vivo no parpadean
        _uiState.update { it.copy(cargando = it.lotes.isEmpty(), mensajeError = null) }
        try {
            // Los productos desactivados desde el catálogo no se ofrecen para ingresar lotes nuevos
            val productos = productoRepositorio.listar().filter { it.activo }
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
     * Valida todos los campos del formulario y, si son correctos, crea el lote en Firestore
     * con [usuarioId] como `creadoPor`. El monitor se actualiza solo por el listener.
     */
    fun guardarLote(usuarioId: String) {
        val form = _uiState.value.formulario
        if (form.guardando) return

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
            return
        }

        if (usuarioId.isBlank()) {
            _uiState.update { it.copy(mensajeError = "No hay una sesión activa. Vuelva a iniciar sesión.") }
            return
        }

        _uiState.update { it.copy(formulario = it.formulario.copy(guardando = true), mensajeError = null) }

        viewModelScope.launch {
            try {
                guardarLoteValidado(form, usuarioId)
            } catch (e: Exception) {
                Logger.registrar(
                    NivelLog.ERROR,
                    LoteViewModel::class.java,
                    "Error al guardar lote: ${e.message}"
                )
                _uiState.update {
                    it.copy(
                        formulario = it.formulario.copy(guardando = false),
                        mensajeError = traducirErrorGuardado(e)
                    )
                }
            }
        }
    }

    private suspend fun guardarLoteValidado(form: IngresoLoteFormState, usuarioId: String) {
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
            estado = EstadoCaducidad.Vigente,
            creadoPor = usuarioId
        )

        // Con Firestore se espera la confirmación del servidor para mostrar errores de reglas o red.
        // Sin conexión la escritura queda en la caché local y se sincroniza sola al volver la red.
        val confirmado = if (loteRepositorio is FirestoreLoteRepositorio) {
            withTimeoutOrNull(TIEMPO_ESPERA_GUARDADO_MS) { loteRepositorio.crearConfirmado(nuevoLote) } != null
        } else {
            loteRepositorio.crear(nuevoLote)
            true
        }

        Logger.info(
            LoteViewModel::class.java,
            "Nuevo lote registrado: $nuevoId (producto=${form.productoId}, caduca=$fechaCaducidad, " +
                "creadoPor=$usuarioId, confirmado=$confirmado)"
        )

        // Reiniciamos el formulario marcando éxito; el monitor lo refleja por el listener
        _uiState.update { estado ->
            estado.copy(
                formulario = IngresoLoteFormState(
                    fechaIngresoTexto = LocalDate.now().toString(),
                    guardadoExitoso = true,
                    ultimoLoteCreadoId = nuevoId,
                    guardadoSinConexion = !confirmado
                ),
                mensajeError = null
            )
        }
    }

    private fun traducirErrorGuardado(e: Exception): String {
        val mensaje = e.message.orEmpty()
        return when {
            mensaje.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "No tiene permiso para registrar lotes. Verifique que su cuenta esté activa."
            mensaje.contains("UNAVAILABLE", ignoreCase = true) ->
                "No hay conexión con el servidor. Intente de nuevo."
            else -> "No se pudo guardar el lote. ${mensaje.ifBlank { "Intente de nuevo." }}"
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
            // MODIFIED: Replaced explicit repository types with generic Repositorio interface
            loteRepositorio: Repositorio<Lote> = ProveedorDependenciasModuloPrincipal.loteRepositorio,
            productoRepositorio: Repositorio<Producto> = ProveedorDependenciasModuloPrincipal.productoRepositorio,
            cambiosDatos: Flow<Unit> = ProveedorDependenciasModuloPrincipal.cambiosLotes
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoteViewModel(
                    motorSemaforo = motorSemaforo,
                    loteRepositorio = loteRepositorio,
                    productoRepositorio = productoRepositorio,
                    cambiosDatos = cambiosDatos
                )
            }
        }

        /** Tiempo máximo esperando la confirmación del servidor antes de asumir modo sin conexión. */
        private const val TIEMPO_ESPERA_GUARDADO_MS = 8_000L
    }
}