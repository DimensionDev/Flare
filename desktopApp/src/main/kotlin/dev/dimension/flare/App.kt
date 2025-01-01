package dev.dimension.flare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.MenuItemSeparator
import com.konyaco.fluent.component.NavigationDisplayMode
import com.konyaco.fluent.component.NavigationView
import com.konyaco.fluent.component.SubtleButton
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.component.menuItem
import com.konyaco.fluent.component.rememberNavigationState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.HouseMedical
import compose.icons.fontawesomeicons.solid.Pen

val menus =
    listOf(
        "Home",
        "Notification",
        "Settings",
        "About",
    )

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun FlareApp() {
    val windowInfo = calculateWindowSizeClass()
    val bigScreen = windowInfo.widthSizeClass >= WindowWidthSizeClass.Medium
    val displayMode =
        if (bigScreen) {
            NavigationDisplayMode.Left
        } else {
            NavigationDisplayMode.LeftCompact
        }
    var selectedIndex by remember { mutableStateOf(0) }
    val state = rememberNavigationState()
    LaunchedEffect(bigScreen) {
        state.expanded = bigScreen
    }
    NavigationView(
        state = state,
        displayMode = displayMode,
        menuItems = {
            item {
                SubtleButton(
                    onClick = {},
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            FontAwesomeIcons.Solid.CircleUser,
                            contentDescription = "Profile",
                            modifier = Modifier.size(36.dp),
                        )
                        if (state.expanded) {
                            Column {
                                Text("<NAME>", maxLines = 1)
                                Text("Software Engineer", style = FluentTheme.typography.caption, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {},
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                ) {
                    Icon(
                        FontAwesomeIcons.Solid.Pen,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                    )
                    if (state.expanded) {
                        Text("Compose", maxLines = 1)
                    }
                }
                MenuItemSeparator()
            }
            menus.forEachIndexed { index, menu ->
                menuItem(
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                    icon = {
                        Icon(FontAwesomeIcons.Solid.HouseMedical, contentDescription = menu)
                    },
                    text = {
                        Text(menu)
                    },
                )
            }
        },
        title = {
            Text("Flare")
        },
        contentPadding = PaddingValues(top = 8.dp),
        footerItems = {
            menuItem(
                selected = false,
                onClick = {},
                icon = {
                    Icon(FontAwesomeIcons.Solid.Gear, contentDescription = "Settings")
                },
                text = {
                    Text("Settings")
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
        ) {
            Button(
                onClick = {},
            ) {
                Text("Click me!")
            }
        }
    }
}
