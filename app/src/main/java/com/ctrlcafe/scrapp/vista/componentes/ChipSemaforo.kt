package com.ctrlcafe.scrapp.vista.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctrlcafe.scrapp.ui.theme.AlertaBaja
import com.ctrlcafe.scrapp.ui.theme.AlertaCritica
import com.ctrlcafe.scrapp.ui.theme.AlertaMedia

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

    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = estado,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
