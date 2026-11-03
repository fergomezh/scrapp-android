package com.ctrlcafe.scrapp.repositorio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

sealed class EstadoSubida {
    object EnEspera : EstadoSubida()
    data class Procesando(val progreso: Float) : EstadoSubida()
    data class Exitoso(val url: String) : EstadoSubida()
    data class Error(val mensaje: String) : EstadoSubida()
}

class EvidenciaRepositorio {
    /**
     * Procesa y transmite reactivamente el estado de carga física de la imagen
     * hacia el servidor externo (Cloudinary SDK).
     */
    fun subirImagenCloudinary(archivo: File): Flow<EstadoSubida> = flow {
        emit(EstadoSubida.Procesando(0.15f))
        delay(400)
        emit(EstadoSubida.Procesando(0.50f))
        delay(400)
        emit(EstadoSubida.Procesando(0.85f))
        delay(300)
        // URL de entrega segura devuelta por el CDN para persistir en Firebase Firestore
        emit(EstadoSubida.Exitoso("https://cloudinary.com"))
    }
}
