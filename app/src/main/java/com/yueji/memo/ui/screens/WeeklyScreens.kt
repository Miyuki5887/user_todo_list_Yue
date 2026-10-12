package com.yueji.memo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.TimeCodec
import com.yueji.memo.data.WeeklyItem
import com.yueji.memo.ui.common.EmptyHint
import com.yueji.memo.ui.common.ScreenBody
import com.yueji.memo.ui.common.ScreenPadding
import com.yueji.memo.ui.common.SectionHeader
import com.yueji.memo.ui.common.WeeklyRowItem
import com.yueji.memo.ui.common.YueJiScaffold
import java.time.LocalDate

private val weekDays = listOf(1, 2, 3, 4, 5, 6, 7)
private val weekQuickTimes = listOf("07:30", "09:00", "12:30", "18:00", "20:00", "21:30")

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
fun WeeklyListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val weekly by AppRepository.weekly.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ordered = remember(weekly) {
        weekly.sortedWith(compareByDescending<WeeklyItem> { it.enabled }.thenBy { it.localTime })
    }

    YueJiScaffold(
        title = "每周固定",
        subtitle = "${weekly.count { it.enabled }} 项进行中 · 共 ${weekly.size} 项",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = "新增每周事项")
            }
        },
    ) { padding ->
        ScreenBody(padding) {
            if (weekly.isEmpty()) {
                EmptyHint(
                    icon = Icons.Rounded.EventRepeat,
                    title = "还没有每周事项",
                    hint = "比如「每周一 09:00 晨会」，到点会收到通知。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ordered, key = { it.id }) { item ->
                        WeeklyRowItem(
                            item = item,
                            onToggle = { AppActions.toggleWeeklyEnabled(context, item.id) },
                            onClick = { onOpen(item.id) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyEditScreen(
    itemId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val existing = remember(itemId) {
        itemId?.let { id -> AppRepository.weekly.value.firstOrNull { it.id == id } }
    }
    val initialTime = existing?.localTime ?: TimeCodec.toTime("09:00")

    var title by remember(itemId) { mutableStateOf(existing?.title.orEmpty()) }
    var detail by remember(itemId) { mutableStateOf(existing?.detail.orEmpty()) }
    var days by remember(itemId) {
        mutableStateOf(existing?.days ?: setOf(LocalDate.now().dayOfWeek.value))
    }
    var hour by remember(itemId) { mutableIntStateOf(initialTime.hour) }
    var minute by remember(itemId) { mutableIntStateOf(initialTime.minute) }
    var enabled by remember(itemId) { mutableStateOf(existing?.enabled ?: true) }
    var remind20 by remember(itemId) { mutableStateOf(existing?.remind20 ?: true) }
    var remind10 by remember(itemId) { mutableStateOf(existing?.remind10 ?: true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val timeText = String.format("%02d:%02d", hour, minute)
    val canSave = title.isNotBlank() && days.isNotEmpty()

    fun save() {
        if (!canSave) return
        val item = existing?.copy(
            title = title.trim(),
            detail = detail.trim(),
            days = days,
            time = timeText,
            enabled = enabled,
            remind20 = remind20,
            remind10 = remind10,
        ) ?: WeeklyItem(
            title = title.trim(),
            detail = detail.trim(),
            days = days,
            time = timeText,
            enabled = enabled,
            remind20 = remind20,
            remind10 = remind10,
        )
        AppActions.saveWeekly(context, item)
        onBack()
    }

    YueJiScaffold(
        title = if (existing == null) "新增每周事项" else "编辑每周事项",
        subtitle = "选定星期与时间，按周循环提醒",
        onBack = onBack,
        actions = {
            IconButton(onClick = { save() }) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "保存",
                    tint = if (canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    ) { padding ->
        ScreenBody(padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("事项名称，如「周会」") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("备注（可选）") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(Modifier.height(20.dp))
                SectionHeader("重复星期")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays.take(4).forEach { day ->
                        DayChip(day = day, selected = day in days) {
                            days = if (day in days) days - day else days + day
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays.drop(4).forEach { day ->
                        DayChip(day = day, selected = day in days) {
                            days = if (day in days) days - day else days + day
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { days = weekDays.toSet() }) { Text("每天") }
                    TextButton(onClick = { days = setOf(1, 2, 3, 4, 5) }) { Text("工作日") }
                    TextButton(onClick = { days = setOf(6, 7) }) { Text("周末") }
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("时间")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { showTimePicker = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(timeText) },
                    )
                    weekQuickTimes.take(3).forEach { t ->
                        val lt = TimeCodec.toTime(t)
                        FilterChip(
                            selected = hour == lt.hour && minute == lt.minute,
                            onClick = { hour = lt.hour; minute = lt.minute },
                            label = { Text(t) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekQuickTimes.drop(3).forEach { t ->
                        val lt = TimeCodec.toTime(t)
                        FilterChip(
                            selected = hour == lt.hour && minute == lt.minute,
                            onClick = { hour = lt.hour; minute = lt.minute },
                            label = { Text(t) },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionHeader("提醒")
                SwitchRow(
                    title = "启用这项每周提醒",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
                SwitchRow(
                    title = "开始前 20 分钟提醒",
                    checked = remind20,
                    onCheckedChange = { remind20 = it },
                )
                SwitchRow(
                    title = "开始前 10 分钟提醒",
                    checked = remind10,
                    onCheckedChange = { remind10 = it },
                )

                if (existing != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("删除这项", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除每周事项") },
            text = { Text("「${existing.title}」的循环提醒会被取消。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    AppActions.deleteWeekly(context, existing.id)
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DayChip(day: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(TimeCodec.shortWeekday(day)) },
    )
}
