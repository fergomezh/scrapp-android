package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.modelo.RegistroVenta
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.ctrlcafe.scrapp.repositorio.UsuarioRepositorio
import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.MotorProyeccion
import com.ctrlcafe.scrapp.servicio.MotorSemaforo
import com.ctrlcafe.scrapp.servicio.ProyeccionProducto
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProduccionUiState(
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
    val proyeccionesVisibles: List<ProyeccionProducto>
        get() = if (ocultarSinHistorial) proyeccionesConHistorial else proyecciones
}

class ProduccionViewModel(
    private val motorProyeccion: MotorProyeccion = ProveedorDependenciasModuloPrincipal.motorProyeccion,
    // CORREÇÃO: Repositorio<Producto> no lugar de ProductoRepositorio
    private val productoRepositorio: Repositorio<Producto> = ProveedorDependenciasModuloPrincipal.productoRepositorio,
    private val configuracionMotores: ConfiguracionMotores = ProveedorDependenciasModuloPrincipal.configuracionMotores,
    // Emite cada vez que cambian ventas, mermas, lotes o productos en Firestore (de este u otro dispositivo)
    private val cambiosDatos: Flow<Unit> = ProveedorDependenciasModuloPrincipal.cambiosProduccion
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProduccionUiState(cargando = true))
    val uiState: StateFlow<ProduccionUiState> = _uiState.asStateFlow()

    init {
        // Tiempo real: la proyección se recalcula sola cuando llega un cambio de los listeners
        viewModelScope.launch {
            cambiosDatos.collect { cargarProyecciones() }
        }
    }

    fun cargarProyecciones(fecha: LocalDate = _uiState.value.fechaObjetivo) {
        // El indicador de carga solo aparece la primera vez; las actualizaciones en vivo no parpadean
        _uiState.update {
            it.copy(cargando = it.proyecciones.isEmpty(), mensajeError = null, fechaObjetivo = fecha)
        }

        try {
            val mapaProductos = productoRepositorio.listar().associateBy { it.id }
            // Los productos desactivados desde el catálogo ya no se proponen para producir
            val todas = motorProyeccion.proyectarTodos(fechaObjetivo = fecha)
                .filter { mapaProductos[it.productoId]?.activo != false }
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

    fun alternarFiltroSinHistorial() {
        _uiState.update { it.copy(ocultarSinHistorial = !it.ocultarSinHistorial) }
    }

    companion object {
        fun provideFactory(
            motorProyeccion: MotorProyeccion = ProveedorDependenciasModuloPrincipal.motorProyeccion,
            // CORREÇÃO: Repositorio<Producto>
            productoRepositorio: Repositorio<Producto> = ProveedorDependenciasModuloPrincipal.productoRepositorio,
            configuracionMotores: ConfiguracionMotores = ProveedorDependenciasModuloPrincipal.configuracionMotores,
            cambiosDatos: Flow<Unit> = ProveedorDependenciasModuloPrincipal.cambiosProduccion
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProduccionViewModel(
                    motorProyeccion = motorProyeccion,
                    productoRepositorio = productoRepositorio,
                    configuracionMotores = configuracionMotores,
                    cambiosDatos = cambiosDatos
                )
            }
        }
    }
}

internal object ProveedorDependenciasModuloPrincipal {
    val usuarioRepositorio: UsuarioRepositorio = ContenedorDatos.usuarioRepositorio
    val productoRepositorio: Repositorio<Producto> = ContenedorDatos.productoRepositorio
    val loteRepositorio: Repositorio<Lote> = ContenedorDatos.loteRepositorio
    val ventaRepositorio: Repositorio<RegistroVenta> = ContenedorDatos.ventaRepositorio
    val mermaRepositorio: Repositorio<Merma> = ContenedorDatos.mermaRepositorio
    val configuracionMotores: ConfiguracionMotores = ContenedorDatos.configuracion
    val motorProyeccion: MotorProyeccion = ContenedorDatos.proyeccion
    val motorSemaforo: MotorSemaforo = ContenedorDatos.semaforo

    // StateFlow de los listeners de Firestore (Integrante 1), para la sincronización en tiempo real
    val productosFlow: Flow<List<Producto>> = ContenedorDatos.productoRepositorio.productosFlow
    val lotesFlow: Flow<List<Lote>> = ContenedorDatos.loteRepositorio.lotesFlow

    /** Cambios que afectan al monitor de lotes: lotes (existencias, nuevos) y nombres de producto. */
    // configuracion/motores: si el admin cambia margen o umbrales, todo se recalcula en vivo
    private val configuracionFlow: Flow<Map<String, Any>> = ContenedorDatos.configuracionRepositorio.configFlow

    val cambiosLotes: Flow<Unit> = combine(lotesFlow, productosFlow, configuracionFlow) { _, _, _ -> }

    /** Cambios que afectan a la proyección de producción. */
    val cambiosProduccion: Flow<Unit> = combine(
        ContenedorDatos.ventaRepositorio.ventasFlow,
        ContenedorDatos.mermaRepositorio.mermasFlow,
        lotesFlow,
        productosFlow,
        configuracionFlow
    ) { _, _, _, _, _ -> }
}