package com.yueji.memo.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Single source of truth for notes / todos / weekly items.
 *
 * Storage is a trio of small JSON documents inside the app's private files dir,
 * written through [scope] on a background dispatcher and exposed as
 * [StateFlow]s. This keeps the project free of annotation processors (no Room /
 * KSP), which makes the build reproducible on a bare toolchain.
 */
object AppRepository {

    private const val TAG = "AppRepository"
    private const val FILE_NOTES = "notes.json"
    private const val FILE_TODOS = "todos.json"
    private const val FILE_WEEKLY = "weekly.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null
    @Volatile private var loaded = false

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    private val _weekly = MutableStateFlow<List<WeeklyItem>>(emptyList())

    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()
    val weekly: StateFlow<List<WeeklyItem>> = _weekly.asStateFlow()

    /** Idempotent; safe to call from Application, receivers and the widget. */
    fun ensureInit(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            appContext = context.applicationContext
            loadAll(context.applicationContext)
            loaded = true
        }
    }

    // ------------------------------------------------------------- loading

    private fun loadAll(context: Context) {
        _notes.value = readArray(context, FILE_NOTES).mapNotNull { parseNote(it) }
        _todos.value = readArray(context, FILE_TODOS).mapNotNull { parseTodo(it) }
        _weekly.value = readArray(context, FILE_WEEKLY).mapNotNull { parseWeekly(it) }
    }

    private fun readArray(context: Context, name: String): List<JSONObject> {
        val file = File(context.filesDir, name)
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONArray(file.readText())
            (0 until root.length()).map { root.getJSONObject(it) }
        }.onFailure { Log.w(TAG, "failed reading $name", it) }.getOrDefault(emptyList())
    }

    private fun writeArray(name: String, array: JSONArray) {
        val context = appContext ?: return
        runCatching { File(context.filesDir, name).writeText(array.toString()) }
            .onFailure { Log.w(TAG, "failed writing $name", it) }
    }

    private fun persistNotes() = persist(FILE_NOTES) { _notes.value.map(::encodeNote) }
    private fun persistTodos() = persist(FILE_TODOS) { _todos.value.map(::encodeTodo) }
    private fun persistWeekly() = persist(FILE_WEEKLY) { _weekly.value.map(::encodeWeekly) }

    private inline fun persist(name: String, build: () -> List<JSONObject>) {
        val payload = JSONArray().apply { build().forEach { put(it) } }
        scope.launch { writeArray(name, payload) }
        onMutated()
    }

    /** Hook for the home-screen widget: refresh whenever the data changes. */
    private fun onMutated() {
        val context = appContext ?: return
        WidgetBridge.requestRefresh(context)
    }

    // ---------------------------------------------------------------- notes

    fun upsertNote(note: Note) {
        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        _notes.value = _notes.value.let { list ->
            val idx = list.indexOfFirst { it.id == stamped.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = stamped } else list + stamped
        }
        persistNotes()
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filterNot { it.id == id }
        persistNotes()
    }

    fun toggleNotePinned(id: String) {
        _notes.value = _notes.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        persistNotes()
    }

    // ---------------------------------------------------------------- todos

    fun upsertTodo(todo: Todo) {
        _todos.value = _todos.value.let { list ->
            val idx = list.indexOfFirst { it.id == todo.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = todo } else list + todo
        }
        persistTodos()
    }

    fun deleteTodo(id: String) {
        _todos.value = _todos.value.filterNot { it.id == id }
        persistTodos()
    }

    fun toggleTodoDone(id: String) {
        _todos.value = _todos.value.map { if (it.id == id) it.copy(done = !it.done) else it }
        persistTodos()
    }

    fun findTodo(id: String): Todo? = _todos.value.firstOrNull { it.id == id }

    // --------------------------------------------------------------- weekly

    fun upsertWeekly(item: WeeklyItem) {
        _weekly.value = _weekly.value.let { list ->
            val idx = list.indexOfFirst { it.id == item.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = item } else list + item
        }
        persistWeekly()
    }

    fun deleteWeekly(id: String) {
        _weekly.value = _weekly.value.filterNot { it.id == id }
        persistWeekly()
    }

    fun toggleWeeklyEnabled(id: String) {
        _weekly.value = _weekly.value.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        persistWeekly()
    }

    fun findWeekly(id: String): WeeklyItem? = _weekly.value.firstOrNull { it.id == id }

    // ----------------------------------------------------------- selectors

    fun todosOn(date: LocalDate): List<Todo> =
        _todos.value.filter { TimeCodec.toDateTime(it.startAt).toLocalDate() == date }
            .sortedBy { it.startAt }

    fun upcomingTodos(from: LocalDateTime = LocalDateTime.now()): List<Todo> =
        _todos.value.filter { !it.done && TimeCodec.toDateTime(it.startAt).isAfter(from) }
            .sortedBy { it.startAt }

    fun weeklyOn(date: LocalDate): List<WeeklyItem> =
        _weekly.value.filter { it.enabled && it.days.contains(date.dayOfWeek.value) }
            .sortedBy { it.localTime }

    fun activeWeekly(): List<WeeklyItem> = _weekly.value.filter { it.enabled }

    fun sortedNotes(): List<Note> =
        _notes.value.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    fun searchNotes(query: String): List<Note> {
        if (query.isBlank()) return sortedNotes()
        val q = query.trim()
        return sortedNotes().filter {
            it.title.contains(q, true) || it.content.contains(q, true)
        }
    }

    // ------------------------------------------------------- widget facade

    fun widgetSnapshot(): WidgetSnapshot {
        val today = LocalDate.now()
        val todayTodos = todosOn(today)
        val todayWeekly = weeklyOn(today)
        return WidgetSnapshot(
            dateLabel = TimeCodec.formatDateCn(today),
            todos = todayTodos.take(4).map {
                WidgetTodo(it.id, it.title.ifBlank { "未命名待办" }, TimeCodec.formatClock(it.startAt), it.done)
            },
            todoTotal = todayTodos.size,
            todoDone = todayTodos.count { it.done },
            weekly = todayWeekly.take(3).map {
                WidgetWeekly(it.title.ifBlank { "每周事项" }, it.time)
            },
            noteCount = _notes.value.size,
            pinnedNote = _notes.value.firstOrNull { it.pinned }?.title?.ifBlank { null },
        )
    }

    // ------------------------------------------------------ json encoding

    private fun encodeNote(n: Note) = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("content", n.content)
        put("pinned", n.pinned); put("tint", n.tint.name)
        put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
    }

    private fun parseNote(o: JSONObject) = runCatching {
        Note(
            id = o.getString("id"),
            title = o.optString("title"),
            content = o.optString("content"),
            pinned = o.optBoolean("pinned"),
            tint = runCatching { NoteTint.valueOf(o.optString("tint", "DEFAULT")) }.getOrDefault(NoteTint.DEFAULT),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }.getOrNull()

    private fun encodeTodo(t: Todo) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("detail", t.detail)
        put("startAt", t.startAt); put("done", t.done)
        put("remind20", t.remind20); put("remind10", t.remind10)
        put("createdAt", t.createdAt)
    }

    private fun parseTodo(o: JSONObject) = runCatching {
        Todo(
            id = o.getString("id"),
            title = o.optString("title"),
            detail = o.optString("detail"),
            startAt = o.optLong("startAt", System.currentTimeMillis()),
            done = o.optBoolean("done"),
            remind20 = o.optBoolean("remind20", true),
            remind10 = o.optBoolean("remind10", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }.getOrNull()

    private fun encodeWeekly(w: WeeklyItem) = JSONObject().apply {
        put("id", w.id); put("title", w.title); put("detail", w.detail)
        put("days", JSONArray(w.sortedDays))
        put("time", w.time); put("enabled", w.enabled)
        put("remind20", w.remind20); put("remind10", w.remind10)
        put("createdAt", w.createdAt)
    }

    private fun parseWeekly(o: JSONObject) = runCatching {
        val daysJson = o.optJSONArray("days")
        val days = buildSet {
            if (daysJson != null) for (i in 0 until daysJson.length()) add(daysJson.optInt(i))
        }.ifEmpty { setOf(1) }
        WeeklyItem(
            id = o.getString("id"),
            title = o.optString("title"),
            detail = o.optString("detail"),
            days = days,
            time = o.optString("time", "09:00"),
            enabled = o.optBoolean("enabled", true),
            remind20 = o.optBoolean("remind20", true),
            remind10 = o.optBoolean("remind10", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }.getOrNull()
}

data class WidgetTodo(val id: String, val title: String, val time: String, val done: Boolean)
data class WidgetWeekly(val title: String, val time: String)

data class WidgetSnapshot(
    val dateLabel: String,
    val todos: List<WidgetTodo>,
    val todoTotal: Int,
    val todoDone: Int,
    val weekly: List<WidgetWeekly>,
    val noteCount: Int,
    val pinnedNote: String?,
)

/**
 * Decouples the repository from the (optional) Glance widget so the data layer
 * never has to know about appwidgets. [com.yueji.memo.widget.TodayWidgetBridge]
 * registers the real implementation at application start-up.
 */
object WidgetBridge {
    @Volatile private var refresh: ((Context) -> Unit)? = null

    fun install(block: (Context) -> Unit) { refresh = block }

    fun requestRefresh(context: Context) {
        runCatching { refresh?.invoke(context) }
    }
}
