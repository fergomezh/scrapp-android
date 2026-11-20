package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.repositorio.DatosIniciales
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.repositorio.UsuarioRepositorio
import com.ctrlcafe.scrapp.repositorio.VentaRepositorio
import com.ctrlcafe.scrapp.util.Logger
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SembradorDatos {
    private val db = FirebaseFirestore.getInstance()

    fun sembrarSiEstaVacio(
        firestoreProductoRepo: FirestoreProductoRepositorio,
        firestoreLoteRepo: FirestoreLoteRepositorio,
        firestoreVentaRepo: FirestoreVentaRepositorio,
        firestoreMermaRepo: FirestoreMermaRepositorio
    ) {
        // Consultamos si hay productos. Si no hay, asumimos que la base está vacía y sembramos.
        db.collection("productos").limit(1).get().addOnSuccessListener { snapshot ->
            if (snapshot != null && snapshot.isEmpty) {
                // 1. Cargamos los datos viejos en memoria temporalmente
                val usuRepo = UsuarioRepositorio()
                val prodRepo = ProductoRepositorio()
                val loteRepo = LoteRepositorio()
                val ventaRepo = VentaRepositorio()
                val mermaRepo = MermaRepositorio()

                DatosIniciales.cargar(usuRepo, prodRepo, loteRepo, ventaRepo, mermaRepo)

                // 2. Subimos todo a Firestore usando tus nuevos repositorios
                prodRepo.listar().forEach { firestoreProductoRepo.crear(it) }
                loteRepo.listar().forEach { firestoreLoteRepo.crear(it) }
                ventaRepo.listar().forEach { firestoreVentaRepo.crear(it) }
                mermaRepo.listar().forEach { firestoreMermaRepo.crear(it) }

                // 3. Sembramos la configuración inicial requerida
                val configInicial = mapOf<String, Any>(
                    "margenSeguridad" to 10,
                    "umbralesSemaforo" to mapOf<String, Int>("horasCritico" to 24, "horasProximo" to 72),
                    "diasHistorial" to 30
                )
                db.collection("configuracion").document("motores").set(configInicial)
            }
        }
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val candadoVentas = Mutex()

    /** Lanza [completarVentasRecientes] en segundo plano, sin hacer esperar al login. */
    fun programarVentasRecientes() {
        alcance.launch { completarVentasRecientes() }
    }
    private var ventasCompletadasEl: LocalDate? = null

    /**
     * Mantiene al día el historial de ventas que usa la recomendación de producción.
     *
     * MotorProyeccion promedia las ventas del mismo día de la semana de las 4 semanas
     * anteriores a la fecha objetivo. Las ventas del sembrado inicial se generaron una sola
     * vez y con los días dejan de caer en esa ventana, y los productos nuevos del catálogo no
     * tienen ninguna. Aquí se completan, para cada producto activo, los días de los últimos
     * [DIAS_VENTAS] que no tengan ninguna venta registrada.
     *
     * Las ventas generadas usan ids deterministas (`SIM-{producto}-{yyyyMMdd}`) y
     * `creadoPor = "SIMULADOR"`: así dos dispositivos no las duplican y las reglas pueden
     * permitir que cualquier usuario activo cree esas (y solo esas). Las ventas reales no se tocan.
     * Corre como máximo una vez al día por proceso y nunca lanza excepciones.
     */
    suspend fun completarVentasRecientes() {
        candadoVentas.withLock {
            val hoy = LocalDate.now()
            if (ventasCompletadasEl == hoy) return
            try {
                val completo = withTimeoutOrNull(TIEMPO_LIMITE_MS) {
                    val productos = db.collection("productos").get().await().documents
                        .filter { it.getBoolean("activo") ?: true }
                    if (productos.isEmpty()) return@withTimeoutOrNull false

                    // Días que ya tienen venta, por producto, y cantidades para estimar la base
                    val diasConVenta = mutableMapOf<String, MutableSet<LocalDate>>()
                    val cantidades = mutableMapOf<String, MutableList<Double>>()
                    db.collection("ventas").get().await().documents.forEach { doc ->
                        val productoId = doc.getString("productoId") ?: return@forEach
                        val fecha = doc.getTimestamp("fecha")?.aLocalDate() ?: return@forEach
                        diasConVenta.getOrPut(productoId) { mutableSetOf() }.add(fecha)
                        cantidades.getOrPut(productoId) { mutableListOf() }.add(doc.getDouble("cantidad") ?: 0.0)
                    }

                    val pendientes = mutableListOf<Pair<String, Map<String, Any>>>()
                    productos.forEach { producto ->
                        val base = cantidades[producto.id]?.takeIf { it.isNotEmpty() }?.average()
                            ?: demandaBaseEstimada(producto.id)
                        val existentes = diasConVenta[producto.id].orEmpty()
                        for (diasAtras in 1L..DIAS_VENTAS) {
                            val fecha = hoy.minusDays(diasAtras)
                            if (fecha in existentes) continue
                            pendientes += "SIM-${producto.id}-${fecha.format(FORMATO_ID)}" to mapOf(
                                "productoId" to producto.id,
                                "cantidad" to cantidadSimulada(producto.id, fecha, base),
                                "fecha" to fecha.aTimestamp(),
                                "creadoPor" to CREADOR_SIMULADO,
                                "creadoEn" to FieldValue.serverTimestamp()
                            )
                        }
                    }
                    // Un WriteBatch admite hasta 500 operaciones
                    pendientes.chunked(MAX_POR_LOTE).forEach { tramo ->
                        val escritura = db.batch()
                        tramo.forEach { (id, datos) -> escritura.set(db.collection("ventas").document(id), datos) }
                        // Sin await: offline queda en cola y el listener de ventas las refleja igual
                        escritura.commit().addOnFailureListener { e ->
                            Logger.error(SembradorDatos::class.java, "No se guardaron las ventas simuladas: ${e.message}", e)
                        }
                    }
                    Logger.info(SembradorDatos::class.java, "Ventas recientes completadas: ${pendientes.size} nuevas")
                    true
                }
                if (completo == true) ventasCompletadasEl = hoy
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logger.error(SembradorDatos::class.java, "No se pudieron completar las ventas recientes: ${e.message}", e)
            }
        }
    }

    /** Demanda diaria de un producto sin historial: estable por producto, entre 8 y 35 unidades. */
    private fun demandaBaseEstimada(productoId: String): Double =
        8.0 + Math.floorMod(productoId.hashCode(), 28)

    /**
     * Venta de un día: la base con un patrón semanal (más en viernes y fin de semana) y una
     * variación determinista de ±15 %, para que todos los dispositivos calculen lo mismo.
     */
    private fun cantidadSimulada(productoId: String, fecha: LocalDate, base: Double): Double {
        val factorDia = when (fecha.dayOfWeek) {
            DayOfWeek.FRIDAY -> 1.15
            DayOfWeek.SATURDAY -> 1.30
            DayOfWeek.SUNDAY -> 1.20
            DayOfWeek.MONDAY -> 0.85
            else -> 1.0
        }
        val variacion = (Math.floorMod((productoId + fecha.toString()).hashCode(), 31) - 15) / 100.0
        return Math.round(base * factorDia * (1 + variacion)).toDouble().coerceAtLeast(1.0)
    }

    private const val DIAS_VENTAS = 35L
    private const val MAX_POR_LOTE = 400
    private const val CREADOR_SIMULADO = "SIMULADOR"
    private val FORMATO_ID: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private const val TIEMPO_LIMITE_MS = 8_000L
}
