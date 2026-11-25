package com.yenaly.han1meviewer

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.adapter.RvWrapper
import com.yenaly.han1meviewer.ui.adapter.RvWrapper.Companion.wrappedWith
import com.yenaly.han1meviewer.ui.view.video.PlaybackSeriesPositioner
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

    private class PortraitSeries(
        val outer: Fixture,
        val rows: Rows,
        val wrapper: RvWrapper<Rows.Holder>,
        val positioner: PlaybackSeriesPositioner,
    ) {
        val list get() = requireNotNull(wrapper.wrapper)
        val manager get() = list.layoutManager as RecordingManager

        fun rebind() {
            wrapper.item = Unit
            outer.layoutNow()
        }

        fun assertPosition(position: Int) {
            Fixture(outer.root, list, manager).assertPosition(position)
        }
    }

    /** A real nested series row and ConcatAdapter, not just a standalone horizontal list. */
    private fun withPortraitSeries(test: (ActivityScenario<SettingsActivity>, PortraitSeries) -> Unit) =
        withList { scenario, outer ->
            lateinit var series: PortraitSeries
            scenario.onActivity { activity ->
                val rows = Rows(videos.size)
                val positioner = PlaybackSeriesPositioner(rows)
                val wrapper = rows.wrappedWith { RecordingManager(activity, RecyclerView.HORIZONTAL) }
                wrapper.doOnBind { positioner.position(this) }
                outer.list.adapter = ConcatAdapter(wrapper)
                outer.layoutNow()
                series = PortraitSeries(outer, rows, wrapper, positioner)
            }
            test(scenario, series)
        }

    @Test fun portraitRebindPositionsAnAlreadyCreatedNestedRow() = withPortraitSeries { scenario, series ->
        lateinit var originalList: RecyclerView
        scenario.onActivity {
            originalList = series.list
            series.positioner.submit(videos, "30") { true }
            series.rebind()
        }
        assertAfterPosition(scenario, series.outer) {
            assertSame(originalList, series.list)
            series.assertPosition(30)
        }
    }

    @Test fun portraitRebindUsesTheVisibleHolderNotTheLastCreatedPrefetchHolder() =
        withPortraitSeries { scenario, series ->
            lateinit var visibleList: RecyclerView
            lateinit var unusedList: RecyclerView
            scenario.onActivity {
                visibleList = series.list
                val unusedHolder = series.wrapper.createViewHolder(series.outer.list, 0)
                unusedList = unusedHolder.itemView.findViewById(R.id.rv)
                assertSame(unusedList, series.wrapper.wrapper)
                series.positioner.submit(videos, "40") { true }
                series.rebind()
            }
            assertAfterPosition(scenario, series.outer) {
                assertSame(visibleList, series.list)
                series.assertPosition(40)
                assertTrue((unusedList.layoutManager as RecordingManager).requests.isEmpty())
            }
        }

    @Test fun portraitDetachedBindingQueuesThePositionWithoutWaitingForAttachment() =
        withPortraitSeries { scenario, series ->
            scenario.onActivity {
                val holder = series.wrapper.createViewHolder(series.outer.list, 0)
                val detachedList = holder.itemView.findViewById<RecyclerView>(R.id.rv)
                series.positioner.submit(videos, "30") { true }
                series.wrapper.bindViewHolder(holder, 0)
                assertFalse(detachedList.isAttachedToWindow)
                val manager = detachedList.layoutManager as RecordingManager
                assertEquals(listOf(30 to 0), manager.requests)
                series.outer.root.addView(holder.itemView, FrameLayout.LayoutParams(480, 80))
                val fixture = Fixture(series.outer.root, detachedList, manager)
                fixture.layoutNow()
                fixture.assertPosition(30)
            }
        }

    @Test fun portraitSameSizeOrderChangeRepositionsWithoutAnInnerAdapterNotification() =
        withPortraitSeries { scenario, series ->
            scenario.onActivity {
                series.positioner.submit(videos, "5") { true }
                series.rebind()
                series.assertPosition(5)
                series.manager.requests.clear()
                series.positioner.submit(videos.asReversed(), "5") { true }
                series.rebind()
            }
            assertAfterPosition(scenario, series.outer) { series.assertPosition(54) }
        }

    @Test fun portraitRepeatedBindingDoesNotUndoTheUsersManualScroll() =
        withPortraitSeries { scenario, series ->
            scenario.onActivity {
                series.positioner.submit(videos, "30") { true }
                series.rebind()
                series.assertPosition(30)
                series.manager.scrollToPositionWithOffset(2, 0)
                series.outer.layoutNow()
                series.manager.requests.clear()
                series.rebind()
            }
            assertAfterPosition(scenario, series.outer) {
                assertTrue(series.manager.requests.isEmpty())
                assertEquals(2, series.manager.findFirstVisibleItemPosition())
            }
        }

    @Test fun portraitEveryNewHolderReceivesTheCurrentRequestOnce() =
        withPortraitSeries { scenario, series ->
            scenario.onActivity {
                series.positioner.submit(videos, "30") { true }
                series.rebind()
                series.assertPosition(30)
                val holder = series.wrapper.createViewHolder(series.outer.list, 0)
                val otherList = holder.itemView.findViewById<RecyclerView>(R.id.rv)
                series.wrapper.bindViewHolder(holder, 0)
                series.wrapper.bindViewHolder(holder, 0)
                assertEquals(listOf(30 to 0), (otherList.layoutManager as RecordingManager).requests)
            }
        }

    @Test fun portraitClearedOrStaleRequestsCannotRepositionAReboundRow() =
        withPortraitSeries { scenario, series ->
            scenario.onActivity {
                var current = true
                series.positioner.submit(videos, "30") { current }
                current = false
                series.rebind()
                assertTrue(series.manager.requests.isEmpty())
                series.positioner.submit(videos, "40") { true }
                series.positioner.clear()
                series.rebind()
            }
            assertAfterPosition(scenario, series.outer) { assertTrue(series.manager.requests.isEmpty()) }
        }

    @Test fun portraitRejectsAnUncommittedCountOrMissingCurrentVideo() =
        withPortraitSeries { scenario, series ->
            scenario.onActivity {
                series.positioner.submit(videos.dropLast(1), "30") { true }
                series.rebind()
                series.positioner.submit(videos, "missing") { true }
                series.rebind()
                series.positioner.submit(videos, "") { true }
                series.rebind()
                val unrelated = PlaybackSeriesPositioner(Rows(videos.size))
                unrelated.submit(videos, "30") { true }
                unrelated.position(series.list)
            }
            assertAfterPosition(scenario, series.outer) { assertTrue(series.manager.requests.isEmpty()) }
        }

    @Test fun portraitPendingPositionWinsOverLaterRestoredScrollState() = withList(RecyclerView.HORIZONTAL) { scenario, fixture ->
        scenario.onActivity {
            val adapter = requireNotNull(fixture.list.adapter)
            val positioner = PlaybackSeriesPositioner(adapter)
            fixture.manager.scrollToPositionWithOffset(2, 0)
            fixture.layoutNow()
            val saved = fixture.manager.onSaveInstanceState()
            fixture.manager.requests.clear()
            positioner.submit(videos, "30") { true }
            positioner.position(fixture.list)
            fixture.manager.onRestoreInstanceState(saved)
            fixture.layoutNow()
        }
        assertAfterPosition(scenario, fixture) { fixture.assertPosition(30) }
    }
}
