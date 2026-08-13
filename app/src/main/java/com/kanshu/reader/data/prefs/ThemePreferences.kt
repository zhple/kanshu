package com.kanshu.reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kanshu.reader.data.ai.TtsVoices
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
    private val siliconflowApiKeyKey = stringPreferencesKey("siliconflow_api_key")
    private val minimaxApiKeyKey = stringPreferencesKey("minimax_api_key")
    private val minimaxVoiceIdKey = stringPreferencesKey("minimax_voice_id")

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

    val siliconflowApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[siliconflowApiKeyKey].orEmpty()
    }

    val minimaxApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[minimaxApiKeyKey].orEmpty()
    }

    val minimaxVoiceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[minimaxVoiceIdKey]?.ifBlank { null } ?: TtsVoices.defaultId
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

    suspend fun setSiliconflowApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[siliconflowApiKeyKey] = key.trim()
        }
    }

    suspend fun clearSiliconflowApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(siliconflowApiKeyKey)
        }
    }

    suspend fun setMinimaxApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[minimaxApiKeyKey] = key.trim()
        }
    }

    suspend fun clearMinimaxApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(minimaxApiKeyKey)
        }
    }

    suspend fun setMinimaxVoiceId(voiceId: String) {
        context.dataStore.edit { prefs ->
            prefs[minimaxVoiceIdKey] = voiceId.trim().ifBlank { TtsVoices.defaultId }
        }
    }
}
