package com.ctrlcafe.scrapp.controlador

import com.ctrlcafe.scrapp.modelo.Accion
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.util.ProductoNoEncontradoException

/**
 * Controlador encargado de la gestión (CRUD) de productos y sus reglas de negocio asociadas,
 * adaptado estrictamente a la interfaz de repositorios del sistema.
 */
class ProductoController(
    private val productoRepositorio: ProductoRepositorio,
    private val authController: AuthController
) {

    /**
     * Da de alta un nuevo producto en el sistema.
     * Requiere permisos de administración de productos.
     */
    fun crearProducto(
        id: String,
        nombre: String,
        categoria: String,
        costoUnitario: Double,
        precioVenta: Double,
        unidadMedida: String
    ): Producto {
        authController.verificarPermiso(Accion.ADMINISTRAR_PRODUCTOS)

        val nuevoProducto = Producto(
            id = id,
            nombre = nombre,
            categoria = categoria,
            costoUnitario = costoUnitario,
            precioVenta = precioVenta,
            unidadMedida = unidadMedida
        )

        productoRepositorio.crear(nuevoProducto)
        return nuevoProducto
    }

    /**
     * Retorna la lista de todos los productos registrados.
     */
    fun listarProductos(): List<Producto> {
        if (!authController.estaAutenticado()) {
            authController.verificarPermiso(Accion.CONSULTAR_STOCK)
        }
        return productoRepositorio.listar()
    }

    /**
     * Busca un producto por su ID único. Lanza excepción si no existe.
     */
    fun buscarProductoPorId(id: String): Producto {
        return productoRepositorio.buscarPorId(id)
            ?: throw ProductoNoEncontradoException(id)
    }

    /**
     * Actualiza los datos de un producto existente.
     * REGLA DE NEGOCIO: Modificar datos de productos requiere permisos de administración.
     */
    fun actualizarProducto(
        id: String,
        nuevoNombre: String,
        nuevaCategoria: String,
        nuevoCostoUnitario: Double,
        nuevoPrecioVenta: Double,
        nuevaUnidadMedida: String
    ): Producto {
        authController.verificarPermiso(Accion.ADMINISTRAR_PRODUCTOS)

        // Validamos que el producto exista antes de actualizar
        buscarProductoPorId(id)

        val productoActualizado = Producto(
            id = id,
            nombre = nuevoNombre,
            categoria = nuevaCategoria,
            costoUnitario = nuevoCostoUnitario,
            precioVenta = nuevoPrecioVenta,
            unidadMedida = nuevaUnidadMedida
        )

        // ProductoRepositorio.actualizar espera (id, entidad) según la interfaz
        productoRepositorio.actualizar(id, productoActualizado)
        return productoActualizado
    }

    /**
     * Elimina un producto del repositorio.
     */
    fun eliminarProducto(id: String) {
        authController.verificarPermiso(Accion.ADMINISTRAR_PRODUCTOS)
        buscarProductoPorId(id)
        productoRepositorio.eliminar(id)
    }
}