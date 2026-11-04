package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.AuthRepositorio
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Filtros del historial de mermas. `null` en un campo significa "sin filtrar". */
data class FiltrosHistorial(
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val productoId: String? = null,
    val causa: CausaMerma? = null
) {
    val activos: Boolean get() = desde != null || hasta != null || productoId != null || causa != null
}

/** Una fila del historial de mermas, con los datos ya resueltos para la vista. */
data class FilaHistorialMerma(
    val merma: Merma,
    val nombreProducto: String,
    val unidadMedida: String,
    val perdida: Double,
    val registradoPor: String
) {
    /** El placeholder del Avance 1 ("sin_evidencia.jpg", "EVID-...") no es una foto real. */
    val urlEvidencia: String?
        get() = merma.rutaEvidencia.takeIf { it.startsWith("http") || it.startsWith("content:") || it.startsWith("file:") }
}

enum class MotivoCierreLote(val etiqueta: String) {
    AGOTADO("Agotado"),
    VENCIDO("Vencido")
}

/** Lote que ya salió del monitor: se agotó o venció. */
data class FilaHistorialLote(
    val lote: Lote,
    val nombreProducto: String,
    val unidadMedida: String,
    val motivo: MotivoCierreLote,
    /** Fecha en que salió del monitor: caducidad si venció, última merma si se agotó. */
    val fechaCierre: LocalDate,
    val unidadesMermadas: Double,
    val perdidaRegistrada: Double,
    /** Existencias de un lote vencido que todavía no se registraron como merma. */
    val cantidadPendiente: Double,
    val perdidaPendiente: Double
) {
    val perdidaTotal: Double get() = perdidaRegistrada + perdidaPendiente
}

enum class FiltroHistorialLote(val etiqueta: String, val motivo: MotivoCierreLote?) {
    TODOS("Todos", null),
    AGOTADOS("Agotados", MotivoCierreLote.AGOTADO),
    VENCIDOS("Vencidos", MotivoCierreLote.VENCIDO)
}

data class HistorialUiState(
    val esAdministrador: Boolean = false,
    val mermas: List<FilaHistorialMerma> = emptyList(),
    val lotes: List<FilaHistorialLote> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val filtros: FiltrosHistorial = FiltrosHistorial(),
    val filtroLotes: FiltroHistorialLote = FiltroHistorialLote.TODOS,
    val mermaSeleccionada: FilaHistorialMerma? = null,
    val cargando: Boolean = true,
    val mensajeError: String? = null
) {
    val mermasFiltradas: List<FilaHistorialMerma>
        get() = mermas.filter { fila ->
            val m = fila.merma
            (filtros.desde == null || !m.fecha.isBefore(filtros.desde)) &&
                (filtros.hasta == null || !m.fecha.isAfter(filtros.hasta)) &&
                (filtros.productoId == null || m.productoId == filtros.productoId) &&
                (filtros.causa == null || m.causa == filtros.causa)
        }

    val perdidaFiltrada: Double get() = mermasFiltradas.sumOf { it.perdida }

    val lotesFiltrados: List<FilaHistorialLote>
        get() = if (filtroLotes.motivo == null) lotes else lotes.filter { it.motivo == filtroLotes.motivo }
}

/**
 * Controlador del Historial de mermas y de lotes (Integrante 3, requerimiento 4).
 *
 * Todo llega en tiempo real desde Firestore: una merma registrada en otro dispositivo
 * aparece sin recargar. El operativo ve solo las mermas que registró él (las reglas no
 * le dejan leer las ajenas); el administrador ve todas.
 */
class HistorialViewModel(
    private val usuario: PerfilUsuario?,
    private val mermasFlow: Flow<List<Merma>>,
    private val productosFlow: Flow<List<Producto>>,
    private val lotesFlow: Flow<List<Lote>>,
    private val nombresUsuariosFlow: Flow<Map<String, String>>,
    private val motorSemaforo: MotorSemaforo
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialUiState(esAdministrador = usuario?.esAdministrador == true))
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    init {
        observarDatos()
    }

    private fun observarDatos() {
        viewModelScope.launch {
            combine(mermasFlow, productosFlow, lotesFlow, nombresUsuariosFlow) { mermas, productos, lotes, nombres ->
                val porId = productos.associateBy { it.id }
                val filasMermas = construirFilasMermas(mermas, porId, nombres)
                val filasLotes = construirFilasLotes(lotes, mermas, porId)
                Triple(productos, filasMermas, filasLotes)
            }
                .catch { e ->
                    Logger.registrar(
                        NivelLog.ERROR,
                        HistorialViewModel::class.java,
                        "Error al escuchar el historial: ${e.message}"
                    )
                    _uiState.update {
                        it.copy(cargando = false, mensajeError = "No se pudo cargar el historial. ${e.message.orEmpty()}")
                    }
                }
                .collect { (productos, filasMermas, filasLotes) ->
                    _uiState.update { estado ->
                        estado.copy(
                            productos = productos.sortedBy { it.nombre },
                            mermas = filasMermas,
                            lotes = filasLotes,
                            // Si la merma abierta cambió (u otro dispositivo la actualizó), se refresca el detalle
                            mermaSeleccionada = estado.mermaSeleccionada?.let { sel ->
                                filasMermas.find { it.merma.id == sel.merma.id }
                            },
                            cargando = false,
                            mensajeError = null
                        )
                    }
                }
        }
    }

    private fun construirFilasMermas(
        mermas: List<Merma>,
        productos: Map<String, Producto>,
        nombres: Map<String, String>
    ): List<FilaHistorialMerma> =
        mermas
            .sortedWith(compareByDescending<Merma> { it.fecha }.thenByDescending { it.creadoEn })
            .map { merma ->
                val producto = productos[merma.productoId]
                FilaHistorialMerma(
                    merma = merma,
                    nombreProducto = producto?.nombre ?: merma.productoId,
                    unidadMedida = producto?.unidadMedida.orEmpty(),
                    perdida = merma.cantidad * merma.costoUnitarioCongelado,
                    // nombreUsuario va desnormalizado en la merma; si falta se busca por uid
                    registradoPor = merma.nombreUsuario.ifBlank {
                        nombres[merma.usuarioId]?.takeIf { it.isNotBlank() } ?: merma.usuarioId
                    }
                )
            }

    /**
     * Lotes agotados y vencidos con lo que se perdió de cada uno. La pérdida registrada sale
     * de las mermas visibles para el usuario: para el operativo solo cuenta las suyas.
     */
    private fun construirFilasLotes(
        lotes: List<Lote>,
        mermas: List<Merma>,
        productos: Map<String, Producto>
    ): List<FilaHistorialLote> {
        val mermasPorLote = mermas.groupBy { it.loteId }
        return lotes.mapNotNull { lote ->
            val agotado = lote.cantidadDisponible <= 0.0
            val vencido = motorSemaforo.bloqueadoParaUso(lote)
            if (!agotado && !vencido) return@mapNotNull null

            val delLote = mermasPorLote[lote.id].orEmpty()
            val producto = productos[lote.productoId]
            val pendiente = if (agotado) 0.0 else lote.cantidadDisponible
            FilaHistorialLote(
                lote = lote,
                nombreProducto = producto?.nombre ?: lote.productoId,
                unidadMedida = producto?.unidadMedida.orEmpty(),
                motivo = if (agotado) MotivoCierreLote.AGOTADO else MotivoCierreLote.VENCIDO,
                fechaCierre = if (agotado) {
                    delLote.maxOfOrNull { it.fecha } ?: lote.fechaCaducidad
                } else {
                    lote.fechaCaducidad
                },
                unidadesMermadas = delLote.sumOf { it.cantidad },
                perdidaRegistrada = delLote.sumOf { it.cantidad * it.costoUnitarioCongelado },
                cantidadPendiente = pendiente,
                perdidaPendiente = pendiente * lote.costoUnitario
            )
        }.sortedByDescending { it.fechaCierre }
    }

    // ------------------------------------------------------------------ Filtros

    fun seleccionarRangoFechas(desde: LocalDate?, hasta: LocalDate?) {
        _uiState.update { it.copy(filtros = it.filtros.copy(desde = desde, hasta = hasta)) }
    }

    fun seleccionarProducto(productoId: String?) {
        _uiState.update { it.copy(filtros = it.filtros.copy(productoId = productoId)) }
    }

    fun seleccionarCausa(causa: CausaMerma?) {
        _uiState.update { it.copy(filtros = it.filtros.copy(causa = causa)) }
    }

    fun limpiarFiltros() {
        _uiState.update { it.copy(filtros = FiltrosHistorial()) }
    }

    fun seleccionarFiltroLotes(filtro: FiltroHistorialLote) {
        _uiState.update { it.copy(filtroLotes = filtro) }
    }

    // ------------------------------------------------------------------ Detalle

    fun abrirDetalle(fila: FilaHistorialMerma) {
        _uiState.update { it.copy(mermaSeleccionada = fila) }
    }

    fun cerrarDetalle() {
        _uiState.update { it.copy(mermaSeleccionada = null) }
    }

    companion object {
        /**
         * El ViewModel depende del usuario en sesión: el administrador escucha toda la
         * colección de mermas y el operativo solo las suyas (`usuarioId == uid`).
         */
        fun provideFactory(usuario: PerfilUsuario?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val esAdmin = usuario?.esAdministrador == true
                val mermas = when {
                    usuario == null -> flowOf(emptyList())
                    esAdmin -> ContenedorDatos.mermaRepositorio.observarMermas(usuarioId = null)
                    else -> ContenedorDatos.mermaRepositorio.observarMermas(usuarioId = usuario.uid)
                }
                val nombres = when {
                    usuario == null -> flowOf(emptyMap())
                    esAdmin -> observarNombresUsuarios()
                    // El operativo solo ve sus mermas, así que basta con su propio nombre
                    else -> flowOf(mapOf(usuario.uid to usuario.nombre))
                }
                HistorialViewModel(
                    usuario = usuario,
                    mermasFlow = mermas,
                    productosFlow = ProveedorDependenciasModuloPrincipal.productosFlow,
                    lotesFlow = ProveedorDependenciasModuloPrincipal.lotesFlow,
                    nombresUsuariosFlow = nombres,
                    motorSemaforo = ProveedorDependenciasModuloPrincipal.motorSemaforo
                )
            }
        }

        /** uid -> nombre de todos los usuarios; solo el administrador puede leer `usuarios`. */
        private fun observarNombresUsuarios(): Flow<Map<String, String>> = callbackFlow {
            val listener = FirebaseFirestore.getInstance()
                .collection(AuthRepositorio.COLECCION_USUARIOS)
                .addSnapshotListener { snapshot, error ->
                    // Sin nombres el historial sigue funcionando: se muestra el uid
                    if (error != null || snapshot == null) {
                        trySend(emptyMap())
                        return@addSnapshotListener
                    }
                    trySend(snapshot.documents.associate { it.id to it.getString("nombre").orEmpty() })
                }
            awaitClose { listener.remove() }
        }
    }
}
