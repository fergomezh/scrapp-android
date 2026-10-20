package com.ctrlcafe.scrapp

import com.ctrlcafe.scrapp.controlador.ProveedorDependenciasModuloPrincipal
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Una merma registrada debe verse en el monitor de lotes, el dashboard y gerencia. */
class IntegracionMermaTest {

    @Test
    fun modulosCompartenLasMismasInstancias() {
        assertSame(ContenedorDatos.loteRepositorio, ProveedorDependenciasModuloPrincipal.loteRepositorio)
        assertSame(ContenedorDatos.productoRepositorio, ProveedorDependenciasModuloPrincipal.productoRepositorio)
        assertSame(ContenedorDatos.semaforo, ProveedorDependenciasModuloPrincipal.motorSemaforo)
        assertSame(ContenedorDatos.proyeccion, ProveedorDependenciasModuloPrincipal.motorProyeccion)
    }

    @Test
    fun mermaDeHoyCambiaLoteProyeccionYPerdida() {
        val hoy = LocalDate.now()
        val manana = hoy.plusDays(1)
        val lote = ProveedorDependenciasModuloPrincipal.loteRepositorio.buscarPorId("LOT-002")!!
        val disponibleAntes = lote.cantidadDisponible
        val sugeridaAntes = ProveedorDependenciasModuloPrincipal.motorProyeccion
            .proyectar("PROD-02", manana).cantidadSugerida
        val perdidaAntes = ContenedorDatos.financiero.perdidaDelDia(hoy)

        // Mismo flujo que MermaViewModel.registrarMerma
        lote.cantidadDisponible -= 5.0
        ContenedorDatos.loteRepositorio.actualizar(lote.id, lote)
        val merma = Merma("MER-TEST", lote.id, lote.productoId, 5.0, lote.costoUnitario, hoy,
            CausaMerma.DETERIORO_ALMACENAMIENTO, "sin_evidencia.jpg", "test")
        ContenedorDatos.mermaRepositorio.crear(merma)
        ContenedorDatos.orquestadorMerma.aplicarMerma(merma)

        val loteMonitor = ProveedorDependenciasModuloPrincipal.motorSemaforo.recalcularTodos()
            .first { it.lote.id == "LOT-002" }
        assertEquals(disponibleAntes - 5.0, loteMonitor.lote.cantidadDisponible, 0.0001)

        val sugeridaDespues = ProveedorDependenciasModuloPrincipal.motorProyeccion
            .proyectar("PROD-02", manana).cantidadSugerida
        assertTrue("La proyección de mañana debe bajar", sugeridaDespues < sugeridaAntes)

        assertEquals(perdidaAntes + 5.0 * lote.costoUnitario, ContenedorDatos.financiero.perdidaDelDia(hoy), 0.0001)
    }
}
