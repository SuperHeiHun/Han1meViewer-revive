package com.yenaly.han1meviewer.logic.dao

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.yenaly_libs.utils.applicationContext

// Separate storage leaves existing history and download databases untouched.
@Database(entities = [LocalVideoEntity::class], version = 1, exportSchema = false)
abstract class LocalVideoDatabase : RoomDatabase() {
    abstract val localVideoDao: LocalVideoDao

    companion object {
        val instance by lazy {
            Room.databaseBuilder(
                applicationContext,
                LocalVideoDatabase::class.java,
                "local_video.db",
            ).build()
        }
    }
}
