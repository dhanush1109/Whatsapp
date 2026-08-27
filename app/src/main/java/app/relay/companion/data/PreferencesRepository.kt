package app.relay.companion.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "relay_settings")

enum class ThemePreference { System, Light, Dark }

fun ThemePreference.resolveDark(systemDark: Boolean): Boolean = when (this) {
    ThemePreference.System -> systemDark
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
}

data class RelaySettings(
    val theme: ThemePreference = ThemePreference.System,
    val lockEnabled: Boolean = false,
    val hasPin: Boolean = false,
    val statusTreeUri: String? = null,
    val hapticsEnabled: Boolean = true,
    val webTextZoom: Int = PreferencesRepository.WEB_TEXT_ZOOM_DEFAULT,
)

class PreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val lockKey = booleanPreferencesKey("lock_enabled")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val treeKey = stringPreferencesKey("status_tree_uri")
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")
    private val webTextZoomKey = intPreferencesKey("web_text_zoom")

    val settings: Flow<RelaySettings> = context.dataStore.data.map { prefs ->
        RelaySettings(
            theme = runCatching { ThemePreference.valueOf(prefs[themeKey] ?: ThemePreference.System.name) }
                .getOrDefault(ThemePreference.System),
            lockEnabled = prefs[lockKey] == true && !prefs[pinHashKey].isNullOrBlank(),
            hasPin = !prefs[pinHashKey].isNullOrBlank(),
            statusTreeUri = prefs[treeKey],
            hapticsEnabled = prefs[hapticsKey] != false,
            webTextZoom = (prefs[webTextZoomKey] ?: WEB_TEXT_ZOOM_DEFAULT)
                .coerceIn(WEB_TEXT_ZOOM_MIN, WEB_TEXT_ZOOM_MAX),
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[hapticsKey] = enabled }
    }

    suspend fun setWebTextZoom(zoom: Int) {
        context.dataStore.edit {
            it[webTextZoomKey] = zoom.coerceIn(WEB_TEXT_ZOOM_MIN, WEB_TEXT_ZOOM_MAX)
        }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[lockKey] = enabled }
    }

    suspend fun setStatusTreeUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(treeKey) else it[treeKey] = uri
        }
    }

    suspend fun savePin(pin: String) {
        val salt = randomSalt()
        val hash = hashPin(pin, salt)
        context.dataStore.edit {
            it[pinSaltKey] = salt
            it[pinHashKey] = hash
            it[lockKey] = true
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val salt = prefs[pinSaltKey].orEmpty()
        val hash = prefs[pinHashKey].orEmpty()
        if (salt.isBlank() || hash.isBlank()) return false
        return hashPin(pin, salt) == hash
    }

    suspend fun clearPin() {
        context.dataStore.edit {
            it.remove(pinHashKey)
            it.remove(pinSaltKey)
            it[lockKey] = false
        }
    }

    companion object {
        const val WEB_TEXT_ZOOM_MIN = 100
        const val WEB_TEXT_ZOOM_MAX = 225
        const val WEB_TEXT_ZOOM_STEP = 10
        const val WEB_TEXT_ZOOM_DEFAULT = 140
        const val WEB_CHAT_ZOOM = 225

        fun hashPin(pin: String, salt: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun randomSalt(): String {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
