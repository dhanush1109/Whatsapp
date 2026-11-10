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
    val biometricEnabled: Boolean = false,
    val statusTreeUri: String? = null,
    val hapticsEnabled: Boolean = true,
    val webTextZoom: Int = PreferencesRepository.WEB_TEXT_ZOOM_DEFAULT,
)

class PreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val lockKey = booleanPreferencesKey("lock_enabled")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val biometricKey = booleanPreferencesKey("biometric_enabled")
    private val treeKey = stringPreferencesKey("status_tree_uri")
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")
    private val webTextZoomKey = intPreferencesKey("web_text_zoom")

    val settings: Flow<RelaySettings> = context.dataStore.data.map { prefs ->
        val hasPin = !prefs[pinHashKey].isNullOrBlank()
        val biometricEnabled = prefs[biometricKey] == true
        RelaySettings(
            theme = runCatching { ThemePreference.valueOf(prefs[themeKey] ?: ThemePreference.System.name) }
                .getOrDefault(ThemePreference.System),
            // The lock screen can be satisfied by either a PIN or biometrics, so
            // App Lock is considered on when the user turned it on and at least
            // one unlock method is available.
            lockEnabled = prefs[lockKey] == true && (hasPin || biometricEnabled),
            hasPin = hasPin,
            biometricEnabled = biometricEnabled,
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
        context.dataStore.edit {
            it[lockKey] = enabled
            if (!enabled) it[biometricKey] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[biometricKey] = enabled
            // Turning on biometric unlock implies App Lock is on.
            if (enabled) it[lockKey] = true
        }
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
            // Only fully disable the lock if biometrics aren't the remaining method.
            if (it[biometricKey] != true) it[lockKey] = false
        }
    }

    companion object {
        const val WEB_TEXT_ZOOM_MIN = 100
        const val WEB_TEXT_ZOOM_MAX = 225
        const val WEB_TEXT_ZOOM_STEP = 10
        const val WEB_TEXT_ZOOM_DEFAULT = 125
        const val WEB_CHAT_ZOOM = 130

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
