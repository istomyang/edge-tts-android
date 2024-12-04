package com.istomyang.edgetss.ui.view.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun IconButton(name: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = {
        onClick()
    }) {
        Icon(
            imageVector = icon,
            contentDescription = name
        )
    }
}