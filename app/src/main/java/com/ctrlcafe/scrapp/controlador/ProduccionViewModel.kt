package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val configuracionMotores: ConfiguracionMotores = ProveedorDependenciasModuloPrincipal.configuracionMotores
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProduccionUiState(cargando = true))
    val uiState: StateFlow<ProduccionUiState> = _uiState.asStateFlow()

    init {
        cargarProyecciones()
    }

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

internal object ProveedorDependenciasModuloPrincipal {
    val usuarioRepositorio: UsuarioRepositorio = ContenedorDatos.usuarioRepositorio
    val productoRepositorio: Repositorio<Producto> = ContenedorDatos.productoRepositorio
    val loteRepositorio: Repositorio<Lote> = ContenedorDatos.loteRepositorio
    val ventaRepositorio: Repositorio<RegistroVenta> = ContenedorDatos.ventaRepositorio
    val mermaRepositorio: Repositorio<Merma> = ContenedorDatos.mermaRepositorio
    val configuracionMotores: ConfiguracionMotores = ContenedorDatos.configuracion
    val motorProyeccion: MotorProyeccion = ContenedorDatos.proyeccion
    val motorSemaforo: MotorSemaforo = ContenedorDatos.semaforo
}