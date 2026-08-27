package com.kanshu.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kanshu.reader.reader.BookFormat
import com.kanshu.reader.ui.ai.AiChatScreen
import com.kanshu.reader.ui.ai.AiChatViewModel
import com.kanshu.reader.ui.ai.AiCreateScreen
import com.kanshu.reader.ui.ai.AiCreateViewModel
import com.kanshu.reader.ui.ai.AiSessionsScreen
import com.kanshu.reader.ui.ai.AiSessionsViewModel
import com.kanshu.reader.ui.library.LibraryScreen
import com.kanshu.reader.ui.library.LibraryViewModel
import com.kanshu.reader.ui.music.MiniPlayerBar
import com.kanshu.reader.ui.music.MusicScreen
import com.kanshu.reader.ui.music.MusicViewModel
import com.kanshu.reader.ui.reader.PdfReaderScreen
import com.kanshu.reader.ui.reader.PdfReaderViewModel
import com.kanshu.reader.ui.reader.ReaderScreen
import com.kanshu.reader.ui.reader.ReaderViewModel
import com.kanshu.reader.ui.theme.KanshuTheme
import com.kanshu.reader.ui.write.WriteScreen
import com.kanshu.reader.ui.write.WriteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KanshuApp

        setContent {
            val themeMode by app.container.themePreferences.themeMode
                .collectAsStateWithLifecycle(
                    initialValue = com.kanshu.reader.data.prefs.AppThemeMode.DAY
                )

            KanshuTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = "library"
                        ) {
                            composable("library") {
                                val vm: LibraryViewModel = viewModel(
                                    factory = LibraryViewModel.factory(
                                        app.container.bookRepository,
                                        app.container.themePreferences,
                                        app.container.defaultBooksSync,
                                        app.container.githubBooksUploader
                                    )
                                )
                                LibraryScreen(
                                    viewModel = vm,
                                    onOpenBook = { id ->
                                        navController.navigate("open/$id")
                                    },
                                    onWrite = { folderId ->
                                        if (folderId != null) {
                                            navController.navigate("write/folder/$folderId")
                                        } else {
                                            navController.navigate("write")
                                        }
                                    },
                                    onEditBook = { id ->
                                        navController.navigate("write/edit/$id")
                                    },
                                    onAiChat = {
                                        navController.navigate("ai/sessions")
                                    },
                                    onMusic = {
                                        navController.navigate("music")
                                    }
                                )
                            }

                            composable("music") {
                                val vm: MusicViewModel = viewModel(
                                    factory = MusicViewModel.factory(
                                        app.container.musicRepository,
                                        app.container.musicController,
                                        app.container.musicSync,
                                        app.container.githubMusicUploader
                                    )
                                )
                                MusicScreen(
                                    viewModel = vm,
                                    musicController = app.container.musicController,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("ai/sessions") {
                                val vm: AiSessionsViewModel = viewModel(
                                    factory = AiSessionsViewModel.factory(
                                        app.container.aiChatRepository,
                                        app.container.themePreferences
                                    )
                                )
                                AiSessionsScreen(
                                    viewModel = vm,
                                    onBack = { navController.popBackStack() },
                                    onCreate = { navController.navigate("ai/create") },
                                    onOpenSession = { id ->
                                        navController.navigate("ai/chat/$id")
                                    }
                                )
                            }
                            composable("ai/create") {
                                val vm: AiCreateViewModel = viewModel(
                                    factory = AiCreateViewModel.factory(app.container.aiChatRepository)
                                )
                                AiCreateScreen(
                                    viewModel = vm,
                                    onBack = { navController.popBackStack() },
                                    onSessionReady = { id ->
                                        navController.navigate("ai/chat/$id") {
                                            popUpTo("ai/create") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(
                                route = "ai/chat/{sessionId}",
                                arguments = listOf(
                                    navArgument("sessionId") { type = NavType.LongType }
                                )
                            ) { entry ->
                                val sessionId = entry.arguments?.getLong("sessionId")
                                    ?: return@composable
                                val vm: AiChatViewModel = viewModel(
                                    factory = AiChatViewModel.factory(
                                        sessionId,
                                        app.container.aiChatRepository
                                    )
                                )
                                AiChatScreen(
                                    viewModel = vm,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("write") {
                                val vm: WriteViewModel = viewModel(
                                    factory = WriteViewModel.factory(
                                        bookId = null,
                                        folderId = null,
                                        bookRepository = app.container.bookRepository,
                                        themePreferences = app.container.themePreferences,
                                        githubBooksUploader = app.container.githubBooksUploader
                                    )
                                )
                                WriteScreen(
                                    viewModel = vm,
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "write/folder/{folderId}",
                                arguments = listOf(
                                    navArgument("folderId") { type = NavType.LongType }
                                )
                            ) { entry ->
                                val folderId = entry.arguments?.getLong("folderId")
                                val vm: WriteViewModel = viewModel(
                                    factory = WriteViewModel.factory(
                                        bookId = null,
                                        folderId = folderId,
                                        bookRepository = app.container.bookRepository,
                                        themePreferences = app.container.themePreferences,
                                        githubBooksUploader = app.container.githubBooksUploader
                                    )
                                )
                                WriteScreen(
                                    viewModel = vm,
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "write/edit/{bookId}",
                                arguments = listOf(
                                    navArgument("bookId") { type = NavType.LongType }
                                )
                            ) { entry ->
                                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                                val vm: WriteViewModel = viewModel(
                                    factory = WriteViewModel.factory(
                                        bookId = bookId,
                                        folderId = null,
                                        bookRepository = app.container.bookRepository,
                                        themePreferences = app.container.themePreferences,
                                        githubBooksUploader = app.container.githubBooksUploader
                                    )
                                )
                                WriteScreen(
                                    viewModel = vm,
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "open/{bookId}",
                                arguments = listOf(
                                    navArgument("bookId") { type = NavType.LongType }
                                )
                            ) { entry ->
                                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                                LaunchedEffect(bookId) {
                                    val book = app.container.bookRepository.getBook(bookId)
                                    val format = BookFormat.fromStored(book?.format ?: "TXT")
                                    val target = if (format == BookFormat.PDF) {
                                        "pdf/$bookId"
                                    } else {
                                        "reader/$bookId"
                                    }
                                    navController.navigate(target) {
                                        popUpTo("open/$bookId") { inclusive = true }
                                    }
                                }
                            }
                            composable(
                                route = "reader/{bookId}",
                                arguments = listOf(
                                    navArgument("bookId") { type = NavType.LongType }
                                )
                            ) { entry ->
                                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                                val vm: ReaderViewModel = viewModel(
                                    factory = ReaderViewModel.factory(
                                        bookId,
                                        app.container.bookRepository,
                                        app.container.themePreferences
                                    )
                                )
                                ReaderScreen(
                                    viewModel = vm,
                                    musicController = app.container.musicController,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "pdf/{bookId}",
                                arguments = listOf(
                                    navArgument("bookId") { type = NavType.LongType }
                                )
                            ) { entry ->
                                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                                val vm: PdfReaderViewModel = viewModel(
                                    factory = PdfReaderViewModel.factory(
                                        bookId,
                                        app.container.bookRepository,
                                        app.container.themePreferences
                                    )
                                )
                            PdfReaderScreen(
                                viewModel = vm,
                                musicController = app.container.musicController,
                                onBack = { navController.popBackStack() }
                            )
                            }
                        }

                        MiniPlayerBar(
                            controller = app.container.musicController,
                            onOpenPlaylist = {
                                if (navController.currentDestination?.route != "music") {
                                    navController.navigate("music")
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}
