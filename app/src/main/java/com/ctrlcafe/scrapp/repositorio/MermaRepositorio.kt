package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.modelo.Merma

class MermaRepositorio : Repositorio<Merma> {
    private val almacenamiento = LinkedHashMap<String, Merma>()

    override fun crear(entidad: Merma): Merma {
        almacenamiento[entidad.id] = entidad
        return entidad
    }

    override fun listar(): List<Merma> = almacenamiento.values.toList()

    override fun buscarPorId(id: String): Merma? = almacenamiento[id]

    fun listarPorLote(loteId: String): List<Merma> =
        almacenamiento.values.filter { it.loteId == loteId }

    fun listarPorProducto(productoId: String): List<Merma> =
        almacenamiento.values.filter { it.productoId == productoId }

    override fun actualizar(id: String, entidad: Merma): Boolean {
        if (!almacenamiento.containsKey(id)) return false
        almacenamiento[id] = entidad
        return true
    }

    override fun eliminar(id: String): Boolean = almacenamiento.remove(id) != null
}