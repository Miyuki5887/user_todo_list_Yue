package com.yueji.memo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences: the daily digest schedule the user picks by hand, reminder
 * toggles and the theme override. Backed by SharedPreferences (tiny payload, no
 * DataStore dependency needed) and mirrored into a [StateFlow] for Compose.
 */
data class AppSettings(
    /** One or more wall-clock "HH:mm" slots at which the daily summary is pushed. */
    val digestTimes: List<String> = listOf("08:00", "20:00"),
    /** 0 = follow system, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
    val todoRemind20: Boolean = true,
    val todoRemind10: Boolean = true,
    /** Whether the digest also lists weekly recurring items for the day. */
    val digestIncludeWeekly: Boolean = true,
)

object SettingsStore {

    private const val PREFS = "yueji_settings"
    private const val K_DIGEST = "digest_times"
    private const val K_THEME = "theme_mode"
    private const val K_REMIND20 = "todo_remind_20"
    private const val K_REMIND10 = "todo_remind_10"
    private const val K_DIGEST_WEEKLY = "digest_include_weekly"

    @Volatile private var appContext: Context? = null
    @Volatile private var loaded = false

    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun ensureInit(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val ctx = context.applicationContext
            appContext = ctx
            _state.value = read(ctx)
            loaded = true
        }
    }

    private fun read(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val digest = prefs.getString(K_DIGEST, null)
            ?.split(',')
            ?.mapNotNull { normalizeTimeInput(it) }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: AppSettings().digestTimes
        return AppSettings(
            digestTimes = digest,
            themeMode = prefs.getInt(K_THEME, 0),
            todoRemind20 = prefs.getBoolean(K_REMIND20, true),
            todoRemind10 = prefs.getBoolean(K_REMIND10, true),
            digestIncludeWeekly = prefs.getBoolean(K_DIGEST_WEEKLY, true),
        )
    }

    private fun commit(settings: AppSettings) {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_DIGEST, settings.digestTimes.joinToString(","))
            .putInt(K_THEME, settings.themeMode)
            .putBoolean(K_REMIND20, settings.todoRemind20)
            .putBoolean(K_REMIND10, settings.todoRemind10)
            .putBoolean(K_DIGEST_WEEKLY, settings.digestIncludeWeekly)
            .apply()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        commit(next)
        val context = appContext ?: return
        // Digest slots are alarm-backed, so any change must be re-armed.
        ReminderBridge.onSettingsChanged(context, next)
    }
}

/**
 * Breaks the data -> reminder dependency cycle: the reminder package installs
 * its scheduler here so [SettingsStore] can stay free of AlarmManager imports.
 */
object ReminderBridge {
    @Volatile private var settingsChanged: ((Context, AppSettings) -> Unit)? = null

    fun install(block: (Context, AppSettings) -> Unit) { settingsChanged = block }

    fun onSettingsChanged(context: Context, settings: AppSettings) {
        runCatching { settingsChanged?.invoke(context, settings) }
    }
}
