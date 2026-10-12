package com.yueji.memo.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yueji.memo.MainActivity
import com.yueji.memo.R

/**
 * All notification plumbing: channels, permission checks and the three concrete
 * notification shapes (to-do reminder, weekly reminder, daily digest).
 */
object Notifications {

    const val CHANNEL_TODO = "channel_todo"
    const val CHANNEL_WEEKLY = "channel_weekly"
    const val CHANNEL_DIGEST = "channel_digest"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val todo = NotificationChannel(
            CHANNEL_TODO,
            context.getString(R.string.channel_todo),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_todo_desc)
            enableVibration(true)
        }

        val weekly = NotificationChannel(
            CHANNEL_WEEKLY,
            context.getString(R.string.channel_weekly),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_weekly_desc)
            enableVibration(true)
        }

        val digest = NotificationChannel(
            CHANNEL_DIGEST,
            context.getString(R.string.channel_digest),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_digest_desc)
        }

        manager.createNotificationChannels(listOf(todo, weekly, digest))
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun contentIntent(context: Context, extraTodoId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (extraTodoId != null) putExtra(MainActivity.EXTRA_FOCUS_TODO, extraTodoId)
        }
        return PendingIntent.getActivity(
            context,
            extraTodoId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ------------------------------------------------------- to-do reminder

    fun notifyTodo(
        context: Context,
        todoId: String,
        title: String,
        detail: String,
        startLabel: String,
        minutesBefore: Int,
        notificationId: Int,
    ) {
        if (!canPost(context)) return
        val safeTitle = title.ifBlank { context.getString(R.string.untitled_todo) }
        val text = context.getString(R.string.notif_todo_body, minutesBefore, startLabel)

        val builder = NotificationCompat.Builder(context, CHANNEL_TODO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(safeTitle)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(text)
                        if (detail.isNotBlank()) append("\n").append(detail)
                    },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, todoId))

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    // ---------------------------------------------------- weekly reminder

    fun notifyWeekly(
        context: Context,
        weeklyId: String,
        title: String,
        detail: String,
        startLabel: String,
        minutesBefore: Int,
        notificationId: Int,
    ) {
        if (!canPost(context)) return
        val safeTitle = title.ifBlank { context.getString(R.string.untitled_weekly) }
        val text = context.getString(R.string.notif_weekly_body, minutesBefore, startLabel)

        val builder = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(safeTitle)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(text)
                        if (detail.isNotBlank()) append("\n").append(detail)
                    },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, weeklyId))

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    // -------------------------------------------------------- daily digest

    fun notifyDigest(
        context: Context,
        slotLabel: String,
        lines: List<String>,
        todoCount: Int,
        weeklyCount: Int,
        notificationId: Int,
    ) {
        if (!canPost(context)) return
        if (lines.isEmpty()) return

        val summary = context.getString(R.string.notif_digest_summary, todoCount, weeklyCount)
        val bigTitle = context.getString(R.string.notif_digest_title, slotLabel)
        // NB: NotificationCompat.Style declares a *member* `apply(NotificationBuilderWithBuilderAccessor)`,
        // which shadows Kotlin's `apply {}` extension on InboxStyle — build the style imperatively.
        val inbox = NotificationCompat.InboxStyle()
        inbox.setBigContentTitle(bigTitle)
        lines.take(7).forEach { inbox.addLine(it) }
        inbox.setSummaryText(summary)

        val builder = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(bigTitle)
            .setContentText(summary)
            .setStyle(inbox)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    // -------------------------------------------------------- test / debug

    /** Fires an immediate sample so the user can confirm the channel works. */
    fun notifyTest(context: Context) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
        NotificationManagerCompat.from(context).notify(999_001, builder.build())
    }
}
