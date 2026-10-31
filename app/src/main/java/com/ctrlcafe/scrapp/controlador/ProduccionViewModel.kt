package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.repositorio.UsuarioRepositorio
import com.ctrlcafe.scrapp.repositorio.VentaRepositorio
import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.MotorProyeccion
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.servicio.ProyeccionProducto
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Estado inmutable de la pantalla Dashboard de Producción.
 */
data class ProduccionUiState(
    // Mañana, igual que MotorProyeccion y OrquestadorMerma: la tasa de merma usa la ventana
    // que termina el día anterior a la fecha objetivo, así que con "hoy" las mermas
    // registradas hoy no se reflejarían en el dashboard.
    val fechaObjetivo: LocalDate = LocalDate.now().plusDays(1),
    val proyecciones: List<ProyeccionProducto> = emptyList(),
    val proyeccionesConHistorial: List<ProyeccionProducto> = emptyList(),
    val proyeccionesSinHistorial: List<ProyeccionProducto> = emptyList(),
    val productosPorId: Map<String, Producto> = emptyMap(),
    val margenSeguridadActual: Double = ConfiguracionMotores.MARGEN_POR_DEFECTO,
    val ocultarSinHistorial: Boolean = false,
    val sinHistorialSuficiente: Boolean = false,
    val cargando: Boolean = false,
    val mensajeError: String? = null
) {
    /** Lista filtrada según la preferencia del usuario en pantalla. */
    val proyeccionesVisibles: List<ProyeccionProducto>
        get() = if (ocultarSinHistorial) proyeccionesConHistorial else proyecciones
}

/**
 * Controlador (ViewModel) para el Dashboard de Producción (Integrante 3).
 * Expone el estado mediante StateFlow para que DashboardScreen solo observe y dibuje.
 */
class ProduccionViewModel(
    private val motorProyeccion: MotorProyeccion = ProveedorDependenciasModuloPrincipal.motorProyeccion,
    private val productoRepositorio: ProductoRepositorio = ProveedorDependenciasModuloPrincipal.productoRepositorio,
    private val configuracionMotores: ConfiguracionMotores = ProveedorDependenciasModuloPrincipal.configuracionMotores
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProduccionUiState(cargando = true))
    val uiState: StateFlow<ProduccionUiState> = _uiState.asStateFlow()

    init {
        cargarProyecciones()
    }

    /**
     * Recalcula la proyección de todos los productos para la [fecha] indicada.
     * Se invoca al iniciar y al regresar a la pantalla para reflejar nuevas mermas.
     */
    fun cargarProyecciones(fecha: LocalDate = _uiState.value.fechaObjetivo) {
        _uiState.update { it.copy(cargando = true, mensajeError = null, fechaObjetivo = fecha) }

        try {
            val mapaProductos = productoRepositorio.listar().associateBy { it.id }
            val todas = motorProyeccion.proyectarTodos(fechaObjetivo = fecha)
            val conHistorial = todas.filter { it.demandaBase > 0.0 }
            val sinHistorial = todas.filter { it.demandaBase <= 0.0 }

            _uiState.update { estadoActual ->
                estadoActual.copy(
                    fechaObjetivo = fecha,
                    proyecciones = todas,
                    proyeccionesConHistorial = conHistorial,
                    proyeccionesSinHistorial = sinHistorial,
                    productosPorId = mapaProductos,
                    margenSeguridadActual = configuracionMotores.margenSeguridad,
                    // Si ningún producto tiene ventas equivalentes registradas, activamos EstadoVacio
                    sinHistorialSuficiente = conHistorial.isEmpty(),
                    cargando = false,
                    mensajeError = null
                )
            }

            Logger.info(
                ProduccionViewModel::class.java,
                "Proyecciones actualizadas para $fecha: ${conHistorial.size} con historial, ${sinHistorial.size} sin historial"
            )
        } catch (e: Exception) {
            Logger.registrar(
                NivelLog.ERROR,
                ProduccionViewModel::class.java,
                "Error al calcular proyecciones de producción: ${e.message}"
            )
            _uiState.update {
                it.copy(
                    cargando = false,
                    mensajeError = e.message ?: "No se pudieron calcular las proyecciones de producción."
                )
            }
        }
    }

    /**
     * Permite alternar en la vista si se muestran todos los productos del catálogo
     * o únicamente aquellos con historial de ventas suficiente.
     */
    fun alternarFiltroSinHistorial() {
        _uiState.update { it.copy(ocultarSinHistorial = !it.ocultarSinHistorial) }
    }

    companion object {
        /**
         * Factory para inyectar las instancias compartidas desde ScrappNavHost / MainActivity.
         * Si se llama a viewModel() sin parámetros, usa por defecto ProveedorDependenciasModuloPrincipal.
         */
        fun provideFactory(
            motorProyeccion: MotorProyeccion = ProveedorDependenciasModuloPrincipal.motorProyeccion,
            productoRepositorio: ProductoRepositorio = ProveedorDependenciasModuloPrincipal.productoRepositorio,
            configuracionMotores: ConfiguracionMotores = ProveedorDependenciasModuloPrincipal.configuracionMotores
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProduccionViewModel(
                    motorProyeccion = motorProyeccion,
                    productoRepositorio = productoRepositorio,
                    configuracionMotores = configuracionMotores
                )
            }
        }
    }
}

/**
 * Punto de acceso a las dependencias de los módulos de Producción y Lotes.
 *
 * No crea instancias propias: delega en [ContenedorDatos] para que [LoteViewModel],
 * [ProduccionViewModel] y [MermaViewModel] compartan los mismos repositorios y motores.
 * Así, una merma registrada por [OrquestadorMerma] se refleja de inmediato en el
 * monitor de lotes y en el dashboard de producción.
 */
internal object ProveedorDependenciasModuloPrincipal {
    val usuarioRepositorio: UsuarioRepositorio = ContenedorDatos.usuarioRepositorio
    val productoRepositorio: ProductoRepositorio = ContenedorDatos.productoRepositorio
    val loteRepositorio: LoteRepositorio = ContenedorDatos.loteRepositorio
    val ventaRepositorio: VentaRepositorio = ContenedorDatos.ventaRepositorio
    val mermaRepositorio: MermaRepositorio = ContenedorDatos.mermaRepositorio
    val configuracionMotores: ConfiguracionMotores = ContenedorDatos.configuracion
    val motorProyeccion: MotorProyeccion = ContenedorDatos.proyeccion
    val motorSemaforo: MotorSemaforo = ContenedorDatos.semaforo
}
