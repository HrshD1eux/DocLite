package com.HrshD1eux.DocLite.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.HrshD1eux.DocLite.ui.theme.SleekOutlineVariant

enum class BottomNavTab(val route: String, val title: String) {
    HOME("home", "Home"),
    FILES("file_manager", "Files"),
    SETTINGS("settings", "Settings")
}

@Composable
fun DocLiteBottomNav(
    currentRoute: String,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color(0xFF001F24),
        selectedTextColor = Color(0xFF001F24),
        indicatorColor = Color(0xFFD3E4E8),
        unselectedIconColor = Color(0xFF40484B),
        unselectedTextColor = Color(0xFF40484B)
    )

    NavigationBar(
        containerColor = Color(0xFFF0F4F4),
        tonalElevation = 0.dp,
        modifier = modifier
            .navigationBarsPadding()
            .testTag("bottom_navigation_bar")
    ) {
        NavigationBarItem(
            selected = currentRoute == BottomNavTab.HOME.route,
            onClick = { onTabSelected(BottomNavTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = navItemColors,
            modifier = Modifier.testTag("nav_item_home")
        )
        NavigationBarItem(
            selected = currentRoute == BottomNavTab.FILES.route,
            onClick = { onTabSelected(BottomNavTab.FILES) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
            label = { Text("Files") },
            colors = navItemColors,
            modifier = Modifier.testTag("nav_item_files")
        )
        NavigationBarItem(
            selected = currentRoute == BottomNavTab.SETTINGS.route,
            onClick = { onTabSelected(BottomNavTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = navItemColors,
            modifier = Modifier.testTag("nav_item_settings")
        )
    }
}

