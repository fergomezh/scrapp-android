package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.repositorio.VentaRepositorio
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import com.ctrlcafe.scrapp.util.ProductoNoEncontradoException
import java.time.LocalDate

/**
 * Proyección diaria de producción.
 *
 *  demandaBase      = 0.40*V1 + 0.30*V2 + 0.20*V3 + 0.10*V4
 *  tasaMerma        = unidadesDesperdiciadas14 / unidadesProducidas14
 *  factorCorreccion = min(tasaMerma, margenSeguridad)
 *  cantidadSugerida = demandaBase * (1 + margenSeguridad − factorCorreccion)
 *
 * V1..V4 son las ventas del mismo día de la semana, 1 a 4 semanas antes de la
 * fecha objetivo. La ventana de 14 días de la tasa termina el día anterior a la
 * fecha objetivo.
 */
class MotorProyeccion(
    private val ventaRepositorio: VentaRepositorio,
    private val mermaRepositorio: MermaRepositorio,
    private val loteRepositorio: LoteRepositorio,
    private val productoRepositorio: ProductoRepositorio,
    private val configuracion: ConfiguracionMotores
) {

    fun proyectar(
        productoId: String,
        fechaObjetivo: LocalDate = LocalDate.now().plusDays(1)
    ): ProyeccionProducto = proyectarCon(productoId, fechaObjetivo, mermaRepositorio.listar())

    fun proyectarTodos(fechaObjetivo: LocalDate = LocalDate.now().plusDays(1)): List<ProyeccionProducto> =
        productoRepositorio.listar()
            .map { proyectar(it.id, fechaObjetivo) }
            .sortedByDescending { it.cantidadSugerida }

    /** Tasa de merma de los 14 días que terminan en [hasta], inclusive. */
    fun tasaMerma(productoId: String, hasta: LocalDate = LocalDate.now()): Double =
        tasaMermaCon(productoId, hasta, mermaRepositorio.listar())

    /**
     * Variante que recibe la lista de mermas. La usa [OrquestadorMerma] para
     * calcular el valor "antes" excluyendo la merma recién registrada.
     */
    internal fun tasaMermaCon(productoId: String, hasta: LocalDate, mermas: List<Merma>): Double {
        val desde = hasta.minusDays(ConfiguracionMotores.DIAS_VENTANA_MERMA - 1)
        val producidas = loteRepositorio.listar()
            .lotesDeProducto(productoId)
            .ingresadosEntre(desde, hasta)
            .unidadesProducidas()

        if (producidas == 0.0) {
            Logger.registrar(
                NivelLog.WARN, MotorProyeccion::class.java,
                "Sin unidades producidas para $productoId entre $desde y $hasta; tasa de merma = 0.0"
            )
            return 0.0
        }

        val desperdiciadas = mermas.mermasDeProducto(productoId).enRango(desde, hasta).unidadesTotales()
        return desperdiciadas / producidas
    }

    internal fun proyectarCon(productoId: String, fechaObjetivo: LocalDate, mermas: List<Merma>): ProyeccionProducto {
        val producto = productoRepositorio.buscarPorId(productoId)
            ?: throw ProductoNoEncontradoException(productoId)

        val ventas = ventaRepositorio.listar()
        val equivalentes = (1..ConfiguracionMotores.SEMANAS_PROYECCION).map { semanas ->
            ventas.unidadesEnFecha(productoId, fechaObjetivo.minusWeeks(semanas.toLong()))
        }

        val margen = configuracion.margenSeguridad
        val tasa = tasaMermaCon(productoId, fechaObjetivo.minusDays(1), mermas)
        val factor = minOf(tasa, margen)

        val presentes = equivalentes.zip(ConfiguracionMotores.PESOS_PROYECCION)
            .filter { (venta, _) -> venta != null }

        val advertencia: String?
        val demandaBase: Double

        if (presentes.isEmpty()) {
            advertencia = "Sin historial de ventas en los días equivalentes; no se sugiere producción"
            demandaBase = 0.0
            Logger.registrar(NivelLog.WARN, MotorProyeccion::class.java, "$productoId: $advertencia")
        } else {
            val sumaPesos = presentes.sumOf { (_, peso) -> peso }
            demandaBase = presentes.sumOf { (venta, peso) -> venta!! * peso } / sumaPesos
            advertencia = if (presentes.size < equivalentes.size) {
                "Historial parcial (${presentes.size} de ${equivalentes.size} semanas); pesos renormalizados"
            } else null
        }

        return ProyeccionProducto(
            productoId = productoId,
            nombreProducto = producto.nombre,
            fechaObjetivo = fechaObjetivo,
            ventasEquivalentes = equivalentes,
            demandaBase = demandaBase,
            tasaMerma = tasa,
            margenSeguridad = margen,
            factorCorreccion = factor,
            cantidadSugerida = demandaBase * (1 + margen - factor),
            advertencia = advertencia
        )
    }
}
