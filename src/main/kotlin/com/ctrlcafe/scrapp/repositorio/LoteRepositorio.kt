package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.modelo.Lote

class LoteRepositorio : Repositorio<Lote> {
    private val almacenamiento = LinkedHashMap<String, Lote>()

    override fun crear(entidad: Lote): Lote {
        almacenamiento[entidad.id] = entidad
        return entidad
    }

    override fun listar(): List<Lote> = almacenamiento.values.toList()

    override fun buscarPorId(id: String): Lote? = almacenamiento[id]

    fun listarPorProducto(productoId: String): List<Lote> =
        almacenamiento.values.filter { it.productoId == productoId }

    override fun actualizar(id: String, entidad: Lote): Boolean {
        if (!almacenamiento.containsKey(id)) return false
        almacenamiento[id] = entidad
        return true
    }

    override fun eliminar(id: String): Boolean = almacenamiento.remove(id) != null
}