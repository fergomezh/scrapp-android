package com.ctrlcafe.scrapp.vista.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctrlcafe.scrapp.modelo.EstadoCaducidad
import com.ctrlcafe.scrapp.ui.theme.AlertaBaja
import com.ctrlcafe.scrapp.ui.theme.AlertaCritica
import com.ctrlcafe.scrapp.ui.theme.AlertaMedia
import com.ctrlcafe.scrapp.ui.theme.TextoPrincipal

@Composable
fun ChipSemaforo(
    estado: String, // "Bajo", "Medio", "Crítico" o equivalente
    modifier: Modifier = Modifier
) {
    // Determinamos los colores de fondo y texto según el estado de la merma o lote
    val (backgroundColor, textColor) = when (estado.lowercase().trim()) {
        "bajo", "controlado" -> Pair(AlertaBaja.copy(alpha = 0.15f), AlertaBaja)
        "medio", "advertencia" -> Pair(AlertaMedia.copy(alpha = 0.15f), AlertaMedia)
        "crítico", "critico", "alto" -> Pair(AlertaCritica.copy(alpha = 0.15f), AlertaCritica)
        else -> Pair(Color.LightGray.copy(alpha = 0.2f), Color.DarkGray)
    }

    ChipBase(texto = estado, colorFondo = backgroundColor, colorTexto = textColor, modifier = modifier)
}

/** Variante para el semáforo de caducidad de lotes (Verde, Amarillo, Rojo, Vencido). */
@Composable
fun ChipSemaforo(
    estado: EstadoCaducidad,
    modifier: Modifier = Modifier
) {
    when (estado) {
        is EstadoCaducidad.Vigente ->
            ChipBase("Verde", AlertaBaja.copy(alpha = 0.15f), AlertaBaja, modifier, Icons.Default.CheckCircle)
        is EstadoCaducidad.ProximoAVencer ->
            ChipBase("Amarillo", AlertaMedia.copy(alpha = 0.15f), AlertaMedia, modifier, Icons.Default.Schedule)
        is EstadoCaducidad.Critico ->
            ChipBase("Rojo", AlertaCritica.copy(alpha = 0.15f), AlertaCritica, modifier, Icons.Default.ReportProblem)
        is EstadoCaducidad.Vencido ->
            ChipBase("Vencido", TextoPrincipal, Color.White, modifier, Icons.Default.Error)
    }
}

@Composable
private fun ChipBase(
    texto: String,
    colorFondo: Color,
    colorTexto: Color,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null
) {
    Row(
        modifier = modifier
            .background(color = colorFondo, shape = RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icono != null) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = colorTexto,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = texto,
            color = colorTexto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
