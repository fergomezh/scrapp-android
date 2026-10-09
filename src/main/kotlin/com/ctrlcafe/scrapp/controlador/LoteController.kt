package com.ctrlcafe.scrapp.controlador

import com.ctrlcafe.scrapp.modelo.Accion
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.util.CantidadInvalidaException
import com.ctrlcafe.scrapp.util.LoteVencidoException
import java.time.LocalDate

/**
 * Controlador encargado de la gestión de lotes: registro de ingresos,
 * cálculo de caducidad y descuento de existencias en el inventario.
 */
class LoteController(
    private val loteRepositorio: LoteRepositorio,
    private val productoRepositorio: ProductoRepositorio,
    private val authController: AuthController
) {

    /**
     * Registra el ingreso de un nuevo lote especificando fecha, cantidad, costo y vida útil.
     * Requiere permisos de administración de lotes.
     */
    fun registrarLote(
        id: String,
        productoId: String,
        cantidadInicial: Double,
        costoUnitario: Double,
        fechaIngreso: LocalDate,
        vidaUtilDias: Int,
        fechaCaducidadManual: LocalDate? = null
    ): Lote {
        // Validamos permisos de administrador/gestión de lotes
        authController.verificarPermiso(Accion.ADMINISTRAR_LOTES)

        // Validamos que el producto asociado realmente exista en el sistema
        productoRepositorio.buscarPorId(productoId)
            ?: throw IllegalArgumentException("No se puede registrar el lote: El producto con ID '$productoId' no existe.")

        if (cantidadInicial <= 0) {
            throw CantidadInvalidaException("La cantidad inicial del lote debe ser mayor a cero.")
        }

        // Creamos el lote. Si se proporciona una fecha manual se usa, de lo contrario
        // el data class calcula automáticamente la fecha de caducidad por defecto.
        val nuevoLote = if (fechaCaducidadManual != null) {
            Lote(
                id = id,
                productoId = productoId,
                cantidadInicial = cantidadInicial,
                cantidadDisponible = cantidadInicial,
                costoUnitario = costoUnitario,
                fechaIngreso = fechaIngreso,
                vidaUtilDias = vidaUtilDias,
                fechaCaducidad = fechaCaducidadManual
            )
        } else {
            Lote(
                id = id,
                productoId = productoId,
                cantidadInicial = cantidadInicial,
                cantidadDisponible = cantidadInicial,
                costoUnitario = costoUnitario,
                fechaIngreso = fechaIngreso,
                vidaUtilDias = vidaUtilDias
            )
        }

        loteRepositorio.crear(nuevoLote)
        return nuevoLote
    }

    /**
     * Retorna la lista de todos los lotes registrados en el sistema.
     */
    fun listarLotes(): List<Lote> {
        if (!authController.estaAutenticado()) {
            authController.verificarPermiso(Accion.CONSULTAR_STOCK)
        }
        return loteRepositorio.listar()
    }

    /**
     * Busca un lote por su identificador único.
     */
    fun buscarLotePorId(id: String): Lote {
        return loteRepositorio.buscarPorId(id)
            ?: throw IllegalArgumentException("No se encontró el lote con ID: $id")
    }

    /**
     * Lista todos los lotes asociados a un producto específico usando el método del repositorio.
     */
    fun listarLotesPorProducto(productoId: String): List<Lote> {
        if (!authController.estaAutenticado()) {
            authController.verificarPermiso(Accion.CONSULTAR_STOCK)
        }
        return loteRepositorio.listarPorProducto(productoId)
    }

    /**
     * Descuenta existencias de un lote específico.
     * Valida que el lote no esté vencido y que haya suficiente stock disponible.
     */
    fun descontarExistencias(idLote: String, cantidadADescontar: Double): Lote {
        authController.verificarPermiso(Accion.CONSULTAR_STOCK)

        val lote = buscarLotePorId(idLote)

        // Validación de caducidad
        if (lote.fechaCaducidad.isBefore(LocalDate.now())) {
            throw LoteVencidoException(lote.id)
        }

        if (cantidadADescontar <= 0) {
            throw CantidadInvalidaException("La cantidad a descontar debe ser mayor a cero.")
        }

        if (lote.cantidadDisponible < cantidadADescontar) {
            throw CantidadInvalidaException("Stock insuficiente en el lote ${lote.id}. Disponible: ${lote.cantidadDisponible}, solicitado: $cantidadADescontar")
        }

        // Actualizamos la cantidad disponible
        lote.cantidadDisponible -= cantidadADescontar
        loteRepositorio.actualizar(lote.id, lote)
        return lote
    }
}