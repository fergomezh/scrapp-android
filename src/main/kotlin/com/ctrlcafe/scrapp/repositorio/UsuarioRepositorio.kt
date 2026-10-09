package com.ctrlcafe.scrapp.repositorio

import com.ctrlcafe.scrapp.modelo.Usuario

class UsuarioRepositorio : Repositorio<Usuario> {
    private val almacenamiento = LinkedHashMap<String, Usuario>()

    override fun crear(entidad: Usuario): Usuario {
        almacenamiento[entidad.id] = entidad
        return entidad
    }

    override fun listar(): List<Usuario> = almacenamiento.values.toList()

    override fun buscarPorId(id: String): Usuario? = almacenamiento[id]

    fun buscarPorUsername(username: String): Usuario? =
        almacenamiento.values.firstOrNull { it.username.equals(username, ignoreCase = true) }

    override fun actualizar(id: String, entidad: Usuario): Boolean {
        if (!almacenamiento.containsKey(id)) return false
        almacenamiento[id] = entidad
        return true
    }

    override fun eliminar(id: String): Boolean = almacenamiento.remove(id) != null
}