package com.ctrlcafe.scrapp.vista.gerencia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ctrlcafe.scrapp.repositorio.ContenedorDatos
import com.ctrlcafe.scrapp.servicio.ConfiguracionMotores
import com.ctrlcafe.scrapp.servicio.UmbralesSemaforo
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp

/**
 * Permite al administrador ajustar el margen de seguridad que usa MotorProyeccion.
 * Se escribe sobre la misma ConfiguracionMotores de ContenedorDatos, así las
 * siguientes proyecciones del Dashboard de Producción usan el nuevo margen.
 */
@Composable
fun ConfiguracionMotoresScreen(
    navController: NavHostController,
    configuracion: ConfiguracionMotores = ContenedorDatos.configuracion
) {
    // La configuración guarda el margen como fracción (0.10); en pantalla se edita en porcentaje (10)
    var margenSeguridad by remember {
        mutableStateOf("%.0f".format(configuracion.margenSeguridad * 100))
    }
    var errorMargen by remember { mutableStateOf<String?>(null) }
    val umbralesPorDefecto = UmbralesSemaforo()

    Scaffold(
        topBar = {
            TopBarScrapp(
                title = "Configuración de Motores",
                showBackButton = true,
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Semáforo de Caducidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Rojo: menos de ${umbralesPorDefecto.horasCritico} h · " +
                    "Amarillo: menos de ${umbralesPorDefecto.horasProximo} h · Verde: el resto",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val porcentaje = margenSeguridad.replace(',', '.').toDoubleOrNull()
                    if (porcentaje == null || porcentaje < 0.0 || porcentaje > 100.0) {
                        errorMargen = "Ingrese un porcentaje entre 0 y 100."
                        return@Button
                    }
                    configuracion.margenSeguridad = porcentaje / 100.0
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Guardar Parámetros")
            }
        }
    }
}
