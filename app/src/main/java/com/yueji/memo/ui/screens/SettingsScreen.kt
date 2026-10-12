package com.yueji.memo.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.memo.AppActions
import com.yueji.memo.data.SettingsStore
import com.yueji.memo.data.TimeCodec
import com.yueji.memo.reminder.Notifications
import com.yueji.memo.reminder.ReminderScheduler
import com.yueji.memo.ui.common.GroupCard
import com.yueji.memo.ui.common.ScreenBody
import com.yueji.memo.ui.common.ScreenPadding
import com.yueji.memo.ui.common.SectionHeader
import com.yueji.memo.ui.common.YueJiScaffold
import com.yueji.memo.ui.common.HSpace
import java.time.LocalTime

private const val MAX_SLOTS = 8

/**
 * Reminder configuration: the user picks one or many times of day for the daily
 * digest, chooses what it contains, and can verify permissions on the spot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    val notificationsOn = remember(permissionTick) { Notifications.canPost(context) }
    val exactAllowed = remember(permissionTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun commit(transform: (com.yueji.memo.data.AppSettings) -> com.yueji.memo.data.AppSettings) {
        SettingsStore.update(transform)
        AppActions.applySettings(context)
    }

    YueJiScaffold(
        title = "提醒设置",
        subtitle = "每日推送与默认提醒规则",
        onBack = onBack,
    ) { padding ->
        ScreenBody(padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding),
            ) {
                // ---------------------------------------------------- permissions
                if (!notificationsOn || !exactAllowed) {
                    SectionHeader("需要授权")
                    GroupCard {
                        if (!notificationsOn) {
                            PermissionRow(
                                title = "通知权限未开启",
                                hint = "没有通知权限，所有提醒都不会显示。",
                                action = "去开启",
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openNotificationSettings(context)
                                }
                                permissionTick++
                            }
                        }
                        if (!exactAllowed) {
                            PermissionRow(
                                title = "精确闹钟未允许",
                                hint = "系统可能会把提醒推迟，建议开启。",
                                action = "去设置",
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                permissionTick++
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ------------------------------------------------- digest times
                SectionHeader("每日待办推送时间")
                Text(
                    text = "在这些时间点，应用会把今天的待办事项（以及可选的每周事项）汇总成一条通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                if (settings.digestTimes.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = "尚未设置推送时间，今日汇总通知已关闭。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    GroupCard {
                        settings.digestTimes.forEach { slot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                HSpace(12)
                                Text(
                                    text = TimeCodec.formatTime(slot),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    commit { it.copy(digestTimes = it.digestTimes - slot) }
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "移除 ${slot}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { showTimePicker = true },
                        enabled = settings.digestTimes.size < MAX_SLOTS,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        HSpace(6)
                        Text("添加时间")
                    }
                    TextButton(onClick = {
                        commit { it.copy(digestTimes = listOf("08:00", "20:00")) }
                    }) { Text("恢复默认") }
                    TextButton(onClick = {
                        commit { it.copy(digestTimes = emptyList()) }
                    }) { Text("全部关闭") }
                }

                // ------------------------------------------------------- content
                Spacer(Modifier.height(12.dp))
                SectionHeader("推送内容")
                GroupCard {
                    SwitchRow(
                        title = "包含每周固定事项",
                        checked = settings.digestIncludeWeekly,
                        onCheckedChange = { value -> commit { it.copy(digestIncludeWeekly = value) } },
                    )
                }

                Spacer(Modifier.height(12.dp))
                SectionHeader("默认提醒规则")
                Text(
                    text = "新建待办与每周事项时默认勾选的提前提醒，可逐条修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                GroupCard {
                    SwitchRow(
                        title = "开始前 20 分钟提醒",
                        checked = settings.todoRemind20,
                        onCheckedChange = { value -> commit { it.copy(todoRemind20 = value) } },
                    )
                    SwitchRow(
                        title = "开始前 10 分钟提醒",
                        checked = settings.todoRemind10,
                        onCheckedChange = { value -> commit { it.copy(todoRemind10 = value) } },
                    )
                }

                // --------------------------------------------------------- theme
                Spacer(Modifier.height(12.dp))
                SectionHeader("外观")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { commit { it.copy(themeMode = mode) } },
                            label = { Text(label) },
                        )
                    }
                }

                // ----------------------------------------------------- utilities
                Spacer(Modifier.height(12.dp))
                SectionHeader("工具")
                GroupCard {
                    TextButton(onClick = { AppActions.sendTestNotification(context) }) {
                        Text("发送一条测试通知")
                    }
                    TextButton(onClick = {
                        ReminderScheduler.rescheduleAll(context, SettingsStore.state.value)
                        AppActions.sendTestNotification(context)
                    }) {
                        Text("重排全部闹钟并测试")
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionHeader("桌面小组件")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        HSpace(12)
                        Text(
                            text = "长按桌面空白处 → 小组件 → 找到「悦记」→ 拖到桌面。" +
                                "小组件会显示今日待办、完成进度和每周事项，点击标题即可打开应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "悦记 1.0.0 · 本地存储，无需联网",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showTimePicker) {
        val now = LocalTime.now()
        val state = rememberTimePickerState(initialHour = now.hour, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择推送时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val value = String.format("%02d:%02d", state.hour, state.minute)
                    commit { current ->
                        current.copy(digestTimes = (current.digestTimes + value).distinct().sorted())
                    }
                    showTimePicker = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    hint: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
