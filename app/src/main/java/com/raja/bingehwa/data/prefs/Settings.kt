package com.raja.bingehwa.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the reader can change in the app, in one immutable snapshot. */
data class SettingsState(
    val onboarded: Boolean = false,
    val tastePicks: Set<String> = emptySet(),
    val chapterLang: String = "en",
    val readerMode: String = "vertical", // "vertical" (webtoon scroll) or "paged"
    val keepScreenOn: Boolean = true,
    val dataSaver: Boolean = false,
    val notificationsOn: Boolean = true,
    val quietHours: Boolean = false,
    val replyAlerts: Boolean = true,
    val displayName: String = "",
    val wifiOnlyDownloads: Boolean = true,
    val deleteAfterRead: Boolean = false,
)

/**
 * Why SharedPreferences and not a database: these are a dozen tiny values, and exposing them
 * as a StateFlow lets every screen react when one changes.
 */
class Settings(context: Context) {
    private val sp = context.getSharedPreferences("bingehwa_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val current: SettingsState get() = _state.value

    fun update(change: (SettingsState) -> SettingsState) {
        val next = change(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): SettingsState {
        val d = SettingsState()
        return SettingsState(
            onboarded = sp.getBoolean("onboarded", d.onboarded),
            tastePicks = sp.getStringSet("tastePicks", emptySet())?.toSet() ?: emptySet(),
            chapterLang = sp.getString("chapterLang", d.chapterLang) ?: d.chapterLang,
            readerMode = sp.getString("readerMode", d.readerMode) ?: d.readerMode,
            keepScreenOn = sp.getBoolean("keepScreenOn", d.keepScreenOn),
            dataSaver = sp.getBoolean("dataSaver", d.dataSaver),
            notificationsOn = sp.getBoolean("notificationsOn", d.notificationsOn),
            quietHours = sp.getBoolean("quietHours", d.quietHours),
            replyAlerts = sp.getBoolean("replyAlerts", d.replyAlerts),
            displayName = sp.getString("displayName", "") ?: "",
            wifiOnlyDownloads = sp.getBoolean("wifiOnlyDownloads", d.wifiOnlyDownloads),
            deleteAfterRead = sp.getBoolean("deleteAfterRead", d.deleteAfterRead),
        )
    }

    private fun write(s: SettingsState) {
        sp.edit()
            .putBoolean("onboarded", s.onboarded)
            .putStringSet("tastePicks", s.tastePicks)
            .putString("chapterLang", s.chapterLang)
            .putString("readerMode", s.readerMode)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("dataSaver", s.dataSaver)
            .putBoolean("notificationsOn", s.notificationsOn)
            .putBoolean("quietHours", s.quietHours)
            .putBoolean("replyAlerts", s.replyAlerts)
            .putString("displayName", s.displayName)
            .putBoolean("wifiOnlyDownloads", s.wifiOnlyDownloads)
            .putBoolean("deleteAfterRead", s.deleteAfterRead)
            .apply()
    }
}
