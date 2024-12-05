package com.istomyang.edgetss.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


/**
 * Screen defines a item for [ModalNavigationDrawer] in [MainContent].
 */
data class Screen(
    val title: String,
    val icon: ImageVector,
    val makeContent: @Composable (openDrawer: () -> Unit) -> Unit
)

@Composable
fun MainContent() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val screens: List<Screen> = listOf(SpeakerScreen)
    val selectedScreen = remember { mutableStateOf(screens[0]) }

    // The reason I don't use [NavigationBar] is that [Scaffold] needs to be in the parent
    // layer, which breaks the connection between [TopAppBar] and [ContentView],
    // which is a complex but not necessary design.
    // In this app, Speaker is a main and unique part, and [NavigationBar] which I think is
    // putting equally important part in a one layer. Based on this, I think complex sibling
    // component interactions are necessary.
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(120.dp)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(12.dp))
                    screens.forEach { screen ->
                        NavigationDrawerItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                selectedScreen.value = screen
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        },
    ) {
        when (selectedScreen.value) {
            SpeakerScreen -> SpeakerScreen.makeContent { scope.launch { drawerState.open() } }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    MainContent()
}