package com.istomyang.edgetss.ui.view.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerView(
    title: String,
    data: List<PickOption>,
    onSelected: (value: String) -> Unit,
    enable: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(PickOption("", "")) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (data.isNotEmpty()) {
                expanded = !expanded
            }
        }
    ) {
        TextField(
            enabled = enable,
            value = selected.title,
            onValueChange = { },
            readOnly = true,
            label = { Text(title) },
            trailingIcon = {
                if (!enable) {
                    return@TextField
                }
                if (data.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .scale(0.6f),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            data.map {
                DropdownMenuItem(
                    enabled = data.isNotEmpty(),
                    text = { Text(text = it.title) },
                    onClick = {
                        selected = it
                        expanded = false
                        onSelected(it.value)
                    }
                )
            }
        }
    }
}

data class PickOption(val title: String, val value: String)


@Preview(showBackground = true)
@Composable
private fun PickerViewPreview() {
    var data by remember { mutableStateOf(emptyList<PickOption>()) }

    LaunchedEffect(UInt) {
//        delay(1000L)
        data = (0..10).map {
            PickOption("Option $it", "$it")
        }
    }

    Box(Modifier.size(300.dp)) {
        Column {
            PickerView(
                title = "Select Option",
                data = data,
                onSelected = {},
            )
            PickerView(
                title = "Select Option2",
                data = data,
                onSelected = {},
            )
        }
    }
}