package com.yueji.memo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.SettingsStore
import com.yueji.memo.data.WidgetBridge

/**
 * Alarms do not survive a reboot, a clock change or an app update, so every
 * such event rebuilds the full schedule.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return

        val appContext = context.applicationContext
        AppRepository.ensureInit(appContext)
        SettingsStore.ensureInit(appContext)
        ReminderScheduler.rescheduleAll(appContext, SettingsStore.state.value)
        WidgetBridge.requestRefresh(appContext)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
