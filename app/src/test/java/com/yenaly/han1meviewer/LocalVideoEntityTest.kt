package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalVideoEntityTest {
    @Test
    fun convertsSavedMetadataToPlayableListItem() {
        val entity = LocalVideoEntity(
            videoCode = "123",
            listType = LocalVideoEntity.FAVORITES,
            title = "Saved video",
            coverUrl = "https://example.com/cover.jpg",
        )
        val item = entity.toHanimeInfo()
        assertEquals(entity.videoCode, item.videoCode)
        assertEquals(entity.title, item.title)
        assertEquals(entity.coverUrl, item.coverUrl)
        assertEquals(HanimeInfo.SIMPLIFIED, item.itemType)
    }
}
