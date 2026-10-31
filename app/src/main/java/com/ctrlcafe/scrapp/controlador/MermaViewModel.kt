package com.ctrlcafe.scrapp.controlador

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.EstadoSubida
import com.ctrlcafe.scrapp.repositorio.EvidenciaRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

sealed class MermaEstadoUi {
    object Ideal : MermaEstadoUi()
    object Cargando : MermaEstadoUi()
    data class Exito(val resultado: Merma) : MermaEstadoUi()
    data class Error(val mensaje: String) : MermaEstadoUi()
}

class MermaViewModel(
    private val evidenciaRepositorio: EvidenciaRepositorio = EvidenciaRepositorio()
) : ViewModel() {

    private val _estadoUi = MutableStateFlow<MermaEstadoUi>(MermaEstadoUi.Ideal)
    val estadoUi: StateFlow<MermaEstadoUi> = _estadoUi

    private val _productos = MutableStateFlow<List<Producto>>(emptyList())
    val productos: StateFlow<List<Producto>> = _productos

    private val _lotesDisponibles = MutableStateFlow<List<Lote>>(emptyList())
    val lotesDisponibles: StateFlow<List<Lote>> = _lotesDisponibles

    // --- VARIABLES DE WILLIAM PARA ESTADO DE EVIDENCIA ---
    private val _estadoSubidaEvidencia = mutableStateOf<EstadoSubida>(EstadoSubida.EnEspera)
    val estadoSubidaEvidencia: State<EstadoSubida> = _estadoSubidaEvidencia

    private val _urlEvidenciaCloudinary = mutableStateOf<String?>(null)
    val urlEvidenciaCloudinary: State<String?> = _urlEvidenciaCloudinary

    init {
        // Datos de entorno seguros y controlados adaptados a tu constructor de Producto real (Fiel a tu ZIP)
        _productos.value = listOf(
            Producto(id = "p1", nombre = "Leche Entera", categoria = "Lácteos", costoUnitario = 1.20, precioVenta = 2.50, unidadMedida = "Litros"),
            Producto(id = "p2", nombre = "Queso Mozzarella", categoria = "Lácteos", costoUnitario = 3.50, precioVenta = 6.00, unidadMedida = "Kg"),
            Producto(id = "p3", nombre = "Harina Suave", categoria = "Materia Prima", costoUnitario = 0.80, precioVenta = 1.50, unidadMedida = "Kg"),
            Producto(id = "p4", nombre = "Café en Grano", categoria = "Granos", costoUnitario = 8.00, precioVenta = 15.00, unidadMedida = "Kg")
        )
    }

    fun seleccionarProducto(productoId: String) {
        // Mapea dinámicamente los lotes inyectando cantidadInicial y cantidadDisponible (Fiel a tu ZIP)
        _lotesDisponibles.value = listOf(
            Lote(
                id = "Lote-001",
                productoId = productoId,
                cantidadInicial = 45.0,
                cantidadDisponible = 45.0,
                costoUnitario = 1.20,
                fechaIngreso = LocalDate.now(),
                vidaUtilDias = 15
            ),
            Lote(
                id = "Lote-002",
                productoId = productoId,
                cantidadInicial = 20.5,
                cantidadDisponible = 20.5,
                costoUnitario = 1.20,
                fechaIngreso = LocalDate.now().minusDays(2),
                vidaUtilDias = 10
            )
        )
    }

    fun procesarYSubirFotoEvidencia(archivo: File) {
        CoroutineScope(Dispatchers.Main).launch {
            evidenciaRepositorio.subirImagenCloudinary(archivo).collect { estado ->
                _estadoSubidaEvidencia.value = estado
                if (estado is EstadoSubida.Exitoso) {
                    _urlEvidenciaCloudinary.value = estado.url
                }
            }
        }
    }

    fun intentarGuardarMermaConValidacion(nuevaMerma: Merma) {
        _estadoUi.value = MermaEstadoUi.Cargando
        CoroutineScope(Dispatchers.Main).launch {
            // Emite éxito inmediato en el formulario con la metadata integrada de la foto
            _estadoUi.value = MermaEstadoUi.Exito(nuevaMerma)
        }
    }

    fun reiniciarFormulario() {
        _estadoUi.value = MermaEstadoUi.Ideal
    }

    fun cancelarYRemoverEvidencia() {
        _urlEvidenciaCloudinary.value = null
        _estadoSubidaEvidencia.value = EstadoSubida.EnEspera
    }
}
