package com.yueji.memo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.SettingsStore
import java.time.LocalDate

/**
 * Delivers the alarm payloads. Because the app process may have been killed,
 * everything it needs is either carried in the intent (to-do / weekly) or
 * reloaded from the repository (digest lines).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        AppRepository.ensureInit(appContext)
        SettingsStore.ensureInit(appContext)
        Notifications.createChannels(appContext)

        when (intent.getStringExtra(ReminderScheduler.EXTRA_KIND)) {
            ReminderScheduler.KIND_TODO -> handleTodo(appContext, intent)
            ReminderScheduler.KIND_WEEKLY -> handleWeekly(appContext, intent)
            ReminderScheduler.KIND_DIGEST -> handleDigest(appContext, intent)
            else -> Unit
        }
    }

    private fun handleTodo(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val detail = intent.getStringExtra(ReminderScheduler.EXTRA_DETAIL).orEmpty()
        val startLabel = intent.getStringExtra(ReminderScheduler.EXTRA_START_LABEL).orEmpty()
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET, 20)
        val todoId = intent.getStringExtra(ReminderScheduler.EXTRA_TODO_ID).orEmpty()
        val notifyId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFY_ID, todoId.hashCode())

        Notifications.notifyTodo(
            context = context,
            todoId = todoId,
            title = title,
            detail = detail,
            startLabel = startLabel,
            minutesBefore = offset,
            notificationId = notifyId,
        )
    }

    private fun handleWeekly(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val detail = intent.getStringExtra(ReminderScheduler.EXTRA_DETAIL).orEmpty()
        val startLabel = intent.getStringExtra(ReminderScheduler.EXTRA_START_LABEL).orEmpty()
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET, 20)
        val weeklyId = intent.getStringExtra(ReminderScheduler.EXTRA_WEEKLY_ID).orEmpty()
        val notifyId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFY_ID, weeklyId.hashCode())

        Notifications.notifyWeekly(
            context = context,
            weeklyId = weeklyId,
            title = title,
            detail = detail,
            startLabel = startLabel,
            minutesBefore = offset,
            notificationId = notifyId,
        )

        // Re-arm the whole item: the occurrence that just fired rolls forward a week.
        AppRepository.findWeekly(weeklyId)?.let { ReminderScheduler.scheduleWeekly(context, it) }
    }

    private fun handleDigest(context: Context, intent: Intent) {
        val index = intent.getIntExtra(ReminderScheduler.EXTRA_DIGEST_INDEX, 0)
        val notifyId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFY_ID, 0)
        val settings = SettingsStore.state.value
        val raw = settings.digestTimes.getOrNull(index)
        val label = raw?.let { ReminderScheduler.slotLabel(it) } ?: ReminderScheduler.nowLabel()

        val (lines, weeklyCount) = ReminderScheduler.digestLines(
            today = LocalDate.now(),
            includeWeekly = settings.digestIncludeWeekly,
        )
        val todoCount = lines.count { it.startsWith("•") || it.startsWith("✓") }

        Notifications.notifyDigest(
            context = context,
            slotLabel = label,
            lines = lines,
            todoCount = todoCount,
            weeklyCount = weeklyCount,
            notificationId = notifyId,
        )

        // The one-shot alarm is consumed, so re-arm this slot for tomorrow.
        ReminderScheduler.scheduleDigests(context, settings)
    }
}
