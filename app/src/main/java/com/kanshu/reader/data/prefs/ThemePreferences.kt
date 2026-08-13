package com.kanshu.reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class AppThemeMode {
    DAY,
    NIGHT
}

class ThemePreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val githubTokenKey = stringPreferencesKey("github_token")
    private val deepseekApiKeyKey = stringPreferencesKey("deepseek_api_key")

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            AppThemeMode.NIGHT.name -> AppThemeMode.NIGHT
            else -> AppThemeMode.DAY
        }
    }

    val githubToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[githubTokenKey].orEmpty()
    }

    val deepseekApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[deepseekApiKeyKey].orEmpty()
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setGithubToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[githubTokenKey] = token.trim()
        }
    }

    suspend fun clearGithubToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(githubTokenKey)
        }
    }

    suspend fun setDeepseekApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[deepseekApiKeyKey] = key.trim()
        }
    }

    suspend fun clearDeepseekApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(deepseekApiKeyKey)
        }
    }
}
