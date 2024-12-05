package com.istomyang.edgetss.ui.main.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Picker(
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
            Picker(
                title = "Select Option",
                data = data,
                onSelected = {},
            )
            Picker(
                title = "Select Option2",
                data = data,
                onSelected = {},
            )
        }
    }
}


data class OptionItem(val name: String, val value: String, val icon: ImageVector? = null)

@Composable
fun OptionPicker(
    modifier: Modifier = Modifier,
    default: String,
    options: List<OptionItem>,
    onClick: (value: String) -> Unit
) {
    var expanded = remember { mutableStateOf(false) }
    var selected = remember { mutableStateOf<OptionItem?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded.value = false
                        selected.value = option
                        onClick(option.value)
                    },
                    leadingIcon = (if (option.icon != null) {
                        Icon(option.icon, contentDescription = null)
                    } else {
                        null
                    }) as @Composable (() -> Unit)?
                )
            }
        }
        Row(
            modifier = Modifier.clickable(onClick = { expanded.value = !expanded.value }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = selected.value?.name ?: default, fontStyle = MaterialTheme.typography.titleLarge.fontStyle)
            Icon(
                imageVector = if (expanded.value) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                contentDescription = null,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionPickerPreview() {
    Column(
        modifier = Modifier.size(300.dp)
    ) {
        OptionPicker(
            default = "Default",
            options = listOf(
                OptionItem("Option1", "value1"),
                OptionItem("Option2", "value2"),
                OptionItem("Option3", "value3"),
            ),
            onClick = {}
        )
    }
}