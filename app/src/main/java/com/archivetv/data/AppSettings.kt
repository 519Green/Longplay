package com.archivetv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User options. [openOnly]: limit Archive searches to public-domain and openly licensed items. */
interface AppSettings {
    val openOnly: StateFlow<Boolean>
    fun setOpenOnly(value: Boolean)

    class InMemory(initial: Boolean = true) : AppSettings {
        private val state = MutableStateFlow(initial)
        override val openOnly: StateFlow<Boolean> = state.asStateFlow()
        override fun setOpenOnly(value: Boolean) { state.value = value }
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

    private companion object { const val KEY_OPEN_ONLY = "openOnly" }
}
