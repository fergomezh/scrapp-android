package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.util.LoteVencidoException
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import java.time.Duration
import java.time.LocalDateTime

/**
 * Semáforo de caducidad por horas restantes.
 *
 * Regla (umbrales por defecto, configurables por producto):
 *  - más de 72 h        -> Vigente        (VERDE)
 *  - de 24 h a 72 h     -> ProximoAVencer (AMARILLO)
 *  - de 0 h a 24 h      -> Critico        (ROJO)
 *  - 0 h o menos        -> Vencido        (NEGRO)
 *
 * CONVENCIÓN DE HORA: `Lote.fechaCaducidad` es un `LocalDate` sin hora, así que
 * el lote se considera apto hasta el final de su día de caducidad; vence a las
 * 00:00 del día siguiente. Es la misma convención que usa `LoteController`
 * (`fechaCaducidad.isBefore(hoy)` => vencido).
 */
class MotorSemaforo(
    private val loteRepositorio: LoteRepositorio,
    private val productoRepositorio: ProductoRepositorio,
    private val configuracion: ConfiguracionMotores
) {

    fun instanteVencimiento(lote: Lote): LocalDateTime =
        lote.fechaCaducidad.plusDays(1).atStartOfDay()

    fun minutosRestantes(lote: Lote, ahora: LocalDateTime = LocalDateTime.now()): Long =
        Duration.between(ahora, instanteVencimiento(lote)).toMinutes()

    /** Horas restantes redondeadas hacia arriba; 0 o negativo si ya venció. */
    fun horasRestantes(lote: Lote, ahora: LocalDateTime = LocalDateTime.now()): Long {
        val minutos = minutosRestantes(lote, ahora)
        return if (minutos <= 0) minutos / 60 else (minutos + 59) / 60
    }

    /** Evalúa el estado sin modificar el lote. */
    fun evaluar(lote: Lote, ahora: LocalDateTime = LocalDateTime.now()): EstadoCaducidad {
        val umbrales = configuracion.umbralesDe(lote.productoId)
        val minutos = minutosRestantes(lote, ahora)
        val horas = horasRestantes(lote, ahora)

        return when {
            minutos <= 0 -> EstadoCaducidad.Vencido
            minutos <= umbrales.horasCritico * 60 -> EstadoCaducidad.Critico(horas)
            minutos <= umbrales.horasProximo * 60 -> EstadoCaducidad.ProximoAVencer(horas)
            else -> EstadoCaducidad.Vigente
        }
    }

    fun bloqueadoParaUso(lote: Lote, ahora: LocalDateTime = LocalDateTime.now()): Boolean =
        evaluar(lote, ahora) is EstadoCaducidad.Vencido

    /**
     * Lanza [LoteVencidoException] si el lote no puede usarse. Pensado para que
     * los controladores lo llamen antes de consumir existencias.
     */
    fun verificarUsable(lote: Lote, ahora: LocalDateTime = LocalDateTime.now()) {
        if (bloqueadoParaUso(lote, ahora)) {
            Logger.registrar(NivelLog.WARN, MotorSemaforo::class.java, "Intento de uso del lote vencido ${lote.id}")
            throw LoteVencidoException(lote.id)
        }
    }

    /** Reevalúa un lote, guarda el nuevo estado en el repositorio y lo devuelve. */
    fun recalcular(lote: Lote, ahora: LocalDateTime = LocalDateTime.now()): EstadoLote {
        lote.estado = evaluar(lote, ahora)
        loteRepositorio.actualizar(lote.id, lote)
        return aEstadoLote(lote, ahora)
    }

    /**
     * Recalcula todos los lotes y los devuelve ordenados por urgencia.
     * Se llama al iniciar la aplicación y al abrir el monitor de lotes.
     *
     * @param incluirAgotados los lotes sin existencias no generan alertas, por
     * eso se omiten salvo que se pidan explícitamente.
     */
    fun recalcularTodos(
        ahora: LocalDateTime = LocalDateTime.now(),
        incluirAgotados: Boolean = false
    ): List<EstadoLote> {
        val resultado = loteRepositorio.listar()
            .filter { incluirAgotados || it.cantidadDisponible > 0.0 }
            .map { recalcular(it, ahora) }
            .sortedBy { minutosRestantes(it.lote, ahora) }

        Logger.info(MotorSemaforo::class.java, "Semáforo recalculado para ${resultado.size} lotes")
        return resultado
    }

    /** Lotes vencidos que aún tienen existencias: el sistema propone registrar su merma. */
    fun lotesParaRegistrarMerma(ahora: LocalDateTime = LocalDateTime.now()): List<EstadoLote> =
        recalcularTodos(ahora).filter { it.bloqueado }

    /** Cantidad de lotes por nivel de alerta, en orden VERDE, AMARILLO, ROJO, NEGRO. */
    fun conteoPorEstado(ahora: LocalDateTime = LocalDateTime.now()): Map<String, Int> {
        val conteo = recalcularTodos(ahora).groupingBy { it.estado.nivelAlerta }.eachCount()
        return NIVELES.associateWith { conteo[it] ?: 0 }
    }

    private fun aEstadoLote(lote: Lote, ahora: LocalDateTime) = EstadoLote(
        lote = lote,
        nombreProducto = productoRepositorio.buscarPorId(lote.productoId)?.nombre ?: lote.productoId,
        horasRestantes = horasRestantes(lote, ahora),
        estado = lote.estado,
        bloqueado = lote.estado is EstadoCaducidad.Vencido
    )

    companion object {
        val NIVELES = listOf("VERDE", "AMARILLO", "ROJO", "NEGRO")
    }
}
