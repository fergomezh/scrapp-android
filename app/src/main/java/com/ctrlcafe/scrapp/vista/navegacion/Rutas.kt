package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Rutas(val route: String) {
    object Splash : Rutas("splash")
    object Login : Rutas("login")
    object Registro : Rutas("registro")
    object IngresoLote : Rutas("ingreso_lote")

    // --- NUEVAS RUTAS ESPECÍFICAS DE WILLIAM (AVANCE 2) ---
    object RecuperarContrasena : Rutas("recuperar_contrasena")
    object CamaraEvidencia : Rutas("camara_evidencia")
    object ConfigurarMotores : Rutas("configurar_motores")

    // Mantiene intacto el flujo parametrizado existente del equipo
    object RegistroMerma : Rutas("registro_merma/{productoId}/{loteId}") {
        const val ARG_PRODUCTO_ID = "productoId"
        const val ARG_LOTE_ID = "loteId"
        fun conLote(productoId: String, loteId: String) = "registro_merma/$productoId/$loteId"
    }
}

sealed class RutasBottom(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // Pestañas Compartidas Existentes y Nueva Pestaña Historial
    object Produccion : RutasBottom("dashboard_produccion", "Producción", Icons.Default.Layers)
    object Lotes : RutasBottom("dashboard_lotes", "Lotes", Icons.AutoMirrored.Filled.Assignment)
    object Mermas : RutasBottom("dashboard_mermas", "Mermas", Icons.Default.History)
    object Historial : RutasBottom("historial_general", "Historial", Icons.Default.ListAlt) // <-- Agregado para ambos roles

    // Menú de Administración exclusivo para Admin
    object Gerencia : RutasBottom("dashboard_admin", "Gerencia", Icons.Default.Analytics)
    object GestionUsuarios : RutasBottom("gestion_usuarios", "Usuarios", Icons.Default.People) // <-- Agregado para Admin
    object CatalogoProductos : RutasBottom("catalogo_productos", "Catálogo", Icons.Default.LocalCafe) // <-- Agregado para Admin
}
