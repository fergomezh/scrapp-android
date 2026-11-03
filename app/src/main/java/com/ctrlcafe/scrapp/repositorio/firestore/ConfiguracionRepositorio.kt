package com.ctrlcafe.scrapp.repositorio.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfiguracionRepositorio {

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("configuracion").document("motores")

    // Usamos mapOf() en lugar de emptyMap() para evitar los errores de inferencia
    private val _configFlow = MutableStateFlow(mapOf<String, Any>())
    val configFlow = _configFlow.asStateFlow()

    private val escucha = EscuchaFirestore<DocumentSnapshot>("configuracion") { snapshot ->
        if (snapshot.exists()) {
            _configFlow.value = snapshot.data ?: mapOf()
        }
    }

    init {
        // Se reabre con cada sesión: sin usuario activo las reglas rechazan la lectura
        SesionFirestore.alCambiarUsuario { uid ->
            if (uid == null) escucha.detener() else escucha.iniciar(docRef)
        }
    }

    fun actualizarConfiguracion(margenSeguridad: Int, umbralVerde: Int, umbralAmarillo: Int, diasHistorial: Int) {
        val datos = mapOf(
            "margenSeguridad" to margenSeguridad,
            "umbralesSemaforo" to mapOf("verde" to umbralVerde, "amarillo" to umbralAmarillo),
            "diasHistorial" to diasHistorial
        )
        docRef.set(datos)
    }
}