package com.ctrlcafe.scrapp.vista.componentes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MiniaturaEvidencia(
    urlImagen: String?,
    onEliminarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!urlImagen.isNullOrEmpty()) {
        Box(
            modifier = modifier
                .size(90.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = urlImagen,
                contentDescription = "Evidencia física de merma",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onEliminarClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Remover foto",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
