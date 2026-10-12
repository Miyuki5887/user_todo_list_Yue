package com.yueji.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.memo.AppActions
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.Note
import com.yueji.memo.data.NoteTint
import com.yueji.memo.ui.common.EmptyHint
import com.yueji.memo.ui.common.NoteRowItem
import com.yueji.memo.ui.common.ScreenBody
import com.yueji.memo.ui.common.ScreenPadding
import com.yueji.memo.ui.common.YueJiScaffold
import com.yueji.memo.ui.common.noteTintColor

private val tintOrder = listOf(
    NoteTint.DEFAULT,
    NoteTint.SAND,
    NoteTint.MINT,
    NoteTint.SKY,
    NoteTint.LILAC,
    NoteTint.ROSE,
)

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
fun NoteListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val notes by AppRepository.notes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    val visible = remember(notes, query) {
        if (query.isBlank()) AppRepository.sortedNotes() else AppRepository.searchNotes(query)
    }

    YueJiScaffold(
        title = "备忘录",
        subtitle = "${notes.size} 篇 · ${notes.count { it.pinned }} 篇置顶",
        onBack = onBack,
        actions = {
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(
                    imageVector = if (searchOpen) Icons.Rounded.Clear else Icons.Rounded.Search,
                    contentDescription = if (searchOpen) "关闭搜索" else "搜索",
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = "新建备忘")
            }
        },
    ) { padding ->
        ScreenBody(padding) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (searchOpen) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 4.dp),
                        placeholder = { Text("搜索标题或内容") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                if (visible.isEmpty()) {
                    EmptyHint(
                        icon = Icons.Rounded.EditNote,
                        title = if (query.isBlank()) "还没有备忘" else "没有匹配结果",
                        hint = if (query.isBlank()) "点击右下角按钮，记录第一条想法。" else "换个关键词试试。",
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
                        items(visible, key = { it.id }) { note ->
                            NoteRowItem(
                                note = note,
                                onClick = { onOpen(note.id) },
                                onTogglePin = { AppActions.toggleNotePinned(note.id) },
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

@Composable
fun NoteEditScreen(
    noteId: String?,
    onBack: () -> Unit,
) {
    val existing = remember(noteId) {
        noteId?.let { id -> AppRepository.notes.value.firstOrNull { it.id == id } }
    }
    var title by remember(noteId) { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember(noteId) { mutableStateOf(existing?.content.orEmpty()) }
    var tint by remember(noteId) { mutableStateOf(existing?.tint ?: NoteTint.DEFAULT) }
    var pinned by remember(noteId) { mutableStateOf(existing?.pinned ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        if (title.isBlank() && content.isBlank()) {
            onBack()
            return
        }
        val now = System.currentTimeMillis()
        val note = existing?.copy(
            title = title.trim(),
            content = content.trim(),
            tint = tint,
            pinned = pinned,
            updatedAt = now,
        ) ?: Note(
            title = title.trim(),
            content = content.trim(),
            tint = tint,
            pinned = pinned,
            createdAt = now,
            updatedAt = now,
        )
        AppActions.saveNote(note)
        onBack()
    }

    YueJiScaffold(
        title = if (existing == null) "新建备忘" else "编辑备忘",
        subtitle = if (existing == null) "随手记下此刻的想法" else "修改后点击右上角保存",
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconButton(onClick = { pinned = !pinned }) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (pinned) "取消置顶" else "置顶",
                        tint = if (pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            IconButton(onClick = { save() }) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "保存",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { padding ->
        ScreenBody(padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScreenPadding),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("标题") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "标记颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tintOrder.forEach { option ->
                        val selected = option == tint
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(noteTintColor(option))
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { tint = option },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text("正文内容…") },
                    shape = RoundedCornerShape(16.dp),
                )
                if (existing != null) {
                    Spacer(Modifier.height(8.dp))
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
                        Text("删除这条备忘", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除备忘") },
            text = { Text("「${existing.title.ifBlank { "无标题备忘" }}」将被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    AppActions.deleteNote(existing.id)
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
