package com.ctrlcafe.scrapp.controlador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.servicio.OrquestadorMerma
import com.ctrlcafe.scrapp.servicio.ResultadoRecalculo
import com.ctrlcafe.scrapp.util.CantidadInvalidaException
import com.ctrlcafe.scrapp.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

sealed interface MermaEstadoUi {
    object Formulario : MermaEstadoUi
    object Guardando : MermaEstadoUi
    data class Exito(val resultado: ResultadoRecalculo) : MermaEstadoUi
    data class Error(val mensaje: String) : MermaEstadoUi
}

class MermaViewModel(
    private val productoRepo: ProductoRepositorio = ContenedorDatos.productoRepositorio,
    private val loteRepo: LoteRepositorio = ContenedorDatos.loteRepositorio,
    private val mermaRepo: MermaRepositorio = ContenedorDatos.mermaRepositorio,
    private val orquestador: OrquestadorMerma = ContenedorDatos.orquestadorMerma
) : ViewModel() {

    private val _estadoUi = MutableStateFlow<MermaEstadoUi>(MermaEstadoUi.Formulario)
    val estadoUi: StateFlow<MermaEstadoUi> = _estadoUi.asStateFlow()

    private val _productos = MutableStateFlow<List<Producto>>(emptyList())
    val productos: StateFlow<List<Producto>> = _productos.asStateFlow()

    private val _lotesDisponibles = MutableStateFlow<List<Lote>>(emptyList())
    val lotesDisponibles: StateFlow<List<Lote>> = _lotesDisponibles.asStateFlow()

    init {
        cargarProductos()
    }

    fun cargarProductos() {
        _productos.value = productoRepo.listar()
    }

    fun seleccionarProducto(productoId: String) {
        _lotesDisponibles.value = loteRepo.listarPorProducto(productoId)
            .filter { it.cantidadDisponible > 0.0 }
    }

    fun registrarMerma(
        loteId: String,
        cantidad: Double,
        causa: CausaMerma,
        fecha: LocalDate,
        rutaEvidencia: String,
        usuarioId: String
    ) {
        viewModelScope.launch {
            _estadoUi.value = MermaEstadoUi.Guardando
            try {
                val lote = loteRepo.buscarPorId(loteId)
                    ?: throw CantidadInvalidaException("El lote seleccionado no existe.")

                if (cantidad <= 0.0) {
                    throw CantidadInvalidaException("La cantidad debe ser mayor a 0.")
                }
                if (cantidad > lote.cantidadDisponible) {
                    throw CantidadInvalidaException("La cantidad ($cantidad) supera las existencias del lote (${lote.cantidadDisponible}).")
                }

                // 1. Congelar costo y descontar existencias en el lote
                val costoCongelado = lote.costoUnitario
                lote.cantidadDisponible -= cantidad
                loteRepo.actualizar(lote.id, lote)

                // 2. Persistir entidad Merma
                val nuevaMerma = Merma(
                    id = "MER-${UUID.randomUUID().toString().take(6).uppercase()}",
                    loteId = lote.id,
                    productoId = lote.productoId,
                    cantidad = cantidad,
                    costoUnitarioCongelado = costoCongelado,
                    fecha = fecha,
                    causa = causa,
                    rutaEvidencia = rutaEvidencia.ifBlank { "sin_evidencia.jpg" },
                    usuarioId = usuarioId
                )
                mermaRepo.crear(nuevaMerma)

                // 3. Orquestador: recálculo dinámico de semáforo, pérdidas y proyecciones
                val resultadoRecalculo = orquestador.aplicarMerma(nuevaMerma)
                Logger.info(MermaViewModel::class.java, "Merma registrada con éxito: ${nuevaMerma.id}")

                _estadoUi.value = MermaEstadoUi.Exito(resultadoRecalculo)
            } catch (e: Exception) {
                Logger.error(MermaViewModel::class.java, "Error registrando merma: ${e.message}", e)
                _estadoUi.value = MermaEstadoUi.Error(e.message ?: "Ocurrió un error al registrar la merma.")
            }
        }
    }

    fun reiniciarFormulario() {
        _estadoUi.value = MermaEstadoUi.Formulario
    }
}