package com.ctrlcafe.scrapp.servicio

import com.ctrlcafe.scrapp.modelo.Lote
import com.ctrlcafe.scrapp.modelo.Merma
import com.ctrlcafe.scrapp.modelo.RegistroVenta
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.round

/**
 * Funciones de extensión que usan los tres motores. Concentrarlas acá evita
 * repetir `filter`/`sumOf` en cada motor y deja los cálculos legibles.
 */

// ------------------------------------------------------------------ Fechas

/** `true` si la fecha cae dentro del rango, con ambos extremos incluidos. */
fun LocalDate.entre(desde: LocalDate, hasta: LocalDate): Boolean =
    !this.isBefore(desde) && !this.isAfter(hasta)

// ------------------------------------------------------------------ Mermas

fun List<Merma>.enRango(desde: LocalDate, hasta: LocalDate): List<Merma> =
    filter { it.fecha.entre(desde, hasta) }

fun List<Merma>.mermasDeProducto(productoId: String): List<Merma> =
    filter { it.productoId == productoId }

/** Unidades desperdiciadas. */
fun List<Merma>.unidadesTotales(): Double = sumOf { it.cantidad }

/** Costo de la pérdida, siempre con el costo unitario congelado al registrar. */
fun List<Merma>.costoTotal(): Double = sumOf { it.cantidad * it.costoUnitarioCongelado }

// ------------------------------------------------------------------- Lotes

fun List<Lote>.ingresadosEntre(desde: LocalDate, hasta: LocalDate): List<Lote> =
    filter { it.fechaIngreso.entre(desde, hasta) }

fun List<Lote>.lotesDeProducto(productoId: String): List<Lote> =
    filter { it.productoId == productoId }

/**
 * Unidades producidas: se toma `cantidadInicial` del lote como equivalente de
 * producción, ya que el dominio no registra producción por separado.
 * Es el denominador de la tasa de merma.
 */
fun List<Lote>.unidadesProducidas(): Double = sumOf { it.cantidadInicial }

/** Costo de producción: denominador del índice de merma. */
fun List<Lote>.costoProduccion(): Double = sumOf { it.cantidadInicial * it.costoUnitario }

// ------------------------------------------------------------------ Ventas

/**
 * Unidades vendidas de un producto en una fecha exacta, o `null` si ese día no
 * tiene registro. El `null` es intencional: distingue "no se vendió nada" de
 * "no hay dato", y la proyección renormaliza pesos según eso.
 */
fun List<RegistroVenta>.unidadesEnFecha(productoId: String, fecha: LocalDate): Double? {
    val delDia = filter { it.productoId == productoId && it.fecha == fecha }
    return if (delDia.isEmpty()) null else delDia.sumOf { it.cantidad }
}

// ------------------------------------------------------------------ Número

/** Redondeo para presentación; evita arrastrar decimales de punto flotante. */
fun Double.redondear(decimales: Int = 2): Double {
    val factor = 10.0.pow(decimales)
    return round(this * factor) / factor
}

/**
 * Porcentaje que representa este valor sobre [total].
 * Devuelve 0.0 si el total es cero, para no propagar `NaN` a la consola.
 */
fun Double.porcentajeDe(total: Double): Double =
    if (total == 0.0) 0.0 else (this / total) * 100.0
