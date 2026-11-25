package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import com.yenaly.han1meviewer.logic.model.HanimeInfo

/** A video may independently belong to both local lists. */
@Entity(primaryKeys = ["videoCode", "listType"])
data class LocalVideoEntity(
    val videoCode: String,
    val listType: String,
    val title: String,
    val coverUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toHanimeInfo() = HanimeInfo(
        title = title,
        coverUrl = coverUrl,
        videoCode = videoCode,
        itemType = HanimeInfo.SIMPLIFIED,
    )

    companion object {
        const val FAVORITES = "favorites"
        const val WATCH_LATER = "watch_later"
    }
}
