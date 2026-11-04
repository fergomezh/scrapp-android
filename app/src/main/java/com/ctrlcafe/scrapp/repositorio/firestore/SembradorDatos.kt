package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.repositorio.DatosIniciales
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.repositorio.UsuarioRepositorio
import com.ctrlcafe.scrapp.repositorio.VentaRepositorio
import com.ctrlcafe.scrapp.modelo.CausaMerma
import com.ctrlcafe.scrapp.modelo.PerfilUsuario
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

    /**
     * Mermas de ejemplo propias del usuario que inicia sesión con Google.
     *
     * Las reglas solo dejan al operativo leer las mermas con su `usuarioId`, y las del
     * sembrado general son de "USR-02", así que una cuenta nueva vería su historial vacío.
     * Si el usuario aún no tiene mermas, se le crean unas pocas sobre los lotes existentes.
     * Son históricas (no descuentan existencias) y caen en el mes en curso.
     * Nunca hace fallar el login: ante cualquier error o sin conexión solo se registra en el log.
     */
    suspend fun sembrarMermasDemoSiNoTiene(perfil: PerfilUsuario) {
        try {
            withTimeoutOrNull(TIEMPO_LIMITE_MS) {
                val mermas = db.collection("mermas")
                    .whereEqualTo("usuarioId", perfil.uid)
                    .limit(1)
                    .get().await()
                if (!mermas.isEmpty) return@withTimeoutOrNull

                val lotes = db.collection("lotes").get().await().documents
                    .filter { (it.getDouble("cantidadInicial") ?: 0.0) > 0.0 && it.getString("productoId") != null }
                    .sortedBy { it.id }
                    .take(MERMAS_DEMO.size)
                if (lotes.isEmpty()) {
                    Logger.info(SembradorDatos::class.java, "Sin lotes en Firestore: no se siembran mermas demo")
                    return@withTimeoutOrNull
                }

                val hoy = LocalDate.now()
                val inicioMes = hoy.withDayOfMonth(1)
                val prefijo = "DEMO-${perfil.uid.take(6).uppercase()}"
                val escritura = db.batch()

                lotes.forEachIndexed { i, doc ->
                    val (diasAtras, causa) = MERMAS_DEMO[i]
                    val cantidadInicial = doc.getDouble("cantidadInicial") ?: 0.0
                    val datos = hashMapOf(
                        "loteId" to doc.id,
                        "productoId" to doc.getString("productoId").orEmpty(),
                        "cantidad" to if (cantidadInicial >= 10.0) 2.0 else 1.0,
                        "costoUnitarioCongelado" to (doc.getDouble("costoUnitario") ?: 0.0),
                        "fecha" to maxOf(hoy.minusDays(diasAtras), inicioMes).aTimestamp(),
                        "causa" to causa.name,
                        "urlEvidencia" to "sin_evidencia.jpg",
                        "usuarioId" to perfil.uid,
                        "nombreUsuario" to perfil.nombre,
                        "creadoEn" to FieldValue.serverTimestamp()
                    )
                    escritura.set(db.collection("mermas").document("$prefijo-${(i + 1).toString().padStart(2, '0')}"), datos)
                }
                // Sin await: offline queda en cola y el listener de mermas las muestra igual
                escritura.commit()
                Logger.info(SembradorDatos::class.java, "Mermas demo sembradas para ${perfil.uid}: ${lotes.size}")
            } ?: Logger.info(SembradorDatos::class.java, "Tiempo agotado al sembrar mermas demo")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Logger.error(SembradorDatos::class.java, "No se pudieron sembrar mermas demo: ${e.message}", e)
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

    // (días atrás, causa) de cada merma demo
    private val MERMAS_DEMO = listOf(
        0L to CausaMerma.NO_VENDIDO,
        1L to CausaMerma.VENCIMIENTO,
        2L to CausaMerma.DETERIORO_ALMACENAMIENTO,
        3L to CausaMerma.ERROR_PREPARACION,
        5L to CausaMerma.SOBREPRODUCCION,
        7L to CausaMerma.VENCIMIENTO
    )
}
