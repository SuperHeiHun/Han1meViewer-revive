package com.yenaly.han1meviewer.logic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalVideoDao {
    // Favorites show newest first; pending videos retain their queue order.
    @Query("""
        SELECT * FROM LocalVideoEntity WHERE listType = :listType
        ORDER BY CASE WHEN listType = 'watch_later' THEN addedAt END ASC,
                 addedAt DESC, rowid ASC
    """)
    fun observeList(listType: String): Flow<List<LocalVideoEntity>>

    @Query("SELECT listType FROM LocalVideoEntity WHERE videoCode = :videoCode")
    fun observeMembership(videoCode: String): Flow<List<String>>

    // Repeated taps must not create duplicates or change the queue position.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: LocalVideoEntity)

    @Query("DELETE FROM LocalVideoEntity WHERE listType = :listType AND videoCode = :videoCode")
    suspend fun delete(listType: String, videoCode: String)

    @Query("DELETE FROM LocalVideoEntity WHERE listType = :listType")
    suspend fun clear(listType: String)
}
