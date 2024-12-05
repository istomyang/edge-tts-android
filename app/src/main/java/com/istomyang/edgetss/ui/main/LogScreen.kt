package com.istomyang.edgetss.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istomyang.edgetss.data.LogLevel
import com.istomyang.edgetss.data.LogRepository
import com.istomyang.edgetss.data.PreferenceRepository
import com.istomyang.edgetss.ui.main.component.IconButton
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SpeakerScreen is a top level [Screen] config for [MainContent].
 */
val LogScreen = Screen(title = "Log", icon = Icons.Filled.Description) { openDrawer ->
    ContentView(openDrawer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentView(openDrawer: () -> Unit) {
    val viewModel = LogViewModel(LocalContext.current)

    var level = remember { mutableStateOf(LogLevel.INFO) }
    var domain = remember { mutableStateOf("") }

    var openSetting = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { // Unit 表示该 Effect 仅在第一次组合时启动
        while (true) {
            viewModel.fetchLog(domain = domain.value, level = level.value)
            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(colors = topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ), title = {
                Text(text = "Log")
            }, navigationIcon = {
                IconButton("Menu", Icons.Default.Menu) { openDrawer() }
            }, actions = {
                IconButton("Setting", Icons.Filled.Settings) {

                }
            })
        }, bottomBar = {
            BottomAppBar(
                actions = {
                    DurationIconMenu { ts ->
                        viewModel.cleanLog(beforeAt = ts)
                    }
                    LogFilter(onChangeLevel = { level.value = it }, onDomain = { domain.value = it })
                }
            )
        }
    ) { innerPadding ->
        LogViewer(
            modifier = Modifier.padding(innerPadding),
            lines = viewModel.lines.value
        )
    }

    if (openSetting.value) {
        SettingDialog(viewModel) {
            openSetting.value = false
        }
    }
}

@Composable
private fun SettingDialog(
    viewModel: LogViewModel,
    onDismiss: () -> Unit
) {
    var checked = remember { mutableStateOf(false) }

    data class SaveTimeOption(val name: String, val value: Int)

    val saveTimeOptions = listOf(
        SaveTimeOption("1 Day", 1),
        SaveTimeOption("7 Day", 7),
        SaveTimeOption("30 Day", 30),
    )

    var saveTimeSelect = remember { mutableIntStateOf(saveTimeOptions[0].value) }

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column {
                Text(text = "Log Settings", style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()

                ListItem(headlineContent = { Text("Open Log") }, trailingContent = {
                    Switch(
                        checked = checked.value,
                        onCheckedChange = {
                            viewModel.openLog(checked.value)
                        }
                    )
                })

                Text("Save Time", style = MaterialTheme.typography.headlineLarge)

                saveTimeOptions.forEach { opt ->
                    RadioButton(
                        selected = opt.value == saveTimeSelect.intValue,
                        onClick = {
                            saveTimeSelect.intValue = opt.value
                            viewModel.saveLogTime(saveTimeSelect.intValue)
                        },
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun SettingDialogPreview() {
    SettingDialog(viewModel = LogViewModel(LocalContext.current), onDismiss = {})
}


@Composable
private fun LogViewer(modifier: Modifier = Modifier, lines: List<String>) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        lazyListState.animateScrollToItem(lines.size - 1)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
    ) {
        items(lines) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogViewerPreview() {
    val lines = (0..1000).map {
        "2023-01-01 00:00:00.000 [INFO] [Log] Hello, world!"
    }

    Column(
        modifier = Modifier
            .height(300.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogViewer(lines = lines)
    }
}

@Composable
private fun DurationIconMenu(
    onClient: (ts: Long) -> Unit
) {
    var expanded = remember { mutableStateOf(false) }

    data class DurationOption(
        val name: String,
        val value: Int,
    )

    val durationOptions = listOf(
        DurationOption("Before 1 minute", 1),
        DurationOption("Before 5 minutes", 5),
        DurationOption("Before 30 minutes", 30),
        DurationOption("Before 24 hours", 24 * 60),
    )

    var selected = remember { mutableIntStateOf(0) }

    Column {
        DropdownMenu(
            modifier = Modifier.padding(5.dp),
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }) {
            durationOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.name) },
                    onClick = {
                        expanded.value = false
                        onClient(System.currentTimeMillis() - option.value * 60 * 1000)
                    },
                    leadingIcon = {
                        Icon(
                            if (selected.intValue == option.value) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null
                        )
                    }
                )
            }
        }
        IconButton("Clean", Icons.Filled.CleaningServices) {
            expanded.value = true
        }
    }
}

@Composable
private fun LogFilter(
    onChangeLevel: (level: LogLevel) -> Unit,
    onDomain: (domain: String) -> Unit
) {
    var expanded = remember { mutableStateOf(false) }

    data class LevelOption(
        val name: String,
        val value: LogLevel,
    )

    data class DomainOption(
        val name: String,
        val value: String,
    )

    val levelOptions = listOf(
        LevelOption("INFO", LogLevel.INFO),
        LevelOption("DEBUG", LogLevel.DEBUG),
        LevelOption("ERROR", LogLevel.ERROR),
        LevelOption("WARN", LogLevel.WARNING),
    )
    val domainOptions = listOf(
        DomainOption("All", "all"),
        DomainOption("Speech", "service/edge-tts/speech"),
    )

    var selectedLevel = remember { mutableStateOf(levelOptions[0].value) }
    var selectedDomain = remember { mutableStateOf(domainOptions[0].value) }

    @Composable
    fun <T> OptionBuilder(name: String, value: T, active: T, onClick: (value: T) -> Unit) {
        DropdownMenuItem(
            text = { Text(name) },
            onClick = { onClick(value) },
            leadingIcon = {
                Icon(
                    if (active == value) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null
                )
            }
        )
    }

    Column {
        DropdownMenu(
            modifier = Modifier.padding(5.dp),
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }) {
            Text("Log Level:", fontStyle = MaterialTheme.typography.headlineSmall.fontStyle)
            levelOptions.forEach { option ->
                OptionBuilder<LogLevel>(option.name, option.value, selectedLevel.value) {
                    expanded.value = false
                    selectedLevel.value = option.value
                    onChangeLevel(option.value)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
            Text("Domain:", fontStyle = MaterialTheme.typography.headlineSmall.fontStyle)
            domainOptions.forEach { option ->
                OptionBuilder<String>(option.name, option.value, selectedDomain.value) {
                    expanded.value = false
                    selectedDomain.value = option.value
                    onDomain(option.value)
                }
            }
        }
        IconButton("Setting", Icons.Filled.FilterAlt) {
            expanded.value = true
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogFilterPreview() {
    Column(
        modifier = Modifier.size(300.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogFilter(onChangeLevel = {}, onDomain = {})
    }
}

private class LogViewModel(context: Context) : ViewModel() {
    val logRepository = LogRepository.create(context)
    val preferenceRepository = PreferenceRepository.create(context)

    var lines = mutableStateOf(listOf<String>())

    fun fetchLog(domain: String, level: LogLevel) {
        viewModelScope.launch {
            val logs = logRepository.query(domain, level, 0, 1, 100)
            lines.value = logs.map {
                "${ts2DateTime(it.createdAt)} [${it.level}]([${it.domain}]): ${it.message}"
            }
        }
    }

    fun cleanLog(beforeAt: Long) {
        viewModelScope.launch {
            logRepository.delete(beforeAt = beforeAt)
        }
    }

    private fun ts2DateTime(ts: Long): String {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(ts),
            ZoneId.systemDefault()
        ).format(
            DateTimeFormatter.ofPattern("MM-dd-HH:mm:ss")
        )
    }

    var logOpened = mutableStateOf(false)
    var logSaveTime = mutableIntStateOf(1)

    fun openLog(b: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setLogOpen(b)
        }
    }

    fun saveLogTime(day: Int) {
        viewModelScope.launch {
            preferenceRepository.setLogSaveTime(day)
        }
    }

    init {
        viewModelScope.launch {
            preferenceRepository.logOpen.collect {
                logOpened.value = it == true
            }
            preferenceRepository.logSaveTime.collect {
                logSaveTime.intValue = it ?: 1
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}