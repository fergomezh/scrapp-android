package com.ctrlcafe.scrapp

import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba que los motores migrados de la Etapa 2 funcionan dentro del proyecto Android. */
class ContenedorDatosTest {

    @Test
    fun cargaDatosIniciales() {
        assertTrue(ContenedorDatos.productoRepositorio.listar().isNotEmpty())
        assertTrue(ContenedorDatos.loteRepositorio.listar().isNotEmpty())
    }

    @Test
    fun motoresCalculanSobreLosDatosIniciales() {
        assertTrue(ContenedorDatos.semaforo.recalcularTodos().isNotEmpty())
        assertTrue(ContenedorDatos.proyeccion.proyectarTodos().isNotEmpty())
    }
}
