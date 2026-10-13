package com.ctrlcafe.scrapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// --- ESQUEMA OSCURO ---
private val DarkColorScheme = darkColorScheme(
    primary = AzulVariante,
    secondary = AzulPrimario,
    background = TextoPrincipal, // Invierte fondos para modo oscuro
    surface = TextoPrincipal,
    error = AlertaCritica
)

// --- ESQUEMA CLARO (FIEL A TU FIGMA) ---
private val LightColorScheme = lightColorScheme(
    primary = AzulPrimario,
    secondary = AzulVariante,
    background = FondoGris,
    surface = SuperficieBlanca,
    error = AlertaCritica,
    onPrimary = SuperficieBlanca,
    onSecondary = SuperficieBlanca,
    onBackground = TextoPrincipal,
    onSurface = TextoPrincipal
)

@Composable
fun ScrappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // El diseño dinámico es opcional en Material 3 (Android 12+). Si se activa, toma colores del fondo del teléfono.
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
        typography = Typography, // Vincula las fuentes Roboto pues es la que ocupe en figma
        content = content
    )
}
