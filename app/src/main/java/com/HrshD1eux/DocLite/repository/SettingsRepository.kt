package com.HrshD1eux.DocLite.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.HrshD1eux.DocLite.models.AppSettings
import com.HrshD1eux.DocLite.models.FontSizeMode
import com.HrshD1eux.DocLite.models.StartScreen
import com.HrshD1eux.DocLite.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doclite_settings")

class SettingsRepository(private val context: Context) {

    private object PreferenceKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val START_SCREEN = stringPreferencesKey("start_screen")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval")
        val DEFAULT_SAVE_LOCATION = stringPreferencesKey("default_save_location")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.valueOf(prefs[PreferenceKeys.THEME_MODE] ?: ThemeMode.LIGHT.name),
            fontSizeMode = FontSizeMode.valueOf(prefs[PreferenceKeys.FONT_SIZE] ?: FontSizeMode.MEDIUM.name),
            startScreen = StartScreen.valueOf(prefs[PreferenceKeys.START_SCREEN] ?: StartScreen.HOME.name),
            isAutoSaveEnabled = prefs[PreferenceKeys.AUTO_SAVE] ?: true,
            autoSaveIntervalSeconds = prefs[PreferenceKeys.AUTO_SAVE_INTERVAL] ?: 30,
            defaultSaveLocation = prefs[PreferenceKeys.DEFAULT_SAVE_LOCATION] ?: "DocLite Documents"
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateFontSize(size: FontSizeMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.FONT_SIZE] = size.name
        }
    }

    suspend fun updateStartScreen(screen: StartScreen) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.START_SCREEN] = screen.name
        }
    }

    suspend fun updateAutoSave(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.AUTO_SAVE] = enabled
        }
    }

    suspend fun updateDefaultSaveLocation(location: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.DEFAULT_SAVE_LOCATION] = location
        }
    }
}

