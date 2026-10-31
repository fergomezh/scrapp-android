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
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp

@Composable
fun ConfiguracionMotoresScreen(
    navController: NavHostController
) {
    // Estados locales para simular la edición de parámetros de control estratégico
    var margenSeguridad by remember { mutableStateOf("15.0") }
    var umbralMedio by remember { mutableStateOf("30.0") }
    var umbralCritico by remember { mutableStateOf("60.0") }

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
                text = "Límites del Semáforo y Alertas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ajuste los parámetros del motor de control de mermas y margen de stock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Input: Margen de Seguridad
            OutlinedTextField(
                value = margenSeguridad,
                onValueChange = { margenSeguridad = it },
                label = { Text("Margen de Seguridad (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Input: Umbral Advertencia (Medio)
            OutlinedTextField(
                value = umbralMedio,
                onValueChange = { umbralMedio = it },
                label = { Text("Umbral de Advertencia Medio (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Input: Umbral Crítico
            OutlinedTextField(
                value = umbralCritico,
                onValueChange = { umbralCritico = it },
                label = { Text("Umbral Crítico de Merma (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Botón para Guardar Configuración
            Button(
                onClick = {
                    // Guarda en la configuración lógica del sistema
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
