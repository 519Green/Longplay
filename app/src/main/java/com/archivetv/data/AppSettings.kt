package com.archivetv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User options. [openOnly]: limit Archive searches to public-domain and openly licensed items.
 * [hideAdult]: the parental filter. The PIN guards turning either filter off.
 */
interface AppSettings {
    val openOnly: StateFlow<Boolean>
    fun setOpenOnly(value: Boolean)

    val hideAdult: StateFlow<Boolean>
    fun setHideAdult(value: Boolean)

    fun hasPin(): Boolean
    /** Throws [IllegalArgumentException] unless [pin] is four ASCII digits. */
    fun setPin(pin: String)
    fun checkPin(pin: String): Boolean

    class InMemory(initial: Boolean = true, hideAdult: Boolean = true) : AppSettings {
        private val state = MutableStateFlow(initial)
        override val openOnly: StateFlow<Boolean> = state.asStateFlow()
        override fun setOpenOnly(value: Boolean) { state.value = value }

        private val adult = MutableStateFlow(hideAdult)
        override val hideAdult: StateFlow<Boolean> = adult.asStateFlow()
        override fun setHideAdult(value: Boolean) { adult.value = value }

        private var salt = ""
        private var hash: String? = null
        override fun hasPin() = hash != null
        override fun setPin(pin: String) {
            require(PinHash.isValid(pin)) { "A PIN is four digits" }
            salt = PinHash.newSalt()
            hash = PinHash.hash(salt, pin)
        }
        override fun checkPin(pin: String) = hash != null && PinHash.hash(salt, pin) == hash
    }
}

/** Stored in SharedPreferences, like [PrefsRecentSearches]. */
class PrefsAppSettings(context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY_OPEN_ONLY, true))
    override val openOnly: StateFlow<Boolean> = state.asStateFlow()

    override fun setOpenOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_ONLY, value).apply()
        state.value = value
    }

    private val adult = MutableStateFlow(prefs.getBoolean(KEY_HIDE_ADULT, true))
    override val hideAdult: StateFlow<Boolean> = adult.asStateFlow()

    override fun setHideAdult(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_ADULT, value).apply()
        adult.value = value
    }

    override fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    override fun setPin(pin: String) {
        require(PinHash.isValid(pin)) { "A PIN is four digits" }
        val salt = PinHash.newSalt()
        prefs.edit().putString(KEY_PIN_SALT, salt).putString(KEY_PIN_HASH, PinHash.hash(salt, pin)).apply()
    }

    override fun checkPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        return PinHash.hash(salt, pin) == hash
    }

    private companion object {
        const val KEY_OPEN_ONLY = "openOnly"
        const val KEY_HIDE_ADULT = "hideAdult"
        const val KEY_PIN_SALT = "pinSalt"
        const val KEY_PIN_HASH = "pinHash"
    }
}
