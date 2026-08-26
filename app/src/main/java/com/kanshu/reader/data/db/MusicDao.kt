package com.kanshu.reader.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "music_tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "未知",
    val fileName: String,
    val durationMs: Long = 0L,
    val sortOrder: Int = 0,
    val remoteId: String? = null,
    val source: String = SOURCE_LOCAL,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"
    }

    val isRemote: Boolean get() = source == SOURCE_REMOTE
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks ORDER BY sortOrder ASC, id ASC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<TrackEntity>

    @Query("SELECT * FROM music_tracks WHERE id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM music_tracks WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: TrackEntity): Long

    @Query("UPDATE music_tracks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query(
        """
        UPDATE music_tracks
        SET remoteId = :remoteId, source = :source
        WHERE id = :id
        """
    )
    suspend fun markRemote(id: Long, remoteId: String, source: String = TrackEntity.SOURCE_REMOTE)

    @Query("DELETE FROM music_tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM music_tracks")
    suspend fun maxSortOrder(): Int
}
