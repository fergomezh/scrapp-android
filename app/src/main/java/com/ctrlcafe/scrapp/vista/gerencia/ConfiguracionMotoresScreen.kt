package com.ctrlcafe.scrapp.vista.gerencia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.repositorio.firestore.ConfiguracionRepositorio
import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.UmbralesSemaforo
import com.ctrlcafe.scrapp.util.Logger
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp
import kotlinx.coroutines.launch

/**
 * Permite al administrador ajustar el margen de seguridad de MotorProyeccion y los
 * umbrales generales del semáforo de caducidad. Se guardan en `configuracion/motores`
 * y el listener de ConfiguracionRepositorio los aplica en todos los dispositivos.
 */
@Composable
fun ConfiguracionMotoresScreen(
    navController: NavHostController,
    configuracion: ConfiguracionMotores = ContenedorDatos.configuracion,
    repositorio: ConfiguracionRepositorio = ContenedorDatos.configuracionRepositorio
) {
    // La configuración guarda el margen como fracción (0.10); en pantalla se edita en porcentaje (10)
    var margenSeguridad by remember {
        mutableStateOf("%.0f".format(configuracion.margenSeguridad * 100))
    }
    var horasCritico by remember { mutableStateOf(configuracion.umbralesPorDefecto.horasCritico.toString()) }
    var horasProximo by remember { mutableStateOf(configuracion.umbralesPorDefecto.horasProximo.toString()) }

    var errorMargen by remember { mutableStateOf<String?>(null) }
    var errorCritico by remember { mutableStateOf<String?>(null) }
    var errorProximo by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBarScrapp(
                title = "Configuración de Motores",
                showBackButton = true,
                onBackClick = { navController.popBackStack() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Proyección de Producción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "El margen de seguridad se suma a la demanda base para calcular la producción sugerida.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = margenSeguridad,
                onValueChange = {
                    margenSeguridad = it
                    errorMargen = null
                },
                label = { Text("Margen de Seguridad (%)") },
                isError = errorMargen != null,
                supportingText = { errorMargen?.let { Text(it) } },
                singleLine = true,
                enabled = !guardando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Semáforo de Caducidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Horas restantes antes de vencer. Rojo: hasta el umbral crítico · " +
                    "Amarillo: hasta el umbral próximo · Verde: el resto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = horasCritico,
                onValueChange = {
                    horasCritico = it
                    errorCritico = null
                },
                label = { Text("Umbral crítico (horas)") },
                isError = errorCritico != null,
                supportingText = { errorCritico?.let { Text(it) } },
                singleLine = true,
                enabled = !guardando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = horasProximo,
                onValueChange = {
                    horasProximo = it
                    errorProximo = null
                },
                label = { Text("Umbral próximo a vencer (horas)") },
                isError = errorProximo != null,
                supportingText = { errorProximo?.let { Text(it) } },
                singleLine = true,
                enabled = !guardando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val porcentaje = margenSeguridad.replace(',', '.').toDoubleOrNull()
                    val critico = horasCritico.trim().toLongOrNull()
                    val proximo = horasProximo.trim().toLongOrNull()

                    errorMargen = if (porcentaje == null || porcentaje < 0.0 || porcentaje > 100.0) {
                        "Ingrese un porcentaje entre 0 y 100."
                    } else null
                    errorCritico = if (critico == null || critico <= 0) "Ingrese un número de horas mayor a 0." else null
                    errorProximo = when {
                        proximo == null || proximo <= 0 -> "Ingrese un número de horas mayor a 0."
                        critico != null && proximo <= critico -> "Debe ser mayor que el umbral crítico ($critico h)."
                        else -> null
                    }
                    if (errorMargen != null || errorCritico != null || errorProximo != null) return@Button

                    guardando = true
                    alcance.launch {
                        try {
                            val confirmado = repositorio.guardar(
                                margenSeguridad = porcentaje!! / 100.0,
                                umbrales = UmbralesSemaforo(critico!!, proximo!!)
                            )
                            if (!confirmado) {
                                snackbarHostState.showSnackbar(
                                    "Guardado sin conexión; se sincronizará al volver la red"
                                )
                            }
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Logger.error(ConfiguracionRepositorio::class.java, "Error guardando configuración: ${e.message}", e)
                            guardando = false
                            snackbarHostState.showSnackbar("No se pudo guardar la configuración: ${e.message}")
                        }
                    }
                },
                enabled = !guardando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar Parámetros")
                }
            }
        }
    }
}
