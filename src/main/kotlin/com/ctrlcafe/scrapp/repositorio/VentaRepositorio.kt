package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.modelo.RegistroVenta

class VentaRepositorio : Repositorio<RegistroVenta> {
    private val almacenamiento = LinkedHashMap<String, RegistroVenta>()

    override fun crear(entidad: RegistroVenta): RegistroVenta {
        almacenamiento[entidad.id] = entidad
        return entidad
    }

    override fun listar(): List<RegistroVenta> = almacenamiento.values.toList()

    override fun buscarPorId(id: String): RegistroVenta? = almacenamiento[id]

    fun listarPorProducto(productoId: String): List<RegistroVenta> =
        almacenamiento.values.filter { it.productoId == productoId }

    override fun actualizar(id: String, entidad: RegistroVenta): Boolean {
        if (!almacenamiento.containsKey(id)) return false
        almacenamiento[id] = entidad
        return true
    }

    override fun eliminar(id: String): Boolean = almacenamiento.remove(id) != null
}