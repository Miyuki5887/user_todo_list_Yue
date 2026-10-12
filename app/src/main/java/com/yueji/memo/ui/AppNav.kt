package com.yueji.memo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yueji.memo.ui.screens.HomeScreen
import com.yueji.memo.ui.screens.NoteEditScreen
import com.yueji.memo.ui.screens.NoteListScreen
import com.yueji.memo.ui.screens.SettingsScreen
import com.yueji.memo.ui.screens.TodoEditScreen
import com.yueji.memo.ui.screens.TodoListScreen
import com.yueji.memo.ui.screens.WeeklyEditScreen
import com.yueji.memo.ui.screens.WeeklyListScreen

/** Every route in the app. Optional ids travel as query arguments. */
object Routes {
    const val HOME = "home"
    const val NOTES = "notes"
    const val NOTE_EDIT = "note_edit"
    const val TODOS = "todos"
    const val TODO_EDIT = "todo_edit"
    const val WEEKLY = "weekly"
    const val WEEKLY_EDIT = "weekly_edit"
    const val SETTINGS = "settings"

    const val ARG_NOTE = "noteId"
    const val ARG_TODO = "todoId"
    const val ARG_WEEKLY = "weeklyId"

    fun noteEdit(id: String?): String = "$NOTE_EDIT?$ARG_NOTE=${id.orEmpty()}"
    fun todoEdit(id: String?): String = "$TODO_EDIT?$ARG_TODO=${id.orEmpty()}"
    fun weeklyEdit(id: String?): String = "$WEEKLY_EDIT?$ARG_WEEKLY=${id.orEmpty()}"
}

@Composable
fun AppNav(
    focusTodoId: String?,
    onFocusConsumed: () -> Unit,
) {
    val nav = rememberNavController()

    // A notification tap deep-links into the to-do editor.
    LaunchedEffect(focusTodoId) {
        if (!focusTodoId.isNullOrBlank()) {
            nav.navigate(Routes.todoEdit(focusTodoId))
            onFocusConsumed()
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenNotes = { nav.navigate(Routes.NOTES) },
                onOpenTodos = { nav.navigate(Routes.TODOS) },
                onOpenWeekly = { nav.navigate(Routes.WEEKLY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenTodo = { id -> nav.navigate(Routes.todoEdit(id)) },
            )
        }

        composable(Routes.NOTES) {
            NoteListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.noteEdit(id)) },
                onCreate = { nav.navigate(Routes.noteEdit(null)) },
            )
        }

        composable(
            route = "${Routes.NOTE_EDIT}?${Routes.ARG_NOTE}={${Routes.ARG_NOTE}}",
            arguments = listOf(
                navArgument(Routes.ARG_NOTE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            NoteEditScreen(
                noteId = remember(entry) {
                    entry.arguments?.getString(Routes.ARG_NOTE)?.takeIf { it.isNotBlank() }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.TODOS) {
            TodoListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.todoEdit(id)) },
                onCreate = { nav.navigate(Routes.todoEdit(null)) },
            )
        }

        composable(
            route = "${Routes.TODO_EDIT}?${Routes.ARG_TODO}={${Routes.ARG_TODO}}",
            arguments = listOf(
                navArgument(Routes.ARG_TODO) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            TodoEditScreen(
                todoId = remember(entry) {
                    entry.arguments?.getString(Routes.ARG_TODO)?.takeIf { it.isNotBlank() }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.WEEKLY) {
            WeeklyListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.weeklyEdit(id)) },
                onCreate = { nav.navigate(Routes.weeklyEdit(null)) },
            )
        }

        composable(
            route = "${Routes.WEEKLY_EDIT}?${Routes.ARG_WEEKLY}={${Routes.ARG_WEEKLY}}",
            arguments = listOf(
                navArgument(Routes.ARG_WEEKLY) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            WeeklyEditScreen(
                itemId = remember(entry) {
                    entry.arguments?.getString(Routes.ARG_WEEKLY)?.takeIf { it.isNotBlank() }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
