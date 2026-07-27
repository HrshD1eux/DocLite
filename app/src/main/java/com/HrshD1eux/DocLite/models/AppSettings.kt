package com.HrshD1eux.DocLite.models

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class FontSizeMode(val scaleFactor: Float, val label: String) {
    SMALL(0.85f, "Small"),
    MEDIUM(1.0f, "Medium"),
    LARGE(1.2f, "Large")
}

enum class StartScreen(val label: String) {
    HOME("Home"),
    FILE_MANAGER("File Manager"),
    RECENT("Recent Documents")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val fontSizeMode: FontSizeMode = FontSizeMode.MEDIUM,
    val startScreen: StartScreen = StartScreen.HOME,
    val isAutoSaveEnabled: Boolean = true,
    val autoSaveIntervalSeconds: Int = 30,
    val defaultSaveLocation: String = "DocLite Documents"
)

