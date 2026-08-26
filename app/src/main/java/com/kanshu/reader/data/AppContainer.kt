package com.kanshu.reader.data

import android.content.Context
import com.kanshu.reader.data.ai.AiChatRepository
import com.kanshu.reader.data.db.AppDatabase
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.DefaultBooksSync
import com.kanshu.reader.data.remote.GithubBooksUploader
import com.kanshu.reader.data.remote.GithubMusicUploader
import com.kanshu.reader.data.remote.MusicSync
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.data.repo.MusicRepository
import com.kanshu.reader.music.MusicController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)

    val bookRepository = BookRepository(
        bookDao = database.bookDao(),
        folderDao = database.folderDao(),
        filesDir = appContext.filesDir
    )
    val musicRepository = MusicRepository(
        musicDao = database.musicDao(),
        filesDir = appContext.filesDir
    )
    val themePreferences = ThemePreferences(appContext)
    val defaultBooksSync = DefaultBooksSync(appContext, bookRepository)
    val githubBooksUploader = GithubBooksUploader(bookRepository, themePreferences)
    val githubMusicUploader = GithubMusicUploader(musicRepository, themePreferences)
    val musicSync = MusicSync(musicRepository)
    val musicController = MusicController(appContext, musicRepository)
    val aiChatRepository = AiChatRepository(
        context = appContext,
        aiChatDao = database.aiChatDao(),
        themePreferences = themePreferences
    )
}
