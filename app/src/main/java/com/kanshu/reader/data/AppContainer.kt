package com.kanshu.reader.data

import android.content.Context
import com.kanshu.reader.data.db.AppDatabase
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.DefaultBooksSync
import com.kanshu.reader.data.repo.BookRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)

    val bookRepository = BookRepository(
        bookDao = database.bookDao(),
        folderDao = database.folderDao(),
        filesDir = appContext.filesDir
    )
    val themePreferences = ThemePreferences(appContext)
    val defaultBooksSync = DefaultBooksSync(appContext, bookRepository)
}
