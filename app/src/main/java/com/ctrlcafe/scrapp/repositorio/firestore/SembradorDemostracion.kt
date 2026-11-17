package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.util.Logger
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Cuántos documentos agregó [SembradorDemostracion.sembrar]. */
data class ResumenSembrado(
    val lotesHistoricos: Int,
    val lotesActuales: Int,
    val mermas: Int
) {
    val total: Int get() = lotesHistoricos + lotesActuales + mermas
}

/**
 * Datos de demostración para la defensa, sembrados por un administrador desde
 * Configuración de Motores.
 *
 * Garantías para no dañar la base:
 *  - Solo CREA documentos con id `DEMOFIN-…`. Nunca borra ni actualiza documentos
 *    existentes: antes de escribir consulta los ids `DEMOFIN-` que ya existen y los omite.
 *  - Los ids son deterministas (producto + fecha), así que repetir la acción el mismo día
 *    no duplica nada; las mermas y lotes anteriores quedan intactos.
 *
 * Qué agrega:
 *  - Historial de 5 semanas: por producto activo, un lote por semana (lunes) ya agotado y
 *    2–3 mermas sobre él, repartidas entre los usuarios activos y con causas variadas.
 *    Las mermas no descuentan existencias de lotes reales.
 *  - Lotes actuales con caducidad escalonada (ayer, hoy, +1 … +7 y +10 días). Con los
 *    umbrales por defecto (24 h y 72 h) hay lotes en verde, amarillo, rojo y negro hoy
 *    y durante los siguientes 7 días sin volver a sembrar.
 *  - Las ventas de las últimas semanas las completa [SembradorDatos.completarVentasRecientes].
 */
object SembradorDemostracion {

    private val db = FirebaseFirestore.getInstance()

    suspend fun sembrar(hoy: LocalDate = LocalDate.now()): ResumenSembrado {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("No hay una sesión activa.")
        val perfil = db.collection("usuarios").document(uid).get().await()
        require(perfil.getString("rol") == "ADMINISTRADOR") { "Solo un administrador puede sembrar datos." }

        val productos = db.collection("productos").get().await().documents
            .filter { it.getBoolean("activo") ?: true }
            .sortedBy { it.id }
        require(productos.isNotEmpty()) { "No hay productos activos en el catálogo." }

        // Usuarios activos que firmarán las mermas (cada cuenta tendrá historial propio)
        val usuarios = db.collection("usuarios").get().await().documents
            .filter { it.getBoolean("activo") ?: true }
            .sortedBy { it.id }
            .map { it.id to (it.getString("nombre")?.takeIf { n -> n.isNotBlank() } ?: "Usuario") }
            .ifEmpty { listOf(uid to (perfil.getString("nombre") ?: "Administrador")) }

        // Venta diaria promedio por producto, para dimensionar los lotes
        val ventasPorProducto = db.collection("ventas").get().await().documents
            .groupBy({ it.getString("productoId").orEmpty() }, { it.getDouble("cantidad") ?: 0.0 })
            .mapValues { (_, cantidades) -> cantidades.average() }

        val lotesExistentes = idsConPrefijo("lotes")
        val mermasExistentes = idsConPrefijo("mermas")

        val nuevos = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
        var lotesHistoricos = 0
        var lotesActuales = 0
        var mermas = 0
        var turnoUsuario = 0

        fun agregarLote(id: String, datos: Map<String, Any>): Boolean {
            if (id in lotesExistentes) return false
            nuevos += db.collection("lotes").document(id) to datos
            return true
        }

        fun agregarMerma(id: String, datos: Map<String, Any>): Boolean {
            if (id in mermasExistentes) return false
            nuevos += db.collection("mermas").document(id) to datos
            return true
        }

        val lunesActual = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        productos.forEachIndexed { indiceProducto, producto ->
            val productoId = producto.id
            val costo = producto.getDouble("costoUnitario") ?: 0.0
            val ventaDiaria = ventasPorProducto[productoId]?.takeIf { it > 0.0 } ?: 12.0

            // --- Historial: un lote por semana ya cerrada (caducidad pasada), agotado, con sus mermas ---
            // La semana en curso no se siembra: su lote caducaría en el futuro y aparecería
            // como "agotado" con fecha posterior a hoy.
            for (semanasAtras in 1L..SEMANAS_HISTORIAL) {
                val ingreso = lunesActual.minusWeeks(semanasAtras)
                val idLote = "$PREFIJO-H-$productoId-${ingreso.format(FORMATO)}"
                val cantidadInicial = (ventaDiaria * 7 * 1.08).roundToInt().coerceAtLeast(10).toDouble()
                if (agregarLote(idLote, datosLote(productoId, cantidadInicial, 0.0, costo, ingreso, VIDA_HISTORICA, uid))) {
                    lotesHistoricos++
                }

                // Mermas sobre ese lote, en días ya transcurridos
                DIAS_MERMA.forEachIndexed { n, desfase ->
                    val fecha = ingreso.plusDays(desfase)
                    if (fecha.isAfter(hoy)) return@forEachIndexed
                    val idMerma = "$PREFIJO-M-$productoId-${fecha.format(FORMATO)}-${n + 1}"
                    val causa = CAUSAS[(indiceProducto + semanasAtras.toInt() + n) % CAUSAS.size]
                    val cantidad = (cantidadInicial * PORCENTAJES_MERMA[(indiceProducto + n) % PORCENTAJES_MERMA.size])
                        .roundToInt().coerceAtLeast(1).toDouble()
                    val (usuarioId, nombre) = usuarios[turnoUsuario++ % usuarios.size]
                    if (agregarMerma(idMerma, datosMerma(idLote, productoId, cantidad, costo, fecha, causa, usuarioId, nombre))) {
                        mermas++
                    }
                }
            }

            // --- Merma del día, para que "Pérdida del día" no quede en cero ---
            // Va sobre el lote actual de este producto (el de ayer, hoy o mañana: negro, rojo o amarillo)
            if (indiceProducto < PRODUCTOS_CON_MERMA_HOY && indiceProducto < DESFASES_CADUCIDAD.size) {
                val idLote = idLoteActual(productoId, hoy, indiceProducto)
                val idMerma = "$PREFIJO-M-$productoId-${hoy.format(FORMATO)}-HOY"
                val (usuarioId, nombre) = usuarios[turnoUsuario++ % usuarios.size]
                val causa = CAUSAS[(indiceProducto + 2) % CAUSAS.size]
                if (agregarMerma(idMerma, datosMerma(idLote, productoId, 2.0, costo, hoy, causa, usuarioId, nombre))) {
                    mermas++
                }
            }
        }

        // --- Lotes actuales: caducidad escalonada para cubrir los cuatro colores ---
        DESFASES_CADUCIDAD.forEachIndexed { i, desfase ->
            val producto = productos[i % productos.size]
            val costo = producto.getDouble("costoUnitario") ?: 0.0
            val ventaDiaria = ventasPorProducto[producto.id]?.takeIf { it > 0.0 } ?: 12.0
            val caducidad = hoy.plusDays(desfase)
            val ingreso = minOf(hoy, caducidad).minusDays(2)
            val vida = ChronoUnit.DAYS.between(ingreso, caducidad).toInt()
            val inicial = (ventaDiaria * 1.5).roundToInt().coerceAtLeast(8).toDouble()
            val disponible = (inicial * 0.6).roundToInt().coerceAtLeast(1).toDouble()
            val id = idLoteActual(producto.id, hoy, i)
            if (agregarLote(id, datosLote(producto.id, inicial, disponible, costo, ingreso, vida, uid))) {
                lotesActuales++
            }
        }

        // Escritura en tramos (un WriteBatch admite hasta 500 operaciones). Solo `set` de ids nuevos.
        nuevos.chunked(MAX_POR_LOTE).forEach { tramo ->
            val escritura = db.batch()
            tramo.forEach { (ref, datos) -> escritura.set(ref, datos) }
            escritura.commit().await()
        }

        // Ventas de las últimas 5 semanas para la proyección (solo completa días vacíos)
        SembradorDatos.programarVentasRecientes()

        val resumen = ResumenSembrado(lotesHistoricos, lotesActuales, mermas)
        Logger.info(SembradorDemostracion::class.java, "Datos de demostración sembrados: $resumen")
        return resumen
    }

    // ------------------------------------------------------------------ Limpieza

    /** Lotes y mermas que [limpiarAnteriores] eliminaría. */
    data class Limpieza(
        val lotes: List<DocumentSnapshot>,
        val mermas: List<DocumentSnapshot>
    ) {
        val vacia: Boolean get() = lotes.isEmpty() && mermas.isEmpty()
    }

    /**
     * Calcula, sin borrar nada, qué se eliminaría: todos los lotes y mermas que no son del
     * sembrado de demostración (`DEMOFIN-`), más los lotes de historial de la semana en curso
     * de un sembrado anterior (caducidad futura) y las mermas que apuntan a lotes eliminados.
     * Productos, ventas, usuarios y configuración nunca se tocan.
     */
    suspend fun calcularLimpieza(hoy: LocalDate = LocalDate.now()): Limpieza {
        verificarAdministrador()
        val lotes = db.collection("lotes").get().await().documents.filter { doc ->
            val caducidad = doc.getTimestamp("fechaCaducidad")?.aLocalDate()
            !doc.id.startsWith("$PREFIJO-") ||
                (doc.id.startsWith("$PREFIJO-H-") && caducidad != null && !caducidad.isBefore(hoy))
        }
        val idsLotes = lotes.map { it.id }.toSet()
        val mermas = db.collection("mermas").get().await().documents.filter { doc ->
            !doc.id.startsWith("$PREFIJO-") || doc.getString("loteId") in idsLotes
        }
        return Limpieza(lotes, mermas)
    }

    /**
     * Guarda un respaldo JSON de lo que se va a borrar en [carpetaRespaldo], elimina esos
     * lotes y mermas y vuelve a sembrar los datos de demostración.
     * Devuelve el archivo de respaldo y el resumen del sembrado.
     */
    suspend fun limpiarYResembrar(limpieza: Limpieza, carpetaRespaldo: File): Pair<File, ResumenSembrado> {
        verificarAdministrador()
        val respaldo = guardarRespaldo(limpieza, carpetaRespaldo)

        // Primero las mermas y luego los lotes, en tramos de hasta 400 operaciones
        (limpieza.mermas + limpieza.lotes).chunked(MAX_POR_LOTE).forEach { tramo ->
            val escritura = db.batch()
            tramo.forEach { escritura.delete(it.reference) }
            escritura.commit().await()
        }
        Logger.info(
            SembradorDemostracion::class.java,
            "Eliminados ${limpieza.lotes.size} lotes y ${limpieza.mermas.size} mermas; respaldo en ${respaldo.absolutePath}"
        )
        return respaldo to sembrar()
    }

    private fun guardarRespaldo(limpieza: Limpieza, carpeta: File): File {
        carpeta.mkdirs()
        fun aJson(valor: Any?): Any? = when (valor) {
            is Timestamp -> valor.toDate().toInstant().toString()
            is Map<*, *> -> JSONObject().apply { valor.forEach { (k, v) -> put(k.toString(), aJson(v)) } }
            is List<*> -> JSONArray().apply { valor.forEach { put(aJson(it)) } }
            null -> JSONObject.NULL
            else -> valor
        }
        fun coleccion(docs: List<DocumentSnapshot>) = JSONArray().apply {
            docs.forEach { doc -> put(JSONObject().put("id", doc.id).put("datos", aJson(doc.data))) }
        }
        val json = JSONObject()
            .put("creado", java.time.Instant.now().toString())
            .put("lotes", coleccion(limpieza.lotes))
            .put("mermas", coleccion(limpieza.mermas))
        val archivo = File(carpeta, "respaldo-${LocalDate.now().format(FORMATO)}-${System.currentTimeMillis()}.json")
        archivo.writeText(json.toString(2))
        return archivo
    }

    private suspend fun verificarAdministrador() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("No hay una sesión activa.")
        val perfil = db.collection("usuarios").document(uid).get().await()
        require(perfil.getString("rol") == "ADMINISTRADOR") { "Solo un administrador puede hacer esto." }
    }

    private fun idLoteActual(productoId: String, hoy: LocalDate, indice: Int): String =
        "$PREFIJO-A-$productoId-${hoy.format(FORMATO)}-${(indice + 1).toString().padStart(2, '0')}"

    /** Ids que ya empiezan con `DEMOFIN-` en la colección (consulta por rango de id). */
    private suspend fun idsConPrefijo(coleccion: String): Set<String> =
        db.collection(coleccion)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), "$PREFIJO-")
            .whereLessThan(FieldPath.documentId(), "$PREFIJO.")
            .get().await()
            .documents.map { it.id }.toSet()

    private fun datosLote(
        productoId: String,
        cantidadInicial: Double,
        cantidadDisponible: Double,
        costo: Double,
        ingreso: LocalDate,
        vidaUtilDias: Int,
        creadoPor: String
    ): Map<String, Any> = hashMapOf(
        "productoId" to productoId,
        "cantidadInicial" to cantidadInicial,
        "cantidadDisponible" to cantidadDisponible,
        "costoUnitario" to costo,
        "fechaIngreso" to ingreso.aTimestamp(),
        "vidaUtilDias" to vidaUtilDias,
        "fechaCaducidad" to ingreso.plusDays(vidaUtilDias.toLong()).aTimestamp(),
        "creadoPor" to creadoPor,
        "creadoEn" to FieldValue.serverTimestamp()
    )

    private fun datosMerma(
        loteId: String,
        productoId: String,
        cantidad: Double,
        costo: Double,
        fecha: LocalDate,
        causa: CausaMerma,
        usuarioId: String,
        nombreUsuario: String
    ): Map<String, Any> = hashMapOf(
        "loteId" to loteId,
        "productoId" to productoId,
        "cantidad" to cantidad,
        "costoUnitarioCongelado" to costo,
        "fecha" to fecha.aTimestamp(),
        "causa" to causa.name,
        "urlEvidencia" to "sin_evidencia.jpg",
        "usuarioId" to usuarioId,
        "nombreUsuario" to nombreUsuario,
        "creadoEn" to FieldValue.serverTimestamp()
    )

    private const val PREFIJO = "DEMOFIN"
    private const val SEMANAS_HISTORIAL = 5L
    private const val VIDA_HISTORICA = 6
    private const val PRODUCTOS_CON_MERMA_HOY = 3
    private const val MAX_POR_LOTE = 400

    /** Días después del ingreso en que se registra merma sobre cada lote semanal. */
    private val DIAS_MERMA = listOf(1L, 3L, 5L)
    private val PORCENTAJES_MERMA = listOf(0.02, 0.035, 0.05, 0.025)
    private val CAUSAS = CausaMerma.entries

    /** Ayer (negro), hoy (rojo), +1 y +2 (amarillo), +3 en adelante (verde). */
    private val DESFASES_CADUCIDAD = listOf(-1L, 0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 10L)

    private val FORMATO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
}
