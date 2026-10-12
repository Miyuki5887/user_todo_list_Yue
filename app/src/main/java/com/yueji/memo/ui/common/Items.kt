package com.yueji.memo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yueji.memo.data.Note
import com.yueji.memo.data.NoteTint
import com.yueji.memo.data.TimeCodec
import com.yueji.memo.data.Todo
import com.yueji.memo.data.WeeklyItem
import java.time.LocalDate

// ---------------------------------------------------------------------------
// List row building blocks shared by the start menu and the detail lists.
// ---------------------------------------------------------------------------

@Composable
fun todoTimeLabel(todo: Todo): String {
    val dt = TimeCodec.toDateTime(todo.startAt)
    val today = LocalDate.now()
    val clock = TimeCodec.formatClock(todo.startAt)
    return when (dt.toLocalDate()) {
        today -> "今天 $clock"
        today.plusDays(1) -> "明天 $clock"
        today.minusDays(1) -> "昨天 $clock"
        else -> "${dt.monthValue}月${dt.dayOfMonth}日 $clock"
    }
}

@Composable
fun TodoRowItem(
    todo: Todo,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (todo.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (todo.done) "标记为未完成" else "标记为已完成",
                    tint = if (todo.done) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title.ifBlank { "未命名待办" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (todo.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (todo.detail.isNotBlank()) {
                    Text(
                        text = todo.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Pill(
                        text = todoTimeLabel(todo),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (!todo.done && todo.remind20) Pill("提前20分钟")
                    if (!todo.done && todo.remind10) Pill("提前10分钟")
                }
            }
        }
    }
}

private val tintDay: Map<NoteTint, Color> = mapOf(
    NoteTint.DEFAULT to Color(0xFF4C6EF5),
    NoteTint.SAND to Color(0xFFF08C00),
    NoteTint.MINT to Color(0xFF12B886),
    NoteTint.SKY to Color(0xFF1C9BD8),
    NoteTint.LILAC to Color(0xFF8B5CF6),
    NoteTint.ROSE to Color(0xFFE0457B),
)

fun noteTintColor(tint: NoteTint): Color = tintDay[tint] ?: Color(0xFF4C6EF5)

@Composable
fun NoteRowItem(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .background(noteTintColor(note.tint), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { "无标题备忘" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = note.content.replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "更新于 ${TimeCodec.formatDateCn(TimeCodec.toDateTime(note.updatedAt).toLocalDate())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = if (note.pinned) "取消置顶" else "置顶",
                    tint = if (note.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun WeeklyRowItem(
    item: WeeklyItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (item.enabled) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (item.enabled) "暂停提醒" else "启用提醒",
                    tint = if (item.enabled) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "未命名事项" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (item.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        text = "每周 ${item.dayLabels}",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Pill(item.time, container = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
