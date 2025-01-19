package com.istomyang.edgetss.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.istomyang.edgetss.data.LogLevel
import com.istomyang.edgetss.ui.main.component.IconButton2

/**
 * LogScreen is a top level [Screen] config for [MainContent].
 */
val LogScreen = Screen(title = "Log", icon = Icons.Filled.Description) { openDrawer ->
    LogContentView(openDrawer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogContentView(openDrawer: () -> Unit) {
    val viewModel: LogViewModel = viewModel(factory = LogViewModel.Factory)

    val lines by viewModel.linesUiState.collectAsStateWithLifecycle()
    val logOpened by viewModel.logOpened.collectAsStateWithLifecycle()
    val logDebugOpened by viewModel.logDebugOpened.collectAsStateWithLifecycle()

    LaunchedEffect(UInt) {
        viewModel.loadLogs()
        viewModel.collectLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(colors = topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ), title = {
                Text(text = "Log")
            }, navigationIcon = {
                IconButton2("Menu", Icons.Default.Menu) { openDrawer() }
            }, actions = {
                IconButton2(
                    "Open Log",
                    if (logOpened) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                    modifier = Modifier.size(48.dp),
                    tint = if (logOpened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                ) {
                    viewModel.openLog(!logOpened)
                }
                IconButton2(
                    "Open Debug",
                    Icons.Filled.BugReport,
                    modifier = Modifier.size(32.dp),
                    tint = if (logDebugOpened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                ) {
                    viewModel.openDebugLog(!logDebugOpened)
                }
            })
        }, bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton2("Clear", Icons.Default.CleaningServices) {
                        viewModel.clearLog()
                    }
                    FilterView(
                        options = listOf(
                            MenuOption("All", "all"),
                            MenuOption("Info", LogLevel.INFO.name),
                            MenuOption("Debug", LogLevel.DEBUG.name),
                            MenuOption("Error", LogLevel.ERROR.name),
                        )
                    ) {
                        viewModel.setLogLevel(it.value)
                    }
                }
            )
        }
    ) { innerPadding ->
        LogViewer(
            modifier = Modifier.padding(innerPadding),
            lines = lines
        )
    }
}

//@Preview(showBackground = true)
@Composable
private fun ContentViewPreview() {
    LogContentView(openDrawer = {})
}

@Composable
private fun LogViewer(modifier: Modifier = Modifier, lines: List<String>) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.size > 1) {
            lazyListState.animateScrollToItem(lines.size - 1)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        items(lines) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            )
        }
    }
}

//@Preview(showBackground = true)
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
private fun FilterView(
    options: List<MenuOption>,
    onSelected: (MenuOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        IconButton2("Level", Icons.Default.FilterAlt) {
            expanded = !expanded
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.title) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private data class MenuOption(val title: String, val value: String)