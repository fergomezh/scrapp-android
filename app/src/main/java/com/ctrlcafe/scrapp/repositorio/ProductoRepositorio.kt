package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.modelo.Producto

class ProductoRepositorio : Repositorio<Producto> {
    private val almacenamiento = LinkedHashMap<String, Producto>()

    override fun crear(entidad: Producto): Producto {
        almacenamiento[entidad.id] = entidad
        return entidad
    }

    override fun listar(): List<Producto> = almacenamiento.values.toList()

    override fun buscarPorId(id: String): Producto? = almacenamiento[id]

    override fun actualizar(id: String, entidad: Producto): Boolean {
        if (!almacenamiento.containsKey(id)) return false
        almacenamiento[id] = entidad
        return true
    }

    override fun eliminar(id: String): Boolean = almacenamiento.remove(id) != null
}