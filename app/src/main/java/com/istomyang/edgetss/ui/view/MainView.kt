package com.istomyang.edgetss.ui.view

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.istomyang.edgetss.ui.view.component.IconButton
import com.istomyang.edgetss.ui.view.component.PickOption
import com.istomyang.edgetss.ui.view.component.PickerView


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView() {
    val viewModel = LocalMainViewModel.current

    val speakers by viewModel.speakers.collectAsState()

    var addMode by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val editIds = remember { mutableStateListOf<Int>() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(text = "Edge TSS")
                },
                actions = {
                    when (editMode) {
                        true -> {
                            Text("${editIds.size} selected")
                            IconButton(
                                "Delete",
                                Icons.Filled.Delete
                            ) { viewModel.deleteSelectedSpeakerIds(editIds) }
                            IconButton(
                                "Cancel",
                                Icons.Filled.Close
                            ) {
                                editMode = false
                                editIds.clear()
                            }
                        }

                        false -> {
                            IconButton("Add Items", Icons.Filled.Add) {
                                addMode = true
                            }
                            IconButton("Edit Items", Icons.Filled.Edit) {
                                editMode = true
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            speakers.forEach { speaker ->
                SpeakerItem(
                    speaker = speaker,
                    mode = if (editMode) ItemMode.EDIT else ItemMode.VIEW,
                    onSelected = { id ->
                        when (editMode) {
                            true -> {
                                if (editIds.contains(id)) {
                                    editIds.remove(id)
                                } else {
                                    editIds.add(id)
                                }
                            }

                            false -> {
                                viewModel.setActiveSpeakerId(id)
                            }
                        }
                    }
                )
            }
        }
    }

    if (addMode) {
        AddView(onConfirm = { id ->
            addMode = false
            viewModel.addSpeaker(id)
        }, onCancel = {
            addMode = false
        })
    }
}

@Preview(showBackground = true)
@Composable
fun MainViewPreview() {
    MainView()
}

enum class ItemMode {
    VIEW, EDIT
}

@Composable
private fun SpeakerItem(
    speaker: Speaker,
    mode: ItemMode,
    onSelected: (id: Int) -> Unit
) {
    var selected by remember { mutableStateOf(false) }
    var preMode by remember { mutableStateOf(mode) }

    if (preMode != mode) {
        preMode = mode
        selected = false
    }

    ListItem(
        modifier = Modifier
            .clickable {
                selected = !selected
                onSelected(speaker.id)
            },
        headlineContent = { Text(speaker.name) },
        supportingContent = { Text(speaker.description) },
        trailingContent = { Text(speaker.locale) },
        leadingContent = {
            when (mode) {
                ItemMode.VIEW -> {
                    Icon(
                        if (speaker.active) Icons.Filled.Check else if (speaker.gender == "Male") Icons.Filled.Male else Icons.Filled.Female,
                        contentDescription = "",
                        tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                ItemMode.EDIT -> {
                    Icon(
                        if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
private fun AddView(
    onConfirm: (id: Int) -> Unit,
    onCancel: () -> Unit = {},
) {
    val viewModel = LocalMainViewModel.current

    var lang by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var speaker by remember { mutableStateOf("") }

    var languages by remember { mutableStateOf(emptyList<String>()) }
    var countries by remember { mutableStateOf(emptyList<String>()) }
    var voices by remember { mutableStateOf(emptyList<VoiceInfo>()) }

    if (languages.isEmpty()) {
        viewModel.fetchLanguages { languages = it }
    }
    if (lang != "") {
        viewModel.fetchCountries(lang) { countries = it }
    }
    if (country != "") {
        viewModel.fetchVoices("$lang-$country") { voices = it }
    }

    Dialog(
        onDismissRequest = { onCancel() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Select Speaker",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )

                PickerView(
                    title = "Language",
                    data = languages.map { PickOption(it, it) },
                    onSelected = { lang = it },
                    enable = true
                )

                PickerView(
                    title = "Country",
                    data = countries.map { PickOption(it, it) },
                    onSelected = { country = it },
                    enable = lang != "",
                )

                PickerView(
                    title = "Speaker",
                    data = voices.map { PickOption(it.title, it.id.toString()) },
                    onSelected = { speaker = it },
                    enable = country != "",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { onCancel() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.weight(1.0f))

                    TextButton(
                        onClick = {
                            if (speaker == "") {
                                Toast.makeText(
                                    viewModel.getApplication(),
                                    "Please select speaker",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@TextButton
                            }
                            onConfirm(speaker.toInt())
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun EditorViewPreview() {
    AddView(
        onConfirm = {})
}


@Preview(showBackground = true)
@Composable
private fun ItemViewPreview() {
    Column {
        ListItem(
            headlineContent = { Text("One line list item with 24x24 icon") },
            leadingContent = {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Localized description",
                )
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Three line list item") },
            supportingContent = {
                Text("Secondary text that is long and perhaps goes onto another line")
            },
            leadingContent = {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Localized description",
                )
            },
            trailingContent = { Text("meta") }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Two line list item with trailing") },
            supportingContent = { Text("Secondary text") },
            trailingContent = { Text("meta") },
            leadingContent = {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Localized description",
                )
            }
        )
    }
}

