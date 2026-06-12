package com.inkblue.writer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inkblue.writer.ui.bookshelf.BookshelfScreen
import com.inkblue.writer.ui.chapters.ChapterListScreen
import com.inkblue.writer.ui.editor.EditorScreen
import com.inkblue.writer.ui.lore.LoreEditorScreen
import com.inkblue.writer.ui.lore.LoreListScreen
import com.inkblue.writer.ui.outline.OutlineScreen
import com.inkblue.writer.ui.settings.SettingsScreen
import com.inkblue.writer.ui.styles.StyleLibraryScreen

@Composable
fun InkNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "bookshelf") {
        composable("bookshelf") {
            BookshelfScreen(
                onOpenBook = { nav.navigate("book/$it") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenStyles = { nav.navigate("styles") },
            )
        }
        composable("styles") {
            StyleLibraryScreen(onBack = { nav.popBackStack() })
        }
        composable(
            "book/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            ChapterListScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
                onOpenChapter = { nav.navigate("editor/$it") },
                onOpenLore = { nav.navigate("lore/$bookId") },
                onOpenOutline = { nav.navigate("outline/$bookId") },
            )
        }
        composable(
            "outline/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            OutlineScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "lore/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            LoreListScreen(
                bookId = bookId,
                onBack = { nav.popBackStack() },
                onOpenEntry = { nav.navigate("lore_entry/$it") },
            )
        }
        composable(
            "lore_entry/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) { entry ->
            val entryId = entry.arguments?.getLong("entryId") ?: return@composable
            LoreEditorScreen(
                entryId = entryId,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "editor/{chapterId}",
            arguments = listOf(navArgument("chapterId") { type = NavType.LongType }),
        ) { entry ->
            val chapterId = entry.arguments?.getLong("chapterId") ?: return@composable
            EditorScreen(
                chapterId = chapterId,
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
