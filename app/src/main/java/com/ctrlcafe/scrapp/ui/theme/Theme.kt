package com.ctrlcafe.scrapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),  // Badges, chips y etiquetas del semáforo
    medium = RoundedCornerShape(14.dp), // Campos de texto (Inputs) y botones
    large = RoundedCornerShape(22.dp)   // Tarjetas principales y cuadros de diálogo
)

// --- MODO OSCURO---
private val DarkColorScheme = darkColorScheme(
    primary = AzulVariante,
    secondary = AzulPrimario,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
    error = AlertaCritica,
    onPrimary = SuperficieBlanca,
    onSecondary = SuperficieBlanca,
    onBackground = SuperficieBlanca,
    onSurface = SuperficieBlanca,
    onSurfaceVariant = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF334155)    // Bordes sutiles en modo oscuro
)

// --- MODO CLARO ---
private val LightColorScheme = lightColorScheme(
    primary = AzulPrimario,
    secondary = AzulVariante,
    background = FondoGris,
    surface = SuperficieBlanca,
    surfaceVariant = Color(0xFFF3F4F6),
    error = AlertaCritica,
    onPrimary = SuperficieBlanca,
    onSecondary = SuperficieBlanca,
    onBackground = TextoPrincipal,
    onSurface = TextoPrincipal,
    onSurfaceVariant = TextoSecundario,
    outlineVariant = Color(0xFFE5E7EB)
)

@Composable
fun ScrappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),

    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
