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
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
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
import com.yueji.memo.data.Todo
import com.yueji.memo.ui.common.EmptyHint
import com.yueji.memo.ui.common.ScreenBody
import com.yueji.memo.ui.common.ScreenPadding
import com.yueji.memo.ui.common.SectionHeader
import com.yueji.memo.ui.common.TodoRowItem
import com.yueji.memo.ui.common.YueJiScaffold
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val quickTimes = listOf("08:00", "09:30", "12:00", "14:00", "18:00", "20:00")

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
fun TodoListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val todos by AppRepository.todos.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pending = remember(todos) { todos.filter { !it.done }.sortedBy { it.startAt } }
    val finished = remember(todos) { todos.filter { it.done }.sortedByDescending { it.startAt } }
    val todayCount = remember(todos) { AppRepository.todosOn(LocalDate.now()).count { !it.done } }

    YueJiScaffold(
        title = "待办清单",
        subtitle = "今天 $todayCount 项 · 共 ${pending.size} 项未完成",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = "新建待办")
            }
        },
    ) { padding ->
        ScreenBody(padding) {
            if (todos.isEmpty()) {
                EmptyHint(
                    icon = Icons.Rounded.Checklist,
                    title = "还没有待办",
                    hint = "新建待办后，会在开始前 20 分钟和 10 分钟提醒你。",
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
                    item { SectionHeader("待完成 · ${pending.size}") }
                    if (pending.isEmpty()) {
                        item {
                            Text(
                                text = "全部待办都已完成 🎉",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(pending, key = { it.id }) { todo ->
                            TodoRowItem(
                                todo = todo,
                                onToggle = { AppActions.toggleTodoDone(context, todo.id) },
                                onClick = { onOpen(todo.id) },
                            )
                        }
                    }
                    if (finished.isNotEmpty()) {
                        item { SectionHeader("已完成 · ${finished.size}", modifier = Modifier.padding(top = 8.dp)) }
                        items(finished, key = { it.id }) { todo ->
                            TodoRowItem(
                                todo = todo,
                                onToggle = { AppActions.toggleTodoDone(context, todo.id) },
                                onClick = { onOpen(todo.id) },
                            )
                        }
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
fun TodoEditScreen(
    todoId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val existing = remember(todoId) {
        todoId?.let { id -> AppRepository.todos.value.firstOrNull { it.id == id } }
    }
    val initial = existing?.start ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)

    var title by remember(todoId) { mutableStateOf(existing?.title.orEmpty()) }
    var detail by remember(todoId) { mutableStateOf(existing?.detail.orEmpty()) }
    var date by remember(todoId) { mutableStateOf(initial.toLocalDate()) }
    var hour by remember(todoId) { mutableIntStateOf(initial.hour) }
    var minute by remember(todoId) { mutableIntStateOf(initial.minute) }
    var remind20 by remember(todoId) { mutableStateOf(existing?.remind20 ?: true) }
    var remind10 by remember(todoId) { mutableStateOf(existing?.remind10 ?: true) }
    var done by remember(todoId) { mutableStateOf(existing?.done ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val startAt = TimeCodec.toMillis(LocalDateTime.of(date, LocalTime.of(hour, minute)))

    fun save() {
        if (title.isBlank()) return
        val todo = existing?.copy(
            title = title.trim(),
            detail = detail.trim(),
            startAt = startAt,
            done = done,
            remind20 = remind20,
            remind10 = remind10,
        ) ?: Todo(
            title = title.trim(),
            detail = detail.trim(),
            startAt = startAt,
            done = false,
            remind20 = remind20,
            remind10 = remind10,
        )
        AppActions.saveTodo(context, todo)
        onBack()
    }

    YueJiScaffold(
        title = if (existing == null) "新建待办" else "编辑待办",
        subtitle = "提前 20 分钟与 10 分钟各提醒一次",
        onBack = onBack,
        actions = {
            IconButton(onClick = { save() }) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "保存",
                    tint = if (title.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
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
                    placeholder = { Text("要做什么？") },
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
                SectionHeader("开始时间")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L, 1L, 2L, 7L).forEach { offset ->
                        val d = LocalDate.now().plusDays(offset)
                        FilterChip(
                            selected = date == d,
                            onClick = { date = d },
                            label = {
                                Text(
                                    when (offset) {
                                        0L -> "今天"
                                        1L -> "明天"
                                        2L -> "后天"
                                        else -> "一周后"
                                    }
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { showDatePicker = true },
                        label = { Text(TimeCodec.formatDateCn(date)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showTimePicker = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(String.format("%02d:%02d", hour, minute)) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "快捷时间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickTimes.take(3).forEach { t ->
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
                    quickTimes.drop(3).forEach { t ->
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
                    SwitchRow(
                        title = "已完成",
                        checked = done,
                        onCheckedChange = { done = it },
                    )
                }

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
                        Text("删除这条待办", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
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
            title = { Text("删除待办") },
            text = { Text("「${existing.title}」及其提醒都会被移除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    AppActions.deleteTodo(context, existing.id)
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
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
