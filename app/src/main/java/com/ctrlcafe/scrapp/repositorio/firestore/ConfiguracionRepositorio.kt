package com.ctrlcafe.scrapp.repositorio.firestore

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfiguracionRepositorio {

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("configuracion").document("motores")

    // Usamos mapOf() en lugar de emptyMap() para evitar los errores de inferencia
    private val _configFlow = MutableStateFlow(mapOf<String, Any>())
    val configFlow = _configFlow.asStateFlow()

    init {
        docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val firestoreData = snapshot.data
            if (firestoreData != null) {
                _configFlow.value = firestoreData
            } else {
                _configFlow.value = mapOf()
            }
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