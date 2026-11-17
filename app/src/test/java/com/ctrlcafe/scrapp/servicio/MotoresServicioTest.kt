package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.Producto
import com.ctrlcafe.scrapp.modelo.RegistroVenta
import com.ctrlcafe.scrapp.repositorio.Repositorio
import com.ctrlcafe.scrapp.util.CantidadInvalidaException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MotoresServicioTest {

    private lateinit var repoProductos: RepoMemoria<Producto>
    private lateinit var repoLotes: RepoMemoria<Lote>
    private lateinit var repoMermas: RepoMemoria<Merma>
    private lateinit var repoVentas: RepoMemoria<RegistroVenta>

    private lateinit var configuracion: ConfiguracionMotores
    private lateinit var semaforo: MotorSemaforo
    private lateinit var financiero: MotorFinanciero
    private lateinit var proyeccion: MotorProyeccion
    private lateinit var orquestador: OrquestadorMerma

    private val hoy = LocalDate.of(2026, 11, 17)

    @Before
    fun setUp() {
        repoProductos = RepoMemoria { it.id }
        repoLotes = RepoMemoria { it.id }
        repoMermas = RepoMemoria { it.id }
        repoVentas = RepoMemoria { it.id }

        configuracion = ConfiguracionMotores()
        semaforo = MotorSemaforo(repoLotes, repoProductos, configuracion)
        financiero = MotorFinanciero(repoMermas, repoLotes, repoProductos)
        proyeccion = MotorProyeccion(repoVentas, repoMermas, repoLotes, repoProductos, configuracion)
        orquestador = OrquestadorMerma(repoLotes, repoMermas, repoProductos, semaforo, financiero, proyeccion)

        repoProductos.crear(
            Producto(
                id = "PROD-1",
                nombre = "Croissant Mantequilla",
                categoria = "Panaderia",
                costoUnitario = 1.50,
                precioVenta = 3.00,
                unidadMedida = "unidad",
                activo = true
            )
        )
    }

    // --- PRUEBAS DE MOTOR FINANCIERO ---

    @Test
    fun `motorFinanciero calcula perdida acumulada e indice de merma correctamente`() {
        repoLotes.crear(
            Lote(
                id = "LOTE-1",
                productoId = "PROD-1",
                cantidadInicial = 100.0,
                cantidadDisponible = 90.0,
                costoUnitario = 1.50,
                fechaIngreso = hoy.minusDays(2),
                vidaUtilDias = 5,
                fechaCaducidad = hoy.plusDays(3),
                estado = EstadoCaducidad.Vigente
            )
        )

        repoMermas.crear(
            Merma(
                id = "M-1",
                loteId = "LOTE-1",
                productoId = "PROD-1",
                cantidad = 10.0,
                costoUnitarioCongelado = 1.50,
                fecha = hoy,
                causa = CausaMerma.VENCIMIENTO,
                rutaEvidencia = "",
                usuarioId = "USR-1"
            )
        )

        val inicioMes = hoy.withDayOfMonth(1)
        // Producción: 100 * 1.50 = 150.0 | Merma: 10 * 1.50 = 15.0 | Índice: 10.0%
        assertEquals(15.0, financiero.perdidaAcumulada(inicioMes, hoy), 0.001)
        assertEquals(150.0, financiero.costoProduccion(inicioMes, hoy), 0.001)
        assertEquals(10.0, financiero.indiceMerma(inicioMes, hoy), 0.001)
    }

    @Test
    fun `motorFinanciero agrupa desglose por causa correctamente`() {
        repoMermas.crear(
            Merma("M-1", "LOTE-1", "PROD-1", 4.0, 2.0, hoy, CausaMerma.VENCIMIENTO, "", "U1")
        )
        repoMermas.crear(
            Merma("M-2", "LOTE-1", "PROD-1", 1.0, 2.0, hoy, CausaMerma.SOBREPRODUCCION, "", "U1")
        )

        val desglose = financiero.desglosePorCausa(hoy.minusDays(1), hoy)
        val vencimiento = desglose.first { it.causa == CausaMerma.VENCIMIENTO }
        val sobreprod = desglose.first { it.causa == CausaMerma.SOBREPRODUCCION }

        assertEquals(8.0, vencimiento.costoPerdida, 0.001)
        assertEquals(80.0, vencimiento.porcentajeDelTotal, 0.001)
        assertEquals(2.0, sobreprod.costoPerdida, 0.001)
        assertEquals(20.0, sobreprod.porcentajeDelTotal, 0.001)
    }

    @Test
    fun `motorFinanciero devuelve indice 0 cuando no hay produccion`() {
        assertEquals(0.0, financiero.indiceMerma(hoy.minusDays(5), hoy), 0.001)
    }

    // --- PRUEBAS DE MOTOR PROYECCION ---

    @Test
    fun `motorProyeccion calcula demanda ponderada y aplica margen y factor de merma`() {
        val fechaObjetivo = hoy.plusDays(1)
        // Ventas mismas semanas anteriores: 40, 30, 20, 10 -> 0.4*40 + 0.3*30 + 0.2*20 + 0.1*10 = 30.0
        repoVentas.crear(RegistroVenta("V1", "PROD-1", 40.0, fechaObjetivo.minusWeeks(1)))
        repoVentas.crear(RegistroVenta("V2", "PROD-1", 30.0, fechaObjetivo.minusWeeks(2)))
        repoVentas.crear(RegistroVenta("V3", "PROD-1", 20.0, fechaObjetivo.minusWeeks(3)))
        repoVentas.crear(RegistroVenta("V4", "PROD-1", 10.0, fechaObjetivo.minusWeeks(4)))

        val resultado = proyeccion.proyectar("PROD-1", fechaObjetivo)
        val esperada = 30.0 * (1.0 + configuracion.margenSeguridad)

        assertEquals(30.0, resultado.demandaBase, 0.001)
        assertEquals(0.0, resultado.tasaMerma, 0.001)
        assertEquals(esperada, resultado.cantidadSugerida, 0.001)
    }

    // --- PRUEBAS DE ORQUESTADOR MERMA ---

    @Test
    fun `orquestadorMerma descuenta existencias del lote y recalcula perdida del dia`() {
        repoLotes.crear(
            Lote(
                id = "LOTE-10",
                productoId = "PROD-1",
                cantidadInicial = 20.0,
                cantidadDisponible = 20.0,
                costoUnitario = 1.50,
                fechaIngreso = hoy,
                vidaUtilDias = 4,
                fechaCaducidad = hoy.plusDays(4),
                estado = EstadoCaducidad.Vigente
            )
        )

        val merma = Merma(
            id = "M-10",
            loteId = "LOTE-10",
            productoId = "PROD-1",
            cantidad = 5.0,
            costoUnitarioCongelado = 1.50,
            fecha = hoy,
            causa = CausaMerma.ERROR_PREPARACION,
            rutaEvidencia = "",
            usuarioId = "USR-1"
        )

        val recalculo = orquestador.registrarMerma(merma, LocalDateTime.of(2026, 11, 17, 10, 0))

        assertEquals(20.0, recalculo.cantidadLoteAntes, 0.001)
        assertEquals(15.0, recalculo.cantidadLoteDespues, 0.001)
        assertEquals(7.50, recalculo.costoPerdida, 0.001)
        assertEquals(7.50, recalculo.perdidaDiaDespues, 0.001)
    }

    @Test
    fun `orquestadorMerma rechaza cantidad mayor al disponible del lote`() {
        repoLotes.crear(
            Lote("LOTE-10", "PROD-1", 10.0, 3.0, 1.50, hoy, 4, hoy.plusDays(4), EstadoCaducidad.Vigente)
        )
        val mermaExcesiva = Merma(
            "M-99", "LOTE-10", "PROD-1", 5.0, 1.50, hoy, CausaMerma.VENCIMIENTO, "", "USR-1"
        )

        assertThrows(CantidadInvalidaException::class.java) {
            orquestador.registrarMerma(mermaExcesiva)
        }
    }

    /** Implementación ligera en memoria de Repositorio<T> para aislar las pruebas unitarias. */
    private class RepoMemoria<T>(private val idSelector: (T) -> String) : Repositorio<T> {
        private val datos = linkedMapOf<String, T>()
        override fun crear(entidad: T): T {
            datos[idSelector(entidad)] = entidad
            return entidad
        }
        override fun listar(): List<T> = datos.values.toList()
        override fun buscarPorId(id: String): T? = datos[id]
        override fun actualizar(id: String, entidad: T): Boolean {
            if (!datos.containsKey(id)) return false
            datos[id] = entidad
            return true
        }
        override fun eliminar(id: String): Boolean = datos.remove(id) != null
    }
}