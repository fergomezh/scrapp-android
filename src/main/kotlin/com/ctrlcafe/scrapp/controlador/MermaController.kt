package com.ctrlcafe.scrapp.controlador

import com.ctrlcafe.scrapp.modelo.Accion
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.util.CantidadInvalidaException
import java.time.LocalDate

/**
 * Controlador encargado del registro y gestión del historial de mermas,
 * aplicando el congelamiento del costo unitario y restricciones de permisos por rol.
 */
class MermaController(
    private val mermaRepositorio: MermaRepositorio,
    private val loteRepositorio: LoteRepositorio,
    private val productoRepositorio: ProductoRepositorio,
    private val authController: AuthController
) {

    /**
     * Registra una nueva merma en el sistema.
     * REGLA CLAVE: Congela el costo unitario del producto en el momento del registro.
     * Descuenta automáticamente la cantidad del lote afectado.
     */
    fun registrarMerma(
        id: String,
        loteId: String,
        cantidad: Double,
        causa: CausaMerma,
        rutaEvidencia: String,
        fecha: LocalDate = LocalDate.now()
    ): Merma {
        // Validamos permisos para registrar mermas (lo pueden hacer operativos y administradores)
        authController.verificarPermiso(Accion.REGISTRAR_MERMA)

        if (cantidad <= 0) {
            throw CantidadInvalidaException("La cantidad de merma debe ser mayor a cero.")
        }

        // Buscamos y validamos el lote
        val lote = loteRepositorio.buscarPorId(loteId)
            ?: throw IllegalArgumentException("El lote con ID '$loteId' no existe.")

        if (lote.cantidadDisponible < cantidad) {
            throw CantidadInvalidaException("No hay suficiente stock disponible en el lote para registrar esta merma. Disponible: ${lote.cantidadDisponible}")
        }

        // Buscamos el producto asociado para extraer su costo unitario actual
        val producto = productoRepositorio.buscarPorId(lote.productoId)
            ?: throw IllegalArgumentException("El producto asociado al lote no fue encontrado.")

        // Obtenemos el usuario autenticado actual para dejar rastro de quién registró
        val usuarioActual = AuthController.usuarioActual
            ?: throw IllegalStateException("No hay una sesión activa para registrar la merma.")

        // CONGELAMIENTO DEL COSTO HISTÓRICO:
        // Guardamos el costo unitario del producto tal como está en este preciso instante.
        val costoCongelado = producto.costoUnitario

        val nuevaMerma = Merma(
            id = id,
            loteId = loteId,
            productoId = producto.id,
            cantidad = cantidad,
            costoUnitarioCongelado = costoCongelado,
            fecha = fecha,
            causa = causa,
            rutaEvidencia = rutaEvidencia,
            usuarioId = usuarioActual.id
        )

        // Descontamos las existencias del lote afectado de manera síncrona
        lote.cantidadDisponible -= cantidad
        loteRepositorio.actualizar(lote.id, lote)

        // Guardamos la merma en el repositorio
        mermaRepositorio.crear(nuevaMerma)
        return nuevaMerma
    }

    /**
     * Lista todas las mermas registradas en el sistema.
     */
    fun listarMermas(): List<Merma> {
        if (!authController.estaAutenticado()) {
            authController.verificarPermiso(Accion.CONSULTAR_STOCK)
        }
        return mermaRepositorio.listar()
    }

    /**
     * Busca una merma por su ID.
     */
    fun buscarMermaPorId(id: String): Merma {
        return mermaRepositorio.buscarPorId(id)
            ?: throw IllegalArgumentException("No se encontró la merma con ID: $id")
    }

    /**
     * Actualiza una merma existente.
     * REGLA DE NEGOCIO: La edición de mermas está estrictamente restringida a Administradores.
     * Si cambia la cantidad, el lote se ajusta por la diferencia para que su stock siga cuadrando.
     */
    fun actualizarMerma(
        id: String,
        nuevaCantidad: Double,
        nuevaCausa: CausaMerma,
        nuevaRutaEvidencia: String,
        nuevaFecha: LocalDate
    ): Merma {
        // Exigimos permisos administrativos explícitos para modificar mermas
        authController.verificarPermiso(Accion.ADMINISTRAR_PRODUCTOS) // O una acción administrativa general

        val mermaExistente = buscarMermaPorId(id)

        if (nuevaCantidad <= 0) {
            throw CantidadInvalidaException("La cantidad actualizada debe ser mayor a cero.")
        }

        // Diferencia positiva: se desperdició más y se descuenta del lote; negativa: se devuelve al lote.
        val lote = buscarLoteDe(mermaExistente)
        val diferencia = nuevaCantidad - mermaExistente.cantidad
        if (diferencia > lote.cantidadDisponible) {
            throw CantidadInvalidaException("No hay suficiente stock en el lote ${lote.id} para aumentar la merma. Disponible: ${lote.cantidadDisponible}")
        }

        // Mantenemos el costo unitario congelado original de la merma para no corromper el histórico
        val mermaActualizada = Merma(
            id = mermaExistente.id,
            loteId = mermaExistente.loteId,
            productoId = mermaExistente.productoId,
            cantidad = nuevaCantidad,
            costoUnitarioCongelado = mermaExistente.costoUnitarioCongelado,
            fecha = nuevaFecha,
            causa = nuevaCausa,
            rutaEvidencia = nuevaRutaEvidencia,
            usuarioId = mermaExistente.usuarioId
        )

        lote.cantidadDisponible -= diferencia
        loteRepositorio.actualizar(lote.id, lote)
        mermaRepositorio.actualizar(id, mermaActualizada)
        return mermaActualizada
    }

    /**
     * Elimina un registro de merma.
     * REGLA DE NEGOCIO: La eliminación de mermas es exclusiva de Administradores.
     * Las unidades de la merma eliminada vuelven al lote del que se descontaron.
     */
    fun eliminarMerma(id: String) {
        authController.verificarPermiso(Accion.ADMINISTRAR_PRODUCTOS)
        val merma = buscarMermaPorId(id)
        val lote = buscarLoteDe(merma)

        lote.cantidadDisponible += merma.cantidad
        loteRepositorio.actualizar(lote.id, lote)
        mermaRepositorio.eliminar(id)
    }

    private fun buscarLoteDe(merma: Merma): Lote =
        loteRepositorio.buscarPorId(merma.loteId)
            ?: throw IllegalStateException("El lote '${merma.loteId}' de la merma ${merma.id} ya no existe; no se puede ajustar su stock.")
}