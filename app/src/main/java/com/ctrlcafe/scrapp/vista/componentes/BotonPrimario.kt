package com.ctrlcafe.scrapp.vista.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctrlcafe.scrapp.ui.theme.AzulPrimario
import com.ctrlcafe.scrapp.ui.theme.SuperficieBlanca

@Composable
fun BotonPrimario(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp), // Altura estándar táctil recomendada
        enabled = enabled,
        shape = RoundedCornerShape(8.dp), // esto es el corner radio
        colors = ButtonDefaults.buttonColors(
            containerColor = AzulPrimario,
            contentColor = SuperficieBlanca,
            disabledContainerColor = Color.LightGray,
            disabledContentColor = Color.DarkGray
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
