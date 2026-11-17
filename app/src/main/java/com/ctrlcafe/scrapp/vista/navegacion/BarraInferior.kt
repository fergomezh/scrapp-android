package com.ctrlcafe.scrapp.vista.navegacion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ctrlcafe.scrapp.ui.theme.AzulContenedor
import com.ctrlcafe.scrapp.ui.theme.AzulPrimario
import com.ctrlcafe.scrapp.ui.theme.BordeSutil
import com.ctrlcafe.scrapp.ui.theme.SuperficieBlanca
import com.ctrlcafe.scrapp.ui.theme.TextoSecundario

@Composable
fun BarraInferior(navController: NavHostController, esAdministrador: Boolean) {
    val itemsBase = listOf(
        RutasBottom.Produccion,
        RutasBottom.Lotes,
        RutasBottom.Mermas,
        RutasBottom.Historial
    )

    // Agrega Gerencia solo si el rol del usuario es Administrador
    val itemsMenu = if (esAdministrador) itemsBase + RutasBottom.Gerencia else itemsBase

    Column {
        // Línea divisoria
        HorizontalDivider(
            thickness = 1.dp,
            color = BordeSutil
        )

        NavigationBar(
            containerColor = SuperficieBlanca,
            tonalElevation = 0.dp
        ) {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry.value?.destination?.route

            itemsMenu.forEach { item ->
                val seleccionado = currentRoute == item.route

                // Selección de ícono
                val iconoModerno = when (item) {
                    RutasBottom.Produccion -> if (seleccionado) Icons.Rounded.Layers else Icons.Outlined.Layers
                    RutasBottom.Lotes -> if (seleccionado) Icons.Rounded.Inventory2 else Icons.Outlined.Inventory2
                    RutasBottom.Mermas -> if (seleccionado) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Outlined.TrendingDown
                    RutasBottom.Historial -> if (seleccionado) Icons.Rounded.History else Icons.Outlined.History
                    RutasBottom.Gerencia -> if (seleccionado) Icons.Rounded.Analytics else Icons.Outlined.Analytics
                    else -> item.icon
                }

                NavigationBarItem(
                    selected = seleccionado,
                    onClick = {
                        if (!seleccionado) {
                            navegarAPestana(navController, item.route)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = iconoModerno,
                            contentDescription = item.title,
                            modifier = Modifier.size(if (seleccionado) 24.dp else 22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (esAdministrador) 10.sp else 11.sp,
                                fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AzulPrimario,
                        selectedTextColor = AzulPrimario,
                        indicatorColor = AzulContenedor,
                        unselectedIconColor = TextoSecundario,
                        unselectedTextColor = TextoSecundario
                    )
                )
            }
        }
    }
}


fun navegarAPestana(navController: NavHostController, ruta: String) {
    navController.navigate(ruta) {
        popUpTo(RutasBottom.Produccion.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
