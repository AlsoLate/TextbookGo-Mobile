package com.textbookgo.mobile.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.textbookgo.mobile.core.repository.AppPreferences
import com.textbookgo.mobile.core.repository.AppTheme
import com.textbookgo.mobile.core.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore("textbookgo-settings")

class DataStorePreferencesRepository(context: Context) : PreferencesRepository {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _preferences = MutableStateFlow(AppPreferences())
    override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    init {
        scope.launch {
            runCatching {
                appContext.settingsDataStore.data
                    .map { prefs ->
                        AppPreferences(
                            // 旧版本只存了 DAY/NIGHT；缺失或无法识别时回到“跟随系统”。
                            theme = AppTheme.entries.firstOrNull { it.name == prefs[KEY_THEME] }
                                ?: AppTheme.SYSTEM,
                            rememberSession = prefs[KEY_REMEMBER] ?: true,
                        )
                    }
                    .first()
            }.onSuccess { loaded -> _preferences.value = loaded }
        }
    }

    override suspend fun setTheme(theme: AppTheme) {
        appContext.settingsDataStore.edit { it[KEY_THEME] = theme.name }
        _preferences.value = _preferences.value.copy(theme = theme)
    }

    override suspend fun setRememberSession(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_REMEMBER] = enabled }
        _preferences.value = _preferences.value.copy(rememberSession = enabled)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_REMEMBER = booleanPreferencesKey("remember_session")
    }
}
