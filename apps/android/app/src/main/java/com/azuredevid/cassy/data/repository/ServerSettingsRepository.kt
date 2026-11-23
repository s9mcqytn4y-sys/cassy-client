package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.core.coroutines.runCatchingCancellable
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.azuredevid.cassy.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val Context.serverSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cassy_server_settings_ds",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, DataStoreServerSettingsRepository.LEGACY_PREFS_NAME))
    }
)

interface ServerSettingsRepository {
    val baseUrlFlow: Flow<String>
        get() = flowOf(getBaseUrl())

    val audioCueMutedFlow: Flow<Boolean>
        get() = flowOf(isAudioCueMuted())

    fun getBaseUrl(): String
    fun setBaseUrl(url: String)
    fun isAudioCueMuted(): Boolean = false
    fun setAudioCueMuted(muted: Boolean) {}
}

class DataStoreServerSettingsRepository(
    context: Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ServerSettingsRepository {

    private val appContext = context.applicationContext
    private val dataStore = appContext.serverSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _baseUrlFlow = MutableStateFlow(readInitialSeed(appContext))
    override val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    private val _audioCueMutedFlow = MutableStateFlow(false)
    override val audioCueMutedFlow: StateFlow<Boolean> = _audioCueMutedFlow.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                val stored = prefs[KEY_BASE_URL_PREF]
                if (!stored.isNullOrBlank()) {
                    _baseUrlFlow.value = normalizeBaseUrl(stored)
                }
                _audioCueMutedFlow.value = prefs[KEY_AUDIO_CUE_MUTED_PREF] ?: false
            }
        }
    }

    override fun getBaseUrl(): String = _baseUrlFlow.value

    override fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        if (normalized.isNotBlank()) {
            _baseUrlFlow.value = normalized
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[KEY_BASE_URL_PREF] = normalized
                }
            }
        }
    }

    override fun isAudioCueMuted(): Boolean = _audioCueMutedFlow.value

    override fun setAudioCueMuted(muted: Boolean) {
        _audioCueMutedFlow.value = muted
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_AUDIO_CUE_MUTED_PREF] = muted
            }
        }
    }

    companion object {
        const val LEGACY_PREFS_NAME = "cassy_server_settings"
        private const val KEY_BASE_URL = "server_base_url"
        private val KEY_BASE_URL_PREF = stringPreferencesKey(KEY_BASE_URL)
        private val KEY_AUDIO_CUE_MUTED_PREF = booleanPreferencesKey("audio_cue_muted")

        private fun readInitialSeed(context: Context): String {
            return runCatchingCancellable {
                val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                val raw = legacyPrefs.getString(KEY_BASE_URL, null)
                if (!raw.isNullOrBlank()) normalizeBaseUrl(raw) else normalizeBaseUrl(BuildConfig.BASE_API_URL)
            }.getOrDefault(normalizeBaseUrl(BuildConfig.BASE_API_URL))
        }

        fun normalizeBaseUrl(raw: String): String {
            return raw.trim()
                .trimEnd('/')
                .removeSuffix("/api/v1")
                .trimEnd('/')
                .ifBlank { BuildConfig.BASE_API_URL }
        }
    }
}

typealias SharedPreferencesServerSettingsRepository = DataStoreServerSettingsRepository
