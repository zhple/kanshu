package com.kanshu.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kanshu.reader.reader.BookFormat
import com.kanshu.reader.ui.library.LibraryScreen
import com.kanshu.reader.ui.library.LibraryViewModel
import com.kanshu.reader.ui.reader.PdfReaderScreen
import com.kanshu.reader.ui.reader.PdfReaderViewModel
import com.kanshu.reader.ui.reader.ReaderScreen
import com.kanshu.reader.ui.reader.ReaderViewModel
import com.kanshu.reader.ui.theme.KanshuTheme

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
                    NavHost(
                        navController = navController,
                        startDestination = "library"
                    ) {
                        composable("library") {
                            val vm: LibraryViewModel = viewModel(
                                factory = LibraryViewModel.factory(
                                    app.container.bookRepository,
                                    app.container.themePreferences,
                                    app.container.defaultBooksSync
                                )
                            )
                            LibraryScreen(
                                viewModel = vm,
                                onOpenBook = { id ->
                                    navController.navigate("open/$id")
                                }
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
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
