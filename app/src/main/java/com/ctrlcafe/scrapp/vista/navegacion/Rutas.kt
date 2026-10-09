package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Rutas(val route: String) {
    object Splash : Rutas("splash") // <-- Agregado con éxito
    object Login : Rutas("login")
}

sealed class RutasBottom(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Produccion : RutasBottom("dashboard_produccion", "Producción", Icons.Default.Layers)
    object Lotes : RutasBottom("dashboard_lotes", "Lotes", Icons.Default.Assignment)
    object Mermas : RutasBottom("dashboard_mermas", "Mermas", Icons.Default.History)
    object Gerencia : RutasBottom("dashboard_admin", "Gerencia", Icons.Default.Analytics)
}
