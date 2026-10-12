package com.yueji.memo

import android.content.Context
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.Note
import com.yueji.memo.data.SettingsStore
import com.yueji.memo.data.Todo
import com.yueji.memo.data.WeeklyItem
import com.yueji.memo.data.WidgetBridge
import com.yueji.memo.reminder.Notifications
import com.yueji.memo.reminder.ReminderScheduler

/**
 * Write-side facade. Every mutation goes through here so the three side effects
 * that must stay in sync - persistence, alarm re-arming and the home-screen
 * widget - can never drift apart.
 */
object AppActions {

    // ------------------------------------------------------------------ notes

    fun saveNote(note: Note) = AppRepository.upsertNote(note)

    fun deleteNote(id: String) = AppRepository.deleteNote(id)

    fun toggleNotePinned(id: String) = AppRepository.toggleNotePinned(id)

    // ------------------------------------------------------------------ todos

    fun saveTodo(context: Context, todo: Todo) {
        AppRepository.upsertTodo(todo)
        ReminderScheduler.scheduleTodo(context, todo, SettingsStore.state.value)
    }

    fun deleteTodo(context: Context, id: String) {
        ReminderScheduler.cancelTodo(context, id)
        AppRepository.deleteTodo(id)
    }

    /** Completing a to-do drops its pending reminders; re-opening re-arms them. */
    fun toggleTodoDone(context: Context, id: String) {
        val before = AppRepository.findTodo(id) ?: return
        AppRepository.toggleTodoDone(id)
        val after = AppRepository.findTodo(id) ?: return
        if (after.done) {
            ReminderScheduler.cancelTodo(context, id)
        } else if (before.done) {
            ReminderScheduler.scheduleTodo(context, after, SettingsStore.state.value)
        }
    }

    // ----------------------------------------------------------------- weekly

    fun saveWeekly(context: Context, item: WeeklyItem) {
        AppRepository.upsertWeekly(item)
        if (item.enabled) {
            ReminderScheduler.scheduleWeekly(context, item)
        } else {
            ReminderScheduler.cancelWeekly(context, item.id)
        }
    }

    fun deleteWeekly(context: Context, id: String) {
        ReminderScheduler.cancelWeekly(context, id)
        AppRepository.deleteWeekly(id)
    }

    fun toggleWeeklyEnabled(context: Context, id: String) {
        val before = AppRepository.findWeekly(id) ?: return
        AppRepository.toggleWeeklyEnabled(id)
        val after = AppRepository.findWeekly(id) ?: return
        if (after.enabled && !before.enabled) {
            ReminderScheduler.scheduleWeekly(context, after)
        } else if (!after.enabled && before.enabled) {
            ReminderScheduler.cancelWeekly(context, id)
        }
    }

    // ---------------------------------------------------------------- digests

    /** Re-arms the daily digest alarms after the user edits the time slots. */
    fun applyDigestSchedule(context: Context) {
        ReminderScheduler.cancelDigests(context)
        ReminderScheduler.scheduleDigests(context, SettingsStore.state.value)
    }

    /**
     * Full re-arm after any settings change: digest slots, reminder defaults and
     * the widget all read from [SettingsStore], so one pass keeps them aligned.
     */
    fun applySettings(context: Context) {
        ReminderScheduler.rescheduleAll(context, SettingsStore.state.value)
        WidgetBridge.requestRefresh(context)
    }

    /** Debug affordance so the user can verify notifications are permitted. */
    fun sendTestNotification(context: Context) {
        Notifications.notifyTest(context)
    }
}
