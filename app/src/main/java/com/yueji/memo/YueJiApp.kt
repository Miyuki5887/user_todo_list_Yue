package com.yueji.memo

import android.app.Application
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.ReminderBridge
import com.yueji.memo.data.SettingsStore
import com.yueji.memo.reminder.Notifications
import com.yueji.memo.reminder.ReminderScheduler
import com.yueji.memo.widget.WidgetRefresh

class YueJiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppRepository.ensureInit(this)
        SettingsStore.ensureInit(this)
        Notifications.createChannels(this)

        // Widget follows every repository mutation.
        WidgetRefresh.install()

        // Digest slots live in settings; any change re-arms the whole schedule.
        ReminderBridge.install { context, settings ->
            ReminderScheduler.rescheduleAll(context, settings)
        }

        // A force-stop or an update can leave alarms behind, so re-arm on cold start.
        ReminderScheduler.rescheduleAll(this, SettingsStore.state.value)
    }
}
