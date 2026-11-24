package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.locale.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ayer_user_prefs")

class AppPreferencesStore(context: Context) {
    private val dataStore = context.applicationContext.appPrefsDataStore

    private object Keys {
        val THEME_OVERRIDE_DARK = booleanPreferencesKey("theme_override_dark")
        val THEME_FOLLOW_SYSTEM = booleanPreferencesKey("theme_follow_system")
        val APP_LANGUAGE_CODE = stringPreferencesKey("app_language_code")
    }

    val darkThemeFlow: Flow<Boolean?> = dataStore.data.map { prefs ->
        val followSystem = prefs[Keys.THEME_FOLLOW_SYSTEM] ?: true
        if (followSystem) null else prefs[Keys.THEME_OVERRIDE_DARK]
    }

    val appLanguageFlow: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.APP_LANGUAGE_CODE] ?: AppLanguage.ARABIC.code)
    }

    suspend fun setDarkTheme(enabled: Boolean?) {
        dataStore.edit { prefs ->
            if (enabled == null) {
                prefs[Keys.THEME_FOLLOW_SYSTEM] = true
                prefs.remove(Keys.THEME_OVERRIDE_DARK)
            } else {
                prefs[Keys.THEME_FOLLOW_SYSTEM] = false
                prefs[Keys.THEME_OVERRIDE_DARK] = enabled
            }
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE_CODE] = language.code
        }
    }
}
