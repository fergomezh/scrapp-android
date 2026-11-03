package com.ctrlcafe.scrapp.vista.mermas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CamaraEvidenciaScreen(
    onImagenConfirmada: (File) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var mostrarExplicacion by remember { mutableStateOf(false) }

    val launcherPermiso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { aceptado ->
        tienePermiso = aceptado
        if (!aceptado) {
            mostrarExplicacion = true
        }
    }

    LaunchedEffect(Unit) {
        if (!tienePermiso) {
            launcherPermiso.launch(Manifest.permission.CAMERA)
        }
    }

    val launcherGaleria = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val archivo = uriToFile(context, it)
            if (archivo != null) onImagenConfirmada(archivo)
        }
    }

    if (mostrarExplicacion) {
        AlertDialog(
            onDismissRequest = { mostrarExplicacion = false },
            title = { Text("Permiso Obligatorio") },
            text = { Text("Scrapp requiere acceso a la cámara para capturar la evidencia de la merma.") },
            confirmButton = {
                Button(onClick = {
                    mostrarExplicacion = false
                    launcherPermiso.launch(Manifest.permission.CAMERA)
                }) { Text("Conceder") }
            },
            dismissButton = {
                TextButton(onClick = onCancelar) { Text("Regresar") }
            }
        )
    }

    if (tienePermiso) {
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        val imageCapture = remember { ImageCapture.Builder().build() }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { launcherGaleria.launch("image/*") }) { Text("Galería") }

                Button(
                    onClick = {
                        val directorioOutput = context.cacheDir
                        val archivoFoto = File(
                            directorioOutput,
                            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + "_evidencia.jpg"
                        )
                        val opcionesOutput = ImageCapture.OutputFileOptions.Builder(archivoFoto).build()

                        // Corrección de paso de ejecutor compatible por posición para la firma del SDK
                        imageCapture.takePicture(
                            opcionesOutput,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onImagenConfirmada(archivoFoto)
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        Toast.makeText(context, "Error en captura", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.size(76.dp)
                ) { Text("Foto") }

                TextButton(onClick = onCancelar) { Text("Cancelar", color = MaterialTheme.colorScheme.surface) }
            }
        }
    }
}

private fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        val archivoTemporal = File.createTempFile("evidencia_galeria_", ".jpg", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            archivoTemporal.outputStream().use { output -> input.copyTo(output) }
        }
        archivoTemporal
    } catch (e: Exception) {
        null
    }
}
