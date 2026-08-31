package com.kanshu.reader.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val writeDailyGoalKey = intPreferencesKey("write_daily_goal")
    private val writeDailyCountKey = intPreferencesKey("write_daily_count")
    private val writeDailyDateKey = stringPreferencesKey("write_daily_date")

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

    /** 每日写作字数目标，默认 1000。 */
    val writeDailyGoal: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[writeDailyGoalKey] ?: 1000).coerceIn(100, 50_000)
    }

    /** 今日已写字数（跨会话累计，按自然日重置）。 */
    val writeDailyProgress: Flow<Pair<Int, String>> = context.dataStore.data.map { prefs ->
        val date = prefs[writeDailyDateKey].orEmpty()
        val count = prefs[writeDailyCountKey] ?: 0
        count to date
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

    suspend fun setWriteDailyGoal(goal: Int) {
        context.dataStore.edit { prefs ->
            prefs[writeDailyGoalKey] = goal.coerceIn(100, 50_000)
        }
    }

    /**
     * 把本会话新增字数累加到今日进度。
     * @return 更新后的今日字数
     */
    suspend fun addWriteDailyChars(delta: Int, today: String): Int {
        var result = 0
        context.dataStore.edit { prefs ->
            val storedDate = prefs[writeDailyDateKey].orEmpty()
            val base = if (storedDate == today) (prefs[writeDailyCountKey] ?: 0) else 0
            result = (base + delta.coerceAtLeast(0)).coerceAtLeast(0)
            prefs[writeDailyDateKey] = today
            prefs[writeDailyCountKey] = result
        }
        return result
    }
}
