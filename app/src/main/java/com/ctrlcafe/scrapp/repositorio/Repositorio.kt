package com.ctrlcafe.scrapp.repositorio

interface Repositorio<T> {
    fun crear(entidad: T): T
    fun listar(): List<T>
    fun buscarPorId(id: String ): T?
    fun actualizar(id: String, entidad: T): Boolean
    fun eliminar(id: String): Boolean
}