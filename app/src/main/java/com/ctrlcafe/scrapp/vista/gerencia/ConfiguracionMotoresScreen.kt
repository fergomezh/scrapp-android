package com.ctrlcafe.scrapp.vista.gerencia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.repositorio.firestore.ConfiguracionRepositorio
import com.ctrlcafe.scrapp.repositorio.firestore.SembradorDemostracion
import com.ctrlcafe.scrapp.vista.componentes.DialogoConfirmacion
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
    var sembrando by remember { mutableStateOf(false) }
    var confirmarSembrado by remember { mutableStateOf(false) }
    var limpiezaPendiente by remember { mutableStateOf<SembradorDemostracion.Limpieza?>(null) }
    val contexto = LocalContext.current

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Datos de demostración",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Agrega 5 semanas de historial (lotes y mermas por producto) y lotes actuales " +
                    "con caducidad escalonada para ver el semáforo en verde, amarillo, rojo y negro. " +
                    "No borra ni modifica datos existentes y se puede repetir sin duplicar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { confirmarSembrado = true },
                enabled = !sembrando && !guardando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (sembrando) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sembrar datos de demostración")
                }
            }

            Text(
                text = "Eliminar los lotes y mermas anteriores y dejar solo los de demostración. " +
                    "Productos, ventas, usuarios y configuración no se tocan. Antes de borrar se " +
                    "guarda un respaldo JSON en el teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    sembrando = true
                    alcance.launch {
                        try {
                            val calculo = SembradorDemostracion.calcularLimpieza()
                            if (calculo.vacia) {
                                snackbarHostState.showSnackbar("No hay lotes ni mermas anteriores que eliminar.")
                            } else {
                                limpiezaPendiente = calculo
                            }
                        } catch (e: Exception) {
                            Logger.error(SembradorDemostracion::class.java, "Error al calcular la limpieza: ${e.message}", e)
                            snackbarHostState.showSnackbar("No se pudo revisar la base: ${e.message}")
                        }
                        sembrando = false
                    }
                },
                enabled = !sembrando && !guardando,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Eliminar datos anteriores y resembrar")
            }
        }
    }

    limpiezaPendiente?.let { limpieza ->
        DialogoConfirmacion(
            titulo = "Eliminar datos anteriores",
            mensaje = "Se eliminarán de Firestore ${limpieza.lotes.size} lotes y ${limpieza.mermas.size} mermas " +
                "que no son de demostración. Esta acción no se puede deshacer desde la app (se guarda un " +
                "respaldo JSON en el teléfono). Después se volverán a sembrar los datos de demostración. ¿Continuar?",
            onConfirm = {
                limpiezaPendiente = null
                sembrando = true
                alcance.launch {
                    val mensaje = try {
                        val (respaldo, resumen) = SembradorDemostracion.limpiarYResembrar(
                            limpieza,
                            File(contexto.filesDir, "respaldos")
                        )
                        "Eliminados ${limpieza.lotes.size} lotes y ${limpieza.mermas.size} mermas " +
                            "(respaldo: ${respaldo.name}). Sembrados ${resumen.total} documentos nuevos."
                    } catch (e: Exception) {
                        Logger.error(SembradorDemostracion::class.java, "Error en la limpieza: ${e.message}", e)
                        "No se completó la limpieza: ${e.message}"
                    }
                    sembrando = false
                    snackbarHostState.showSnackbar(mensaje)
                }
            },
            onDismiss = { limpiezaPendiente = null }
        )
    }

    if (confirmarSembrado) {
        DialogoConfirmacion(
            titulo = "Sembrar datos de demostración",
            mensaje = "Se agregarán lotes y mermas de ejemplo en Firestore. Los datos actuales no se borran ni se modifican. ¿Continuar?",
            onConfirm = {
                confirmarSembrado = false
                sembrando = true
                alcance.launch {
                    val mensaje = try {
                        val resumen = SembradorDemostracion.sembrar()
                        if (resumen.total == 0) {
                            "Los datos de demostración de hoy ya estaban sembrados."
                        } else {
                            "Agregados: ${resumen.lotesHistoricos} lotes de historial, " +
                                "${resumen.lotesActuales} lotes actuales y ${resumen.mermas} mermas."
                        }
                    } catch (e: Exception) {
                        Logger.error(SembradorDemostracion::class.java, "Error al sembrar demostración: ${e.message}", e)
                        "No se pudieron sembrar los datos: ${e.message}"
                    }
                    sembrando = false
                    snackbarHostState.showSnackbar(mensaje)
                }
            },
            onDismiss = { confirmarSembrado = false }
        )
    }
}
