package com.istomyang.edgetss.ui.main

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.istomyang.edgetss.engine.EdgeTTSOutputFormat
import com.istomyang.edgetss.ui.main.component.IconButton
import kotlinx.coroutines.launch

/**
 * SpeakerScreen is a top level [Screen] config for [MainContent].
 */
val SpeakerScreen = Screen(title = "Speaker", icon = Icons.Filled.RecentActors) { openDrawer ->
    SpeakerContentView(openDrawer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakerContentView(openDrawer: () -> Unit) {
    val viewModel: SpeakerViewModel = viewModel(factory = SpeakerViewModel.Factory)

    val speakers by viewModel.speakerUiState.collectAsStateWithLifecycle()
    val voices by viewModel.voicesUiState.collectAsStateWithLifecycle()
    val message by viewModel.messageUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsUiState.collectAsStateWithLifecycle()

    var openPicker by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val editItems = remember { mutableStateListOf<String>() } // id

    var openSetting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        scope.launch {
            val message = message ?: return@launch
            val msg = if (message.error) {
                "Error: ${message.description}"
            } else {
                message.description
            }
            snackBarHostState.showSnackbar(message = msg)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackBarHostState)
        },
        topBar = {
            TopAppBar(colors = topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ), title = {
                Text(text = "Edge TSS")
            }, navigationIcon = {
                IconButton("Menu", Icons.Default.Menu) { openDrawer() }
            }, actions = {
                when (editMode) {
                    true -> {
                        Text("${editItems.size} selected")
                        IconButton(
                            "Delete", Icons.Filled.Delete
                        ) {
                            viewModel.removeSpeakers(editItems)
                        }
                        IconButton("Cancel", Icons.Filled.Close) {
                            editMode = false
                            editItems.clear()
                        }
                    }
                    false -> {
                        IconButton("Open Settings", Icons.Filled.Settings) {
                            openSetting = true
                        }
                        IconButton("Add Items", Icons.Filled.Add) {
                            openPicker = true
                            viewModel.loadVoices()
                        }
                        IconButton("Edit Items", Icons.Filled.Edit) {
                            editMode = true
                        }
                    }
                }
            })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(speakers) { speaker ->
                Item(speaker = speaker,
                    status = if (editMode) ItemStatus.EDIT else ItemStatus.VIEW,
                    onSelected = { id ->
                        when (editMode) {
                            true -> {
                                if (editItems.contains(id)) {
                                    editItems.remove(id)
                                } else {
                                    editItems.add(id)
                                }
                            }
                            false -> {
                                viewModel.setActiveSpeaker(id)
                            }
                        }
                    })
            }
        }
    }

    if (openPicker) {
        SpeakerPicker(
            data = voices,
            onConfirm = { ids ->
                openPicker = false
                viewModel.addSpeakers(ids)
            },
            onCancel = {
                openPicker = false
            }
        )
    }

    if (openSetting) {
        Settings(
            defaultValue = settings,
            onConfirm = {
                openSetting = false
                viewModel.setAudioFormat(it.format)
                viewModel.setUseFlow(it.useFlow)
            },
            onCancel = {
                openSetting = false
            }
        )
    }
}

//@Preview(showBackground = true)
@Composable
private fun ContentViewPreview() {
    SpeakerContentView {}
}

// region ListItem

private enum class ItemStatus {
    VIEW, EDIT
}

@Composable
private fun Item(speaker: Speaker, status: ItemStatus, onSelected: (id: String) -> Unit) {
    var selected by remember { mutableStateOf(false) }
    var preMode by remember { mutableStateOf(status) }

    if (preMode != status) {
        preMode = status
        selected = false
    }

    ListItem(modifier = Modifier.clickable {
        selected = !selected
        onSelected(speaker.id)
    },
        headlineContent = { Text(speaker.name) },
        supportingContent = { Text(speaker.description) },
        trailingContent = { Text(speaker.locale) },
        leadingContent = {
            when (status) {
                ItemStatus.VIEW -> {
                    Icon(
                        if (speaker.active) Icons.Filled.Check else if (speaker.gender == "Male") Icons.Filled.Male else Icons.Filled.Female,
                        contentDescription = "",
                        tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                ItemStatus.EDIT -> {
                    Icon(
                        if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        })
}

// endregion

// region SpeakerPicker

data class Option(val title: String, val value: String, val searchKey: String)

@Composable
private fun SpeakerPicker(
    modifier: Modifier = Modifier,
    data: List<Option>,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit = {},
) {
    var selected by remember { mutableStateOf(emptyList<String>()) }
    var search by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<Option>()) }

    if (data.isNotEmpty() && candidates.isEmpty()) {
        candidates = data
    }

    Dialog(onDismissRequest = { onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Select Speaker",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )

                HorizontalDivider()

                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    singleLine = true,
                    value = search,
                    onValueChange = {
                        search = it
                        if (search.isEmpty()) {
                            candidates = data
                            return@TextField
                        }
                        val searches = search.split(" ")
                        candidates = data.filter { v ->
                            for (search in searches) {
                                val contain = v.searchKey.contains(search, ignoreCase = true)
                                if (!contain) {
                                    return@filter false
                                }
                            }
                            return@filter true
                        }
                    },
                    leadingIcon = {
                        if (data.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        } else {
                            Image(imageVector = Icons.Filled.Search, contentDescription = "")
                        }
                    },
                )

                LazyColumn(modifier = modifier.heightIn(max = 400.dp)) {
                    items(items = candidates) { voice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = true,
                                    onClick = {
                                        selected = if (selected.contains(voice.value)) {
                                            selected - voice.value
                                        } else {
                                            selected + voice.value
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(voice.value),
                                onCheckedChange = null
                            )
                            Text(
                                text = voice.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            onConfirm(selected)
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}


//@Preview(showBackground = true)
@Composable
private fun SpeakerPickerPreview() {
    SpeakerPicker(
        data = (0..300).map {
            Option(
                title = "Microsoft Server Speech Text to Speech Voice (en-US, AvaMultilingualNeural) $it",
                value = "speaker_$it",
                searchKey = ""
            )
        }, onConfirm = { it ->
            Log.d("SpeakerPicker", "Confirm $it")
        }, onCancel = {}
    )
}

// endregion

// region Setting


data class SettingsData(
    val format: String,
    val useFlow: Boolean
)

@Composable
private fun Settings(
    modifier: Modifier = Modifier,
    defaultValue: SettingsData,
    onConfirm: (SettingsData) -> Unit,
    onCancel: () -> Unit = {},
) {
    var format by remember { mutableStateOf(defaultValue.format) }
    var useFlow by remember { mutableStateOf(defaultValue.useFlow) }

    Dialog(onDismissRequest = { onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Setting",
                    style = MaterialTheme.typography.headlineSmall,
                )
                HorizontalDivider()

                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Text(
                        "TTS Audio Format",
                        style = MaterialTheme.typography.titleMedium
                    )

                    listOf(
                        EdgeTTSOutputFormat.Audio24Khz48KbitrateMonoMp3,
                        EdgeTTSOutputFormat.Audio24Khz96KbitrateMonoMp3,
                        EdgeTTSOutputFormat.Webm24Khz16BitMonoOpus,
                    ).forEach {
                        val value = it.value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = true,
                                    onClick = {
                                        format = value
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = value == format,
                                onCheckedChange = null
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Use Flow",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Start playing audio after a while of downloading data.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .width(200.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1.0f))
                        Switch(
                            modifier = Modifier
                                .padding(start = 5.dp),
                            checked = useFlow,
                            onCheckedChange = { useFlow = it })
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.weight(1.0f))

                    TextButton(
                        onClick = { onCancel() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Cancel")
                    }

                    TextButton(
                        onClick = {
                            val data = SettingsData(
                                format = format,
                                useFlow = useFlow
                            )
                            onConfirm(data)
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingPreview() {
    Settings(defaultValue = SettingsData("", false), onConfirm = {}, onCancel = {})
}

// endregion