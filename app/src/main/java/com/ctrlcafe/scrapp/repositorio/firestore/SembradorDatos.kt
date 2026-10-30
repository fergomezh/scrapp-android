package com.ctrlcafe.scrapp.repositorio.firestore

import com.ctrlcafe.scrapp.repositorio.DatosIniciales
import com.ctrlcafe.scrapp.repositorio.LoteRepositorio
import com.ctrlcafe.scrapp.repositorio.MermaRepositorio
import com.ctrlcafe.scrapp.repositorio.ProductoRepositorio
import com.ctrlcafe.scrapp.repositorio.UsuarioRepositorio
import com.ctrlcafe.scrapp.repositorio.VentaRepositorio
import com.google.firebase.firestore.FirebaseFirestore

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
                    "umbralesSemaforo" to mapOf<String, Int>("verde" to 7, "amarillo" to 3),
                    "diasHistorial" to 30
                )
                db.collection("configuracion").document("motores").set(configInicial)
            }
        }
    }
}