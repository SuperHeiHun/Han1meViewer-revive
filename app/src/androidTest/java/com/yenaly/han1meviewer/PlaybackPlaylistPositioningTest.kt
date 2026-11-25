package com.yenaly.han1meviewer

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.view.video.positionPlaybackVideo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local rows only: no player, media, images, network requests or preference changes. */
@RunWith(AndroidJUnit4::class)
class PlaybackPlaylistPositioningTest {
    private val videos = (0 until 60).map { HanimeInfo("Video $it", "", "$it", itemType = HanimeInfo.NORMAL) }

    private class RecordingManager(context: android.content.Context, orientation: Int) :
        LinearLayoutManager(context, orientation, false) {
        val requests = mutableListOf<Pair<Int, Int>>()

        override fun scrollToPositionWithOffset(position: Int, offset: Int) {
            requests += position to offset
            super.scrollToPositionWithOffset(position, offset)
        }
    }

    private class Rows(private val count: Int) : RecyclerView.Adapter<Rows.Holder>() {
        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
        override fun getItemCount() = count
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(120, 80)
        })
        override fun onBindViewHolder(holder: Holder, position: Int) { holder.text.text = position.toString() }
    }

    private class Fixture(val root: FrameLayout, val list: RecyclerView, val manager: RecordingManager) {
        fun layoutNow() {
            val exact = android.view.View.MeasureSpec.EXACTLY
            list.measure(android.view.View.MeasureSpec.makeMeasureSpec(480, exact),
                android.view.View.MeasureSpec.makeMeasureSpec(320, exact))
            list.layout(0, 0, 480, 320)
        }

        fun assertPosition(position: Int) {
            assertEquals(listOf(position to 0), manager.requests)
            val row = manager.findViewByPosition(position)
            assertNotNull("Current video must have a visible row", row)
            row ?: return
            if (manager.orientation == RecyclerView.HORIZONTAL) {
                assertTrue(manager.getDecoratedLeft(row) >= list.paddingLeft)
                assertTrue(manager.getDecoratedRight(row) <= list.width - list.paddingRight)
            } else {
                assertTrue(manager.getDecoratedTop(row) >= list.paddingTop)
                assertTrue(manager.getDecoratedBottom(row) <= list.height - list.paddingBottom)
            }
        }
    }

    private fun withList(
        orientation: Int = RecyclerView.VERTICAL,
        test: (ActivityScenario<SettingsActivity>, Fixture) -> Unit,
    ) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                val manager = RecordingManager(activity, orientation)
                val list = RecyclerView(activity).apply {
                    layoutManager = manager
                    adapter = Rows(videos.size)
                    itemAnimator = null
                }
                root.addView(list, FrameLayout.LayoutParams(480, 320))
                fixture = Fixture(root, list, manager)
                fixture.layoutNow()
            }
            test(scenario, fixture)
        }
    }

    private fun assertAfterPosition(
        scenario: ActivityScenario<SettingsActivity>, fixture: Fixture, assertion: () -> Unit,
    ) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity {
            fixture.layoutNow()
            assertion()
        }
    }

    @Test fun verticalPanelPositionsTheCurrentVideoAfterLayout() = withList { scenario, fixture ->
        scenario.onActivity { fixture.list.positionPlaybackVideo(videos, "30") { true } }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(30) }
    }

    @Test fun horizontalSeriesPositionsTheCurrentVideoAfterLayout() = withList(RecyclerView.HORIZONTAL) { scenario, fixture ->
        scenario.onActivity { fixture.list.positionPlaybackVideo(videos, "30") { true } }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(30) }
    }

    @Test fun reversedOrderLocatesByCodeInsteadOfTheOriginalIndex() = withList { scenario, fixture ->
        scenario.onActivity { fixture.list.positionPlaybackVideo(videos.asReversed(), "5") { true } }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(54) }
    }

    @Test fun lastItemRemainsFullyVisibleWhenTheOffsetIsClamped() = withList { scenario, fixture ->
        scenario.onActivity { fixture.list.positionPlaybackVideo(videos, "59") { true } }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(59) }
    }

    @Test fun reopeningPositionsTheCurrentVideoInsteadOfKeepingAnOldScroll() = withList { scenario, fixture ->
        scenario.onActivity {
            fixture.manager.scrollToPositionWithOffset(2, 0)
            fixture.layoutNow()
            fixture.manager.requests.clear()
            fixture.list.positionPlaybackVideo(videos, "30") { true }
        }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(30) }
    }

    @Test fun missingOrBlankCurrentCodeDoesNotGuessTheFirstItem() = withList { scenario, fixture ->
        scenario.onActivity {
            fixture.list.positionPlaybackVideo(videos, "missing") { true }
            fixture.list.positionPlaybackVideo(videos, "") { true }
            fixture.list.positionPlaybackVideo(videos, null) { true }
        }
        assertAfterPosition(scenario, fixture) { assertTrue(fixture.manager.requests.isEmpty()) }
    }

    @Test fun lazySeriesWaitsUntilItsNestedListIsAttached() = withList(RecyclerView.HORIZONTAL) { scenario, fixture ->
        scenario.onActivity {
            fixture.root.removeView(fixture.list)
            fixture.list.positionPlaybackVideo(videos, "30") { true }
            fixture.layoutNow()
            assertTrue(fixture.manager.requests.isEmpty())
            fixture.root.addView(fixture.list, FrameLayout.LayoutParams(480, 320))
            fixture.layoutNow()
        }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(30) }
    }

    @Test fun aReplacedAdapterInvalidatesTheOldPositionRequest() = withList { scenario, fixture ->
        scenario.onActivity {
            fixture.list.positionPlaybackVideo(videos, "30") { true }
            fixture.list.adapter = Rows(videos.size)
            fixture.layoutNow()
        }
        assertAfterPosition(scenario, fixture) { assertTrue(fixture.manager.requests.isEmpty()) }
    }

    @Test fun rapidOrderChangesApplyOnlyTheLatestAdapter() = withList { scenario, fixture ->
        scenario.onActivity {
            fixture.list.positionPlaybackVideo(videos, "5") { true }
            fixture.list.adapter = Rows(videos.size)
            fixture.list.positionPlaybackVideo(videos.asReversed(), "5") { true }
            fixture.layoutNow()
        }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(54) }
    }

    @Test fun anOldVideoRequestCannotMoveTheNewVideoInTheSameAdapter() = withList { scenario, fixture ->
        scenario.onActivity {
            var generation = 0
            fixture.list.positionPlaybackVideo(videos, "5") { generation == 0 }
            generation++
            fixture.list.positionPlaybackVideo(videos, "40") { generation == 1 }
        }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(40) }
    }

    @Test fun dismissingBeforeThePostedScrollCancelsPositioning() = withList { scenario, fixture ->
        scenario.onActivity {
            var showing = true
            fixture.list.positionPlaybackVideo(videos, "30") { showing }
            showing = false
        }
        assertAfterPosition(scenario, fixture) { assertTrue(fixture.manager.requests.isEmpty()) }
    }

    @Test fun restoredScrollStateDoesNotOverrideTheCurrentVideo() = withList { scenario, fixture ->
        scenario.onActivity {
            fixture.manager.scrollToPositionWithOffset(2, 0)
            fixture.layoutNow()
            val saved = fixture.manager.onSaveInstanceState()
            fixture.manager.requests.clear()
            fixture.list.positionPlaybackVideo(videos, "30") { true }
            fixture.manager.onRestoreInstanceState(saved)
            fixture.layoutNow()
        }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(30) }
    }
}
