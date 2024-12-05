package com.istomyang.edgetss.ui.main

import android.content.Context
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
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istomyang.edgetss.data.PreferenceRepository
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.data.database.Voice
import com.istomyang.edgetss.ui.main.component.IconButton
import com.istomyang.edgetss.ui.main.component.PickOption
import com.istomyang.edgetss.ui.main.component.Picker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SpeakerScreen is a top level [Screen] config for [MainContent].
 */
val SpeakerScreen = Screen(title = "Speaker", icon = Icons.Filled.RecentActors) { openDrawer ->
    ContentView(openDrawer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentView(openDrawer: () -> Unit) {
    val viewModel = SpeakerViewModel(LocalContext.current)

    val speakers by viewModel.speakers.collectAsState()

    var openPicker by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val editIds = remember { mutableStateListOf<Int>() }

    Scaffold(
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
                        Text("${editIds.size} selected")
                        IconButton(
                            "Delete", Icons.Filled.Delete
                        ) { viewModel.deleteSelectedSpeakerIds(editIds) }
                        IconButton("Cancel", Icons.Filled.Close) {
                            editMode = false
                            editIds.clear()
                        }
                    }

                    false -> {
                        IconButton("Add Items", Icons.Filled.Add) {
                            openPicker = true
                        }
                        IconButton("Edit Items", Icons.Filled.Edit) {
                            editMode = true
                        }
                    }
                }
            })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            speakers.forEach { speaker ->
                Item(speaker = speaker,
                    status = if (editMode) ItemStatus.EDIT else ItemStatus.VIEW,
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
                    })
            }
        }
    }

    if (openPicker) {
        SpeakerPicker(viewModel, onConfirm = { id ->
            openPicker = false
            viewModel.addSpeaker(id)
        }, onCancel = {
            openPicker = false
        })
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentViewPreview() {
    ContentView {}
}

// region ListItem

private enum class ItemStatus {
    VIEW, EDIT
}

@Composable
private fun Item(
    speaker: Speaker, status: ItemStatus, onSelected: (id: Int) -> Unit,
) {
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

@Composable
private fun SpeakerPicker(
    viewModel: SpeakerViewModel,
    onConfirm: (id: Int) -> Unit,
    onCancel: () -> Unit = {},
) {
    val ctx = LocalContext.current
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

    Dialog(onDismissRequest = { onCancel() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Select Speaker",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )

                Picker(
                    title = "Language",
                    data = languages.map { PickOption(it, it) },
                    onSelected = { lang = it },
                    enable = true
                )

                Picker(
                    title = "Country",
                    data = countries.map { PickOption(it, it) },
                    onSelected = { country = it },
                    enable = lang != "",
                )

                Picker(
                    title = "Speaker",
                    data = voices.map { PickOption(it.title, it.id.toString()) },
                    onSelected = { speaker = it },
                    enable = country != "",
                )

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
                            if (speaker == "") {
                                Toast.makeText(ctx, "Please select speaker", Toast.LENGTH_SHORT)
                                    .show()
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
private fun SpeakerPickerPreview() {
}

// endregion

// region SpeakerViewModel

private class SpeakerViewModel(context: Context) : ViewModel() {
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.create(context)
    private val speakerRepository: SpeakerRepository = SpeakerRepository.create(context)

    var speakers = MutableStateFlow<List<Speaker>>(emptyList())

    fun fetchLanguages(cb: (data: List<String>) -> Unit) {
        viewModelScope.launch {
            val data = speakerRepository.queryLocales().map {
                it.split("-").first()
            }.distinct()
            cb(data)
        }
    }

    fun fetchCountries(lang: String, cb: (data: List<String>) -> Unit) {
        viewModelScope.launch {
            val data = speakerRepository.queryLocales().filter { it.startsWith(lang) }
                .map { it.split("-").last() }.distinct()
            cb(data)
        }
    }

    fun fetchVoices(locale: String, cb: (data: List<VoiceInfo>) -> Unit) {
        viewModelScope.launch {
            val data = speakerRepository.query(locale).map { VoiceInfo.from(it) }
            cb(data)
        }
    }


    fun addSpeaker(id: Int) {
        viewModelScope.launch {
            preferenceRepository.addSelectedSpeakerId(id)
        }
    }

    fun setActiveSpeakerId(id: Int?) {
        viewModelScope.launch {
            preferenceRepository.setActiveSpeakerId(id)
        }
    }

    fun deleteSelectedSpeakerIds(ids: List<Int>) {
        viewModelScope.launch {
            val activeId = preferenceRepository.activeSpeakerId.first()
            if (ids.any { it == activeId }) {
                setActiveSpeakerId(null)
            }
            val undeleted = speakers.value.filter { !ids.contains(it.id) }.map { it.id }
            preferenceRepository.setSelectedSpeakerIds(undeleted)
        }
    }

    fun ensureLocalInitialized() {
        CoroutineScope(Dispatchers.IO).launch {
            speakerRepository.ensureLocalInitialized()
        }
    }

    init {
        ensureLocalInitialized()
        viewModelScope.launch {
            preferenceRepository.selectedSpeakerIds.collect { ids ->
                if (ids.isNotEmpty()) {
                    val active = preferenceRepository.activeSpeakerId.first()
                    speakers.value = speakerRepository.getByIds(ids).map {
                        val speaker = Speaker.from(it)
                        speaker.active = speaker.id == active
                        speaker
                    }
                }
            }
            preferenceRepository.activeSpeakerId.collect { id ->
                if (id == null) {
                    return@collect
                }
                for (speaker in speakers.value) {
                    speaker.active = speaker.id == id
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}

data class Speaker(
    val id: Int,
    val name: String,
    val gender: String,
    val locale: String,
    val description: String,
    var active: Boolean = false,
) {
    companion object {
        fun from(voice: Voice): Speaker {
            val description = voice.voicePersonalities + voice.contentCategories
            return Speaker(
                id = voice.uid,
                name = extractVoiceName(voice),
                gender = voice.gender,
                locale = voice.locale,
                description = description,
            )
        }
    }
}

data class VoiceInfo(val title: String, val id: Int) {
    companion object {
        fun from(voice: Voice): VoiceInfo {
            val name = extractVoiceName(voice)
            val gender = voice.gender
            val description = voice.contentCategories
            return VoiceInfo("$name - $gender - $description", voice.uid)
        }
    }
}

// en-US-AvaMultilingualNeural 提取 AvaMultilingualNeural 删除 Neural
private fun extractVoiceName(voice: Voice) = voice.shortName.split('-').last().replace("Neural", "")

// endregion



