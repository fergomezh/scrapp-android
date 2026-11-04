package com.ctrlcafe.scrapp.repositorio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.ctrlcafe.scrapp.BuildConfig
import com.ctrlcafe.scrapp.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed class EstadoSubida {
    object EnEspera : EstadoSubida()
    data class Procesando(val progreso: Float) : EstadoSubida()
    data class Exitoso(val url: String) : EstadoSubida()
    data class Error(val mensaje: String) : EstadoSubida()
}

/**
 * Sube la foto de evidencia de una merma a Cloudinary con un *upload preset* sin firma
 * (POST multipart a `https://api.cloudinary.com/v1_1/{cloud}/image/upload`).
 *
 * Antes de subir, la imagen se reescala (lado mayor ≤ [LADO_MAXIMO_PX]) y se recomprime en
 * JPEG respetando la orientación EXIF, para que una foto de cámara de varios MB quede en
 * unos cientos de KB. La URL `secure_url` que devuelve Cloudinary es la que se guarda en
 * Firestore como `urlEvidencia`.
 */
class EvidenciaRepositorio(
    private val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME,
    private val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
    private val carpeta: String = "scrapp/mermas"
) {

    val estaConfigurado: Boolean
        get() = cloudName.isNotBlank() && uploadPreset.isNotBlank()

    fun subirImagenCloudinary(archivo: File): Flow<EstadoSubida> = flow {
        if (!estaConfigurado) {
            emit(EstadoSubida.Error("Cloudinary no está configurado (CLOUDINARY_CLOUD_NAME / CLOUDINARY_UPLOAD_PRESET)."))
            return@flow
        }
        if (!archivo.exists() || archivo.length() == 0L) {
            emit(EstadoSubida.Error("No se encontró la imagen a subir."))
            return@flow
        }

        emit(EstadoSubida.Procesando(0f))
        val bytes = comprimirImagen(archivo)
        emit(EstadoSubida.Procesando(0.05f))

        val limite = "----ScrappLimite${UUID.randomUUID()}"
        val cabecera = buildString {
            campo(limite, "upload_preset", uploadPreset)
            campo(limite, "folder", carpeta)
            append("--$limite\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"${archivo.nameWithoutExtension}.jpg\"\r\n")
            append("Content-Type: image/jpeg\r\n\r\n")
        }.toByteArray()
        val cierre = "\r\n--$limite--\r\n".toByteArray()

        val conexion = (URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$limite")
            setFixedLengthStreamingMode(cabecera.size + bytes.size + cierre.size)
        }

        try {
            DataOutputStream(conexion.outputStream).use { salida ->
                salida.write(cabecera)
                var enviados = 0
                while (enviados < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val tramo = minOf(TAMANO_TRAMO, bytes.size - enviados)
                    salida.write(bytes, enviados, tramo)
                    enviados += tramo
                    // 5 %–95 %: el último 5 % queda para la respuesta del servidor
                    emit(EstadoSubida.Procesando(0.05f + 0.90f * enviados / bytes.size))
                }
                salida.write(cierre)
                salida.flush()
            }

            val codigo = conexion.responseCode
            val cuerpo = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (codigo !in 200..299) {
                val detalle = runCatching {
                    JSONObject(cuerpo).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $codigo")
                Logger.error(EvidenciaRepositorio::class.java, "Cloudinary rechazó la subida: $detalle")
                emit(EstadoSubida.Error("No se pudo subir la evidencia: $detalle"))
                return@flow
            }

            val url = JSONObject(cuerpo).getString("secure_url")
            Logger.info(EvidenciaRepositorio::class.java, "Evidencia subida a Cloudinary: $url")
            emit(EstadoSubida.Procesando(1f))
            emit(EstadoSubida.Exitoso(url))
        } finally {
            conexion.disconnect()
        }
    }
        .catch { e ->
            Logger.error(EvidenciaRepositorio::class.java, "Error subiendo evidencia: ${e.message}", e)
            emit(EstadoSubida.Error("No se pudo subir la evidencia. Revisa tu conexión e inténtalo de nuevo."))
        }
        .flowOn(Dispatchers.IO)

    private fun StringBuilder.campo(limite: String, nombre: String, valor: String) {
        append("--$limite\r\n")
        append("Content-Disposition: form-data; name=\"$nombre\"\r\n\r\n")
        append(valor).append("\r\n")
    }

    /** Reescala y recomprime la foto; si no se puede decodificar, se sube el archivo tal cual. */
    private fun comprimirImagen(archivo: File): ByteArray {
        val opcionesTamano = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.absolutePath, opcionesTamano)
        val ladoMayor = maxOf(opcionesTamano.outWidth, opcionesTamano.outHeight)
        if (ladoMayor <= 0) return archivo.readBytes()

        var muestreo = 1
        while (ladoMayor / (muestreo * 2) >= LADO_MAXIMO_PX) muestreo *= 2

        val original = BitmapFactory.decodeFile(
            archivo.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = muestreo }
        ) ?: return archivo.readBytes()

        val escala = LADO_MAXIMO_PX.toFloat() / maxOf(original.width, original.height)
        val matriz = Matrix().apply {
            if (escala < 1f) postScale(escala, escala)
            postRotate(rotacionExif(archivo))
        }
        val ajustada = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)

        return ByteArrayOutputStream().use { salida ->
            ajustada.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            if (ajustada !== original) ajustada.recycle()
            original.recycle()
            salida.toByteArray()
        }
    }

    private fun rotacionExif(archivo: File): Float = runCatching {
        when (ExifInterface(archivo.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)

    private companion object {
        const val LADO_MAXIMO_PX = 1600
        const val CALIDAD_JPEG = 80
        const val TAMANO_TRAMO = 16 * 1024
        const val TIMEOUT_MS = 30_000
    }
}
