package com.kanshu.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        FolderEntity::class,
        AiSessionEntity::class,
        AiMessageEntity::class,
        TrackEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun musicDao(): MusicDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE books ADD COLUMN folderId INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'"
                )
                db.execSQL("ALTER TABLE books ADD COLUMN remoteId TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        openingHint TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES ai_sessions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_messages_sessionId ON ai_messages(sessionId)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ai_sessions ADD COLUMN visualDnaJson TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE ai_sessions ADD COLUMN imageSeed INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE ai_messages ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE ai_messages ADD COLUMN imagePrompt TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS music_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        remoteId TEXT,
                        source TEXT NOT NULL,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kanshu.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            db.execSQL("PRAGMA foreign_keys=ON")
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}
