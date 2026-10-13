package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Rutas(val route: String) {
    object Splash : Rutas("splash") // <-- Agregado con éxito
    object Login : Rutas("login")
    object Registro : Rutas("registro")
    object IngresoLote : Rutas("ingreso_lote")

    // Se abre desde el Monitor de Lotes con el producto ya elegido
    object RegistroMerma : Rutas("registro_merma/{productoId}") {
        const val ARG_PRODUCTO_ID = "productoId"
        fun conProducto(productoId: String) = "registro_merma/$productoId"
    }
}

sealed class RutasBottom(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Produccion : RutasBottom("dashboard_produccion", "Producción", Icons.Default.Layers)
    object Lotes : RutasBottom("dashboard_lotes", "Lotes", Icons.AutoMirrored.Filled.Assignment)
    object Mermas : RutasBottom("dashboard_mermas", "Mermas", Icons.Default.History)
    object Gerencia : RutasBottom("dashboard_admin", "Gerencia", Icons.Default.Analytics)
}
