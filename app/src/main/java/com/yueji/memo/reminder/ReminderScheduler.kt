package com.yueji.memo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.AppSettings
import com.yueji.memo.data.TimeCodec
import com.yueji.memo.data.WeeklyItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * AlarmManager-backed scheduler.
 *
 * Every reminder is a one-shot exact alarm; when it fires [ReminderReceiver]
 * re-arms the next occurrence. That is more reliable than setRepeating()
 * (inexact since API 19) and keeps the app free of a WorkManager dependency.
 *
 * Request codes are derived from the entity id plus a slot number, so a given
 * to-do owns exactly two stable alarm slots (T-20 and T-10).
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    const val EXTRA_KIND = "yueji.kind"
    const val EXTRA_TODO_ID = "yueji.todoId"
    const val EXTRA_WEEKLY_ID = "yueji.weeklyId"
    const val EXTRA_TITLE = "yueji.title"
    const val EXTRA_DETAIL = "yueji.detail"
    const val EXTRA_START_LABEL = "yueji.startLabel"
    const val EXTRA_OFFSET = "yueji.offset"
    const val EXTRA_DIGEST_INDEX = "yueji.digestIndex"
    const val EXTRA_NOTIFY_ID = "yueji.notifyId"

    const val KIND_TODO = "todo"
    const val KIND_WEEKLY = "weekly"
    const val KIND_DIGEST = "digest"

    private const val SLOT_TODO_20 = 1
    private const val SLOT_TODO_10 = 2
    private const val SLOT_WEEKLY_20 = 3
    private const val SLOT_WEEKLY_10 = 4

    /** Digest slots are addressed from a dedicated request-code range. */
    private const val DIGEST_CODE_BASE = 800_000
    /** Hard ceiling on user-chosen digest times (mirrored by the settings UI). */
    const val MAX_DIGEST_SLOTS = 8

    private const val MINUTE = 60_000L

    private fun requestCode(id: String, slot: Int): Int =
        ((id.hashCode() and 0x0FFFFFFF) * 8) + slot

    // ------------------------------------------------------------ public API

    /** Re-arms every alarm the app owns: boot, time change, timezone change, app start. */
    fun rescheduleAll(context: Context, settings: AppSettings) {
        AppRepository.ensureInit(context)
        AppRepository.todos.value.forEach { cancelTodo(context, it.id) }
        AppRepository.weekly.value.forEach { cancelWeekly(context, it.id) }
        cancelDigests(context)

        AppRepository.todos.value.forEach { scheduleTodo(context, it, settings) }
        AppRepository.weekly.value.forEach { scheduleWeekly(context, it) }
        scheduleDigests(context, settings)
        Log.i(TAG, "rescheduleAll complete")
    }

    /** Arms the T-20 and T-10 alarms for a single to-do. */
    fun scheduleTodo(context: Context, todo: com.yueji.memo.data.Todo, settings: AppSettings) {
        cancelTodo(context, todo.id)
        if (todo.done) return

        if (settings.todoRemind20 && todo.remind20) {
            armTodo(context, todo, offsetMinutes = 20, slot = SLOT_TODO_20)
        }
        if (settings.todoRemind10 && todo.remind10) {
            armTodo(context, todo, offsetMinutes = 10, slot = SLOT_TODO_10)
        }
    }

    fun cancelTodo(context: Context, todoId: String) {
        cancelSlot(context, requestCode(todoId, SLOT_TODO_20))
        cancelSlot(context, requestCode(todoId, SLOT_TODO_10))
    }

    /** Arms T-20 / T-10 for every weekday the item recurs on. */
    fun scheduleWeekly(context: Context, item: WeeklyItem) {
        cancelWeekly(context, item.id)
        if (!item.enabled) return
        val time = item.localTime
        item.sortedDays.forEach { isoDay ->
            val dayOfWeek = DayOfWeek.of(isoDay.coerceIn(1, 7))
            val startMillis = TimeCodec.toMillis(TimeCodec.nextOccurrence(dayOfWeek, time))
            if (item.remind20) {
                armWeekly(context, item, offsetMinutes = 20, slot = SLOT_WEEKLY_20, startMillis = startMillis)
            }
            if (item.remind10) {
                armWeekly(context, item, offsetMinutes = 10, slot = SLOT_WEEKLY_10, startMillis = startMillis)
            }
        }
    }

    fun cancelWeekly(context: Context, weeklyId: String) {
        cancelSlot(context, requestCode(weeklyId, SLOT_WEEKLY_20))
        cancelSlot(context, requestCode(weeklyId, SLOT_WEEKLY_10))
    }

    /**
     * Arms one daily alarm per user-chosen slot. This is the feature where the
     * user picks one *or more* times of day for the "today's to-dos" push.
     */
    fun scheduleDigests(context: Context, settings: AppSettings) {
        val manager = alarmManager(context)
        settings.digestTimes.take(MAX_DIGEST_SLOTS).forEachIndexed { index, raw ->
            val triggerAt = TimeCodec.toMillis(TimeCodec.nextDaily(TimeCodec.toTime(raw)))
            if (triggerAt <= System.currentTimeMillis()) return@forEachIndexed
            val code = DIGEST_CODE_BASE + index
            val pi = pendingIntent(context, digestIntent(context, index, code), code)
            setExact(manager, triggerAt, pi)
        }
    }

    fun cancelDigests(context: Context) {
        for (index in 0 until MAX_DIGEST_SLOTS) {
            val code = DIGEST_CODE_BASE + index
            cancelSlot(context, code)
        }
    }

    // ---------------------------------------------------------------- arming

    private fun armTodo(context: Context, todo: com.yueji.memo.data.Todo, offsetMinutes: Int, slot: Int) {
        val triggerAt = todo.startAt - offsetMinutes * MINUTE
        if (triggerAt <= System.currentTimeMillis()) return
        val code = requestCode(todo.id, slot)
        val intent = baseIntent(context, code).apply {
            putExtra(EXTRA_KIND, KIND_TODO)
            putExtra(EXTRA_TODO_ID, todo.id)
            putExtra(EXTRA_TITLE, todo.title)
            putExtra(EXTRA_DETAIL, todo.detail)
            putExtra(EXTRA_OFFSET, offsetMinutes)
            putExtra(EXTRA_NOTIFY_ID, code)
            putExtra(EXTRA_START_LABEL, TimeCodec.formatDateTime(todo.startAt))
        }
        setExact(alarmManager(context), triggerAt, pendingIntent(context, intent, code))
    }

    private fun armWeekly(
        context: Context,
        item: WeeklyItem,
        offsetMinutes: Int,
        slot: Int,
        startMillis: Long,
    ) {
        val triggerAt = startMillis - offsetMinutes * MINUTE
        if (triggerAt <= System.currentTimeMillis()) return
        val code = requestCode(item.id, slot)
        val intent = baseIntent(context, code).apply {
            putExtra(EXTRA_KIND, KIND_WEEKLY)
            putExtra(EXTRA_WEEKLY_ID, item.id)
            putExtra(EXTRA_TITLE, item.title)
            putExtra(EXTRA_DETAIL, item.detail)
            putExtra(EXTRA_OFFSET, offsetMinutes)
            putExtra(EXTRA_NOTIFY_ID, code)
            putExtra(EXTRA_START_LABEL, item.time)
        }
        setExact(alarmManager(context), triggerAt, pendingIntent(context, intent, code))
    }

    // ------------------------------------------------------------- plumbing

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun setExact(manager: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        try {
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Graceful degradation: still fires, but the OS may batch it.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "exact alarm rejected, degrading to inexact", se)
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    /**
     * Cancels whatever alarm occupies [code]. The pending intent is rebuilt
     * without extras on purpose: PendingIntent matching ignores extras, and the
     * per-code `data` URI keeps the slots distinguishable.
     */
    private fun cancelSlot(context: Context, code: Int) {
        val probe = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("yueji://reminder/$code"))
        val pi = PendingIntent.getBroadcast(
            context, code, probe,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager(context).cancel(pi)
            pi.cancel()
        }
    }

    private fun pendingIntent(context: Context, intent: Intent, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun baseIntent(context: Context, code: Int): Intent =
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("yueji://reminder/$code"))

    private fun digestIntent(context: Context, index: Int, code: Int): Intent =
        baseIntent(context, code).apply {
            putExtra(EXTRA_KIND, KIND_DIGEST)
            putExtra(EXTRA_DIGEST_INDEX, index)
            putExtra(EXTRA_NOTIFY_ID, code)
        }

    // --------------------------------------------------------------- helpers

    /** Today's to-dos, formatted for the digest notification body. */
    fun digestLines(today: LocalDate, includeWeekly: Boolean): Pair<List<String>, Int> {
        val todos = AppRepository.todosOn(today)
        val lines = todos.map { todo ->
            val mark = if (todo.done) "✓" else "•"
            "$mark ${TimeCodec.formatClock(todo.startAt)}  ${todo.title.ifBlank { "未命名待办" }}"
        }.toMutableList()
        var weeklyCount = 0
        if (includeWeekly) {
            val weekly = AppRepository.weeklyOn(today)
            weeklyCount = weekly.size
            weekly.forEach { item ->
                lines += "↻ ${item.time}  ${item.title.ifBlank { "每周事项" }}"
            }
        }
        return lines to weeklyCount
    }

    /** Formatting for a digest slot label, resolved from the live settings. */
    fun slotLabel(raw: String): String {
        val time = TimeCodec.toTime(raw)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    fun nowLabel(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
}
