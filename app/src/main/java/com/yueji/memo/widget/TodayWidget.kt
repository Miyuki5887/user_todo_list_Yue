package com.yueji.memo.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.yueji.memo.MainActivity
import com.yueji.memo.data.AppRepository
import com.yueji.memo.data.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget: today's schedule at a glance.
 *
 * Tapping a to-do marks it done, tapping the header opens the app. The widget
 * re-renders whenever the repository mutates (see [WidgetRefresh]).
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 150.dp),
            DpSize(320.dp, 220.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppRepository.ensureInit(context)
        provideContent { WidgetBody(AppRepository.widgetSnapshot()) }
    }
}

/** Manifest-registered receiver for [TodayWidget]. */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/**
 * Bridges repository mutations to widget re-renders. Installed once from
 * `YueJiApp.onCreate` so the data layer stays widget-agnostic.
 */
object WidgetRefresh {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun install() {
        com.yueji.memo.data.WidgetBridge.install { context ->
            scope.launch { updateAll(context) }
            Unit
        }
    }

    /** Suspend variant used by the action callbacks. */
    suspend fun refreshNow(context: Context) {
        updateAll(context)
    }

    /**
     * Glance 1.1.1's [GlanceAppWidget] only exposes `update(context, glanceId)`,
     * so enumerate every placed instance of the widget ourselves.
     */
    private suspend fun updateAll(context: Context) {
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            val widget = TodayWidget()
            manager.getGlanceIds(TodayWidget::class.java).forEach { id ->
                widget.update(context, id)
            }
        }.onFailure { Log.w("YueJiWidget", "widget refresh failed", it) }
    }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

private val dayBg = Color(0xFFFFFFFF)
private val nightBg = Color(0xFF191C24)
private val dayFg = Color(0xFF1A1D26)
private val nightFg = Color(0xFFECEFF5)
private val dayMuted = Color(0xFF6B7280)
private val nightMuted = Color(0xFF9AA3B2)
private val dayAccent = Color(0xFF4C6EF5)
private val nightAccent = Color(0xFF8FA6FF)
private val dayDone = Color(0xFF9CA3AF)
private val nightDone = Color(0xFF6B7280)

private val bg = ColorProvider(dayBg, nightBg)
private val fg = ColorProvider(dayFg, nightFg)
private val muted = ColorProvider(dayMuted, nightMuted)
private val accent = ColorProvider(dayAccent, nightAccent)
private val doneColor = ColorProvider(dayDone, nightDone)

private val TodoIdKey = ActionParameters.Key<String>("yueji.widgetTodoId")

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(14.dp),
    ) {
        Header(snapshot)
        Spacer(GlanceModifier.height(8.dp))

        if (snapshot.todos.isEmpty()) {
            Text(
                text = "今天没有待办，轻松一下",
                style = TextStyle(color = muted, fontSize = 13.sp),
            )
        } else {
            snapshot.todos.forEach { todo -> TodoRow(todo) }
        }

        if (snapshot.weekly.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            snapshot.weekly.forEach { weekly ->
                Text(
                    text = "↻  ${weekly.time}  ${weekly.title}",
                    style = TextStyle(color = accent, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.defaultWeight())
        Footer(snapshot)
    }
}

@Composable
private fun Header(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "悦记 · 今日",
            style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = snapshot.dateLabel,
            style = TextStyle(color = muted, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun TodoRow(todo: com.yueji.memo.data.WidgetTodo) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(actionRunCallback<ToggleTodoAction>(actionParametersOf(TodoIdKey to todo.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (todo.done) "✓" else "○",
            style = TextStyle(
                color = if (todo.done) doneColor else accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = todo.time,
            style = TextStyle(color = muted, fontSize = 12.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = todo.title,
            style = TextStyle(
                color = if (todo.done) doneColor else fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Footer(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (snapshot.todoTotal == 0) {
                "备忘 ${snapshot.noteCount} 篇"
            } else {
                "完成 ${snapshot.todoDone}/${snapshot.todoTotal} · 备忘 ${snapshot.noteCount} 篇"
            },
            style = TextStyle(color = muted, fontSize = 11.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = "打开",
            style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        )
    }
}

/** Marks a to-do done/undone straight from the widget. */
class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[TodoIdKey] ?: return
        AppRepository.ensureInit(context)
        AppRepository.toggleTodoDone(todoId)
        WidgetRefresh.refreshNow(context)
    }
}
