package com.yueji.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.memo.AppActions
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.SettingsStore
import com.yueji.memo.data.TimeCodec
import com.yueji.memo.ui.common.EntryCard
import com.yueji.memo.ui.common.ScreenBody
import com.yueji.memo.ui.common.ScreenPadding
import com.yueji.memo.ui.common.SectionHeader
import com.yueji.memo.ui.common.TodoRowItem
import com.yueji.memo.ui.common.WeeklyRowItem
import com.yueji.memo.ui.common.YueJiScaffold
import java.time.LocalDate

/**
 * Start menu: greeting, today's overview, the four feature entries and a preview
 * of what is scheduled for today. Every deeper screen is one tap away.
 */
@Composable
fun HomeScreen(
    onOpenNotes: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTodo: (String) -> Unit,
) {
    val notes by AppRepository.notes.collectAsStateWithLifecycle()
    val todos by AppRepository.todos.collectAsStateWithLifecycle()
    val weekly by AppRepository.weekly.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val today = LocalDate.now()
    val todayTodos = remember(todos, today) { AppRepository.todosOn(today) }
    val todayWeekly = remember(weekly, today) { AppRepository.weeklyOn(today) }
    val nextTodo = remember(todos) { AppRepository.upcomingTodos().firstOrNull() }
    val pendingTotal = remember(todos) { todos.count { !it.done } }
    val activeWeekly = remember(weekly) { weekly.count { it.enabled } }
    val pinned = remember(notes) { AppRepository.sortedNotes().firstOrNull() }

    YueJiScaffold(
        title = TimeCodec.greeting(),
        subtitle = "${today.monthValue}月${today.dayOfMonth}日 · ${TimeCodec.fullWeekday(today.dayOfWeek.value)}",
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "提醒设置",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    ) { padding ->
        ScreenBody(padding) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = 4.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { OverviewCard(todayTodos.size, todayTodos.count { it.done }, nextTodo?.let { TimeCodec.formatClock(it.startAt) to it.title }) }

                item { SectionHeader("功能入口", modifier = Modifier.padding(top = 4.dp)) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EntryCard(
                            icon = Icons.Rounded.EditNote,
                            title = "备忘录",
                            subtitle = if (notes.isEmpty()) "记录灵感与想法" else "${notes.size} 篇备忘",
                            accent = Color(0xFF4C6EF5),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenNotes,
                        )
                        EntryCard(
                            icon = Icons.Rounded.Checklist,
                            title = "待办清单",
                            subtitle = when {
                                todos.isEmpty() -> "记录要做的事"
                                pendingTotal == 0 -> "全部已完成"
                                else -> "$pendingTotal 项未完成"
                            },
                            accent = Color(0xFF12B886),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenTodos,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EntryCard(
                            icon = Icons.Rounded.EventRepeat,
                            title = "每周固定",
                            subtitle = if (activeWeekly == 0) "按周循环的事项" else "$activeWeekly 项进行中",
                            accent = Color(0xFF8B5CF6),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenWeekly,
                        )
                        EntryCard(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "提醒设置",
                            subtitle = if (settings.digestTimes.isEmpty()) "未开启每日推送" else "每日 ${settings.digestTimes.size} 次推送",
                            accent = Color(0xFFF08C00),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenSettings,
                        )
                    }
                }

                if (pinned != null) {
                    item {
                        SectionHeader("置顶备忘")
                    }
                    item {
                        Surface(
                            onClick = onOpenNotes,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF4C6EF5).copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Widgets,
                                        contentDescription = null,
                                        tint = Color(0xFF4C6EF5),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pinned.title.ifBlank { "无标题备忘" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = pinned.content.replace('\n', ' ').ifBlank { "暂无内容" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "今日待办",
                        modifier = Modifier.padding(top = 4.dp),
                        trailing = {
                            TextButton(onClick = onOpenTodos) { Text("全部") }
                        },
                    )
                }

                if (todayTodos.isEmpty()) {
                    item { QuietCard("今天没有安排待办，享受轻松的一天吧。") }
                } else {
                    items(todayTodos.take(3), key = { it.id }) { todo ->
                        TodoRowItem(
                            todo = todo,
                            onToggle = { AppActions.toggleTodoDone(context, todo.id) },
                            onClick = { onOpenTodo(todo.id) },
                        )
                    }
                    if (todayTodos.size > 3) {
                        item { QuietCard("还有 ${todayTodos.size - 3} 项今天的待办…") }
                    }
                }

                item {
                    SectionHeader(
                        title = "今日每周事项",
                        modifier = Modifier.padding(top = 4.dp),
                        trailing = {
                            TextButton(onClick = onOpenWeekly) { Text("管理") }
                        },
                    )
                }

                if (todayWeekly.isEmpty()) {
                    item { QuietCard("还没有设置每周重复的事项，可在「每周固定」里添加。") }
                } else {
                    items(todayWeekly, key = { it.id }) { item ->
                        WeeklyRowItem(
                            item = item,
                            onToggle = { AppActions.toggleWeeklyEnabled(context, item.id) },
                            onClick = onOpenWeekly,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(total: Int, done: Int, next: Pair<String, String>?) {
    val ratio = if (total == 0) 0f else done.toFloat() / total.toFloat()
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(start, end)))
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = "今日概览",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (total == 0) "暂无待办" else "$done / $total 已完成",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.28f),
                // Material 3 draws a "stop indicator" dot at the end of the track;
                // at 0% it reads as a rendering bug, so drop it.
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = next?.let { "下一项 ${it.first} · ${it.second}" } ?: "没有即将到来的待办",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuietCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
