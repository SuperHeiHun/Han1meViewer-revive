package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.RelatedVideoGridSpec
import com.yenaly.han1meviewer.ui.view.video.relatedVideoGridSpec
import org.junit.Assert.*
import org.junit.Test

class RelatedVideoGridSpecTest {
    private fun normal(width: Int) = requireNotNull(relatedVideoGridSpec(width, 192, 108, 2, 8))
    private fun simplified(width: Int) = requireNotNull(relatedVideoGridSpec(width, 120, 176, 3, 8))

    @Test fun landscapeSwitchThenPortraitRecomputesColumnsAndCoverBounds() {
        assertEquals(RelatedVideoGridSpec(4, 192, 108), normal(900))
        assertEquals(RelatedVideoGridSpec(2, 172, 96), normal(360))
    }

    @Test fun returningToAWideViewportRestoresTheResourceDimensions() {
        assertEquals(RelatedVideoGridSpec(2, 152, 85), normal(320))
        assertEquals(RelatedVideoGridSpec(4, 192, 108), normal(900))
    }

    @Test fun normalCardsRetainTheMinimumTwoColumns() {
        assertEquals(RelatedVideoGridSpec(2, 92, 51), normal(200))
    }

    @Test fun simplifiedCardsRetainThreeColumnsAndTheirOwnResourceRatio() {
        assertEquals(RelatedVideoGridSpec(3, 112, 164), simplified(360))
        assertEquals(RelatedVideoGridSpec(7, 120, 176), simplified(900))
    }

    @Test fun aVideoTypeChangeAtTheSameWidthChangesTheGrid() {
        assertEquals(2, normal(360).columns)
        assertEquals(3, simplified(360).columns)
        assertTrue(normal(360).coverWidth > normal(360).coverHeight)
        assertTrue(simplified(360).coverWidth < simplified(360).coverHeight)
    }

    @Test fun cardInsetsFitWithinEveryIntegerColumn() {
        for (width in 200..1200) {
            for (spec in listOf(normal(width), simplified(width))) {
                assertTrue((spec.coverWidth + 8) * spec.columns <= width)
            }
        }
    }

    @Test fun theCallerCanExcludeRecyclerViewPadding() {
        assertEquals(RelatedVideoGridSpec(2, 164, 92), normal(360 - 16))
    }

    @Test fun hiddenOrUnmeasuredWidthsWaitForARealViewport() {
        assertNull(relatedVideoGridSpec(0, 192, 108, 2, 8))
        assertNull(relatedVideoGridSpec(-10, 192, 108, 2, 8))
    }

    @Test fun repeatedResizesDoNotAccumulateScaling() {
        repeat(5) {
            assertEquals(RelatedVideoGridSpec(2, 172, 96), normal(360))
            assertEquals(RelatedVideoGridSpec(4, 192, 108), normal(900))
        }
    }

    @Test fun tinyPositiveWidthsNeverProduceInvalidViewDimensions() {
        val spec = normal(1)
        assertEquals(2, spec.columns)
        assertTrue(spec.coverWidth > 0)
        assertTrue(spec.coverHeight > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidResourceDimensionsAreRejected() {
        relatedVideoGridSpec(360, 0, 108, 2, 8)
    }
}
