package com.ctrlcafe.scrapp.vista.gerencia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ctrlcafe.scrapp.controlador.GerenciaViewModel
import com.ctrlcafe.scrapp.ui.theme.AlertaCritica
import com.ctrlcafe.scrapp.ui.theme.TextoSecundario
import com.ctrlcafe.scrapp.vista.componentes.TopBarScrapp

@Composable
fun PanelGerenciaScreen(
    navController: NavHostController,
    viewModel: GerenciaViewModel = GerenciaViewModel()
) {
    val perdidaDia = viewModel.perdidaDia.value
    val perdidaMes = viewModel.perdidaMes.value
    val indiceMerma = viewModel.indiceMerma.value
    val productosCriticos = viewModel.productosCriticos.value

    Scaffold(
        topBar = { TopBarScrapp(title = "Panel de Gerencia", showBackButton = false) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text(text = "Resumen Financiero", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaFinanciera("Pérdida del Día", String.format("$%.2f", perdidaDia), Modifier.weight(1f))
                    TarjetaFinanciera("Pérdida Acumulada", String.format("$%.2f", perdidaMes), Modifier.weight(1f))
                }
            }
            item { TarjetaFinanciera("Índice de Merma Global", String.format("%.1f%%", indiceMerma * 100), Modifier.fillMaxWidth()) }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Top 5 Productos Críticos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(productosCriticos) { (nombre, porcentaje) ->
                BarraProgresoProducto(nombre = nombre, porcentaje = porcentaje, textoPorcentaje = String.format("%.0f%%", porcentaje * 100))
            }
        }
    }
}

@Composable
fun TarjetaFinanciera(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            Spacer(modifier = Modifier.height(6.dp))
            Text(valor, style = MaterialTheme.typography.headlineMedium, color = AlertaCritica, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BarraProgresoProducto(nombre: String, porcentaje: Float, textoPorcentaje: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(textoPorcentaje, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(6.dp))) {
            Box(modifier = Modifier.fillMaxWidth(porcentaje).fillMaxHeight().background(AlertaCritica, RoundedCornerShape(6.dp)))
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@androidx.compose.runtime.Composable
fun PreviewPanelGerenciaDefinitivo() {
    com.ctrlcafe.scrapp.ui.theme.ScrappTheme {
        PanelGerenciaScreen(navController = androidx.navigation.compose.rememberNavController())
    }
}
