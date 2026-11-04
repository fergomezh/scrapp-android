package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.UmbralesSemaforo
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.util.NivelLog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Parámetros de los motores en `configuracion/motores`, sincronizados en tiempo real.
 *
 * Cada cambio del documento se aplica sobre la misma [ConfiguracionMotores] que usan
 * los motores, así que si el administrador cambia el margen o los umbrales en un
 * dispositivo, la proyección y el semáforo se recalculan en todos.
 *
 * Formato del documento:
 * ```
 * margenSeguridad   10                       (porcentaje entero)
 * umbralesSemaforo  { horasCritico: 24, horasProximo: 72 }
 * diasHistorial     30
 * actualizadoEn     serverTimestamp
 * ```
 */
class ConfiguracionRepositorio(
    private val configuracion: ConfiguracionMotores
) {

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("configuracion").document("motores")

    // Usamos mapOf() en lugar de emptyMap() para evitar los errores de inferencia
    private val _configFlow = MutableStateFlow(mapOf<String, Any>())
    val configFlow: StateFlow<Map<String, Any>> = _configFlow.asStateFlow()

    private val escucha = EscuchaFirestore<DocumentSnapshot>("configuracion") { snapshot ->
        if (snapshot.exists()) {
            val datos = snapshot.data ?: mapOf()
            aplicar(datos)
            _configFlow.value = datos
        }
    }

    init {
        // Se reabre con cada sesión: sin usuario activo las reglas rechazan la lectura
        SesionFirestore.alCambiarUsuario { uid ->
            if (uid == null) escucha.detener() else escucha.iniciar(docRef)
        }
    }

    /**
     * Guarda los parámetros (solo administrador, por reglas). Se aplican en memoria al
     * instante; devuelve `false` si el servidor no confirmó a tiempo (queda en cola offline).
     */
    suspend fun guardar(margenSeguridad: Double, umbrales: UmbralesSemaforo): Boolean {
        configuracion.margenSeguridad = margenSeguridad
        configuracion.umbralesPorDefecto = umbrales

        val datos = mapOf(
            CAMPO_MARGEN to Math.round(margenSeguridad * 100).toInt(),
            CAMPO_UMBRALES to mapOf(
                CAMPO_HORAS_CRITICO to umbrales.horasCritico,
                CAMPO_HORAS_PROXIMO to umbrales.horasProximo
            ),
            "actualizadoEn" to FieldValue.serverTimestamp()
        )
        // merge: conserva diasHistorial y cualquier otro campo del documento
        val tarea = docRef.set(datos, SetOptions.merge())
        return withTimeoutOrNull(TIEMPO_CONFIRMACION_MS) { tarea.await() } != null
    }

    /** Pasa los valores del documento a [ConfiguracionMotores]; los inválidos se ignoran. */
    private fun aplicar(datos: Map<String, Any>) {
        (datos[CAMPO_MARGEN] as? Number)?.let { porcentaje ->
            runCatching { configuracion.margenSeguridad = porcentaje.toDouble() / 100.0 }
                .onFailure { advertir("margenSeguridad inválido: $porcentaje") }
        }

        val umbrales = datos[CAMPO_UMBRALES] as? Map<*, *> ?: return
        val critico = (umbrales[CAMPO_HORAS_CRITICO] as? Number)?.toLong()
        val proximo = (umbrales[CAMPO_HORAS_PROXIMO] as? Number)?.toLong()
        if (critico != null && proximo != null) {
            runCatching { configuracion.umbralesPorDefecto = UmbralesSemaforo(critico, proximo) }
                .onFailure { advertir("umbrales inválidos: $critico / $proximo") }
        }
    }

    private fun advertir(mensaje: String) =
        Logger.registrar(NivelLog.WARN, ConfiguracionRepositorio::class.java, "configuracion/motores: $mensaje")

    private companion object {
        const val CAMPO_MARGEN = "margenSeguridad"
        const val CAMPO_UMBRALES = "umbralesSemaforo"
        const val CAMPO_HORAS_CRITICO = "horasCritico"
        const val CAMPO_HORAS_PROXIMO = "horasProximo"
        const val TIEMPO_CONFIRMACION_MS = 5_000L
    }
}
