package com.yenaly.han1meviewer

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.adapter.HanimeVideoRvAdapter
import com.yenaly.han1meviewer.ui.view.video.RelatedVideoGridController
import com.yenaly.han1meviewer.ui.view.video.relatedVideoGridSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Detached/local cards and empty image URLs only; no player, video requests or preference changes. */
@RunWith(AndroidJUnit4::class)
class RelatedVideoGridLayoutTest {
    private class Fixture(
        val list: RecyclerView,
        val manager: GridLayoutManager,
        val adapter: HanimeVideoRvAdapter,
        val controller: RelatedVideoGridController,
    ) {
        val normalWidth get() = list.resources.getDimensionPixelSize(R.dimen.video_cover_width)
        val wide get() = normalWidth * 5
        val narrow get() = normalWidth * 2 - 16

        fun layout(width: Int) {
            // The first pass dispatches the viewport change; the next consumes its layout request.
            repeat(2) {
                val exact = View.MeasureSpec.EXACTLY
                list.measure(View.MeasureSpec.makeMeasureSpec(width, exact), View.MeasureSpec.makeMeasureSpec(800, exact))
                list.layout(0, 0, width, 800)
            }
        }

        fun assertGrid(type: Int) {
            val resources = list.resources
            val normal = type == HanimeInfo.NORMAL
            val spec = requireNotNull(relatedVideoGridSpec(
                list.width - list.paddingLeft - list.paddingRight,
                resources.getDimensionPixelSize(if (normal) R.dimen.video_cover_width else R.dimen.video_cover_simplified_width),
                resources.getDimensionPixelSize(if (normal) R.dimen.video_cover_height else R.dimen.video_cover_simplified_height),
                if (normal) 2 else 3,
                2 * (4 * resources.displayMetrics.density).roundToInt(),
            ))
            assertEquals(spec.columns, manager.spanCount)
            val holder = adapter.createViewHolder(list, type)
            adapter.bindViewHolder(holder, 0)
            val cover = holder.itemView.findViewById<View>(R.id.cover_wrapper)
            assertEquals(spec.coverWidth, cover.layoutParams.width)
            assertEquals(spec.coverHeight, cover.layoutParams.height)
        }
    }

    private fun withGrid(type: Int = HanimeInfo.NORMAL, test: (Fixture) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                val list = RecyclerView(activity)
                val manager = GridLayoutManager(activity, 1)
                val adapter = HanimeVideoRvAdapter(VIDEO_LAYOUT_MATCH_PARENT)
                list.layoutManager = manager
                list.adapter = adapter
                list.itemAnimator = null
                root.addView(list)
                val controller = RelatedVideoGridController(list, manager, adapter)
                try {
                    adapter.submitList(listOf(HanimeInfo("Local card", "", "local", itemType = type)))
                    controller.setVideoType(type)
                    test(Fixture(list, manager, adapter, controller))
                } finally {
                    controller.dispose()
                }
            }
        }
    }

    @Test fun landscapeLoadedCardsReturnToPortraitColumnsWithoutAnotherVideoLoad() = withGrid { fixture ->
        fixture.layout(fixture.wide)
        assertEquals(5, fixture.manager.spanCount)
        fixture.assertGrid(HanimeInfo.NORMAL)
        fixture.layout(fixture.narrow)
        assertEquals(2, fixture.manager.spanCount)
        fixture.assertGrid(HanimeInfo.NORMAL)
    }

    @Test fun recycledHoldersAndLaterBindsUseTheNewCoverDimensions() = withGrid { fixture ->
        fixture.layout(fixture.wide)
        val holder = fixture.adapter.createViewHolder(fixture.list, HanimeInfo.NORMAL)
        fixture.adapter.bindViewHolder(holder, 0)
        val cover = holder.itemView.findViewById<View>(R.id.cover_wrapper)
        val wideWidth = cover.layoutParams.width
        fixture.layout(fixture.narrow)
        fixture.adapter.bindViewHolder(holder, 0)
        assertTrue(cover.layoutParams.width < wideWidth)
        fixture.assertGrid(HanimeInfo.NORMAL)
        fixture.layout(fixture.wide)
        fixture.adapter.bindViewHolder(holder, 0)
        assertEquals(wideWidth, cover.layoutParams.width)
    }

    @Test fun sizeOnlyPayloadDoesNotRebindTheTitleOrImageContent() = withGrid { fixture ->
        fixture.layout(fixture.wide)
        val holder = fixture.adapter.createViewHolder(fixture.list, HanimeInfo.NORMAL)
        fixture.adapter.bindViewHolder(holder, 0)
        val title = holder.itemView.findViewById<TextView>(R.id.title)
        title.text = "Do not rebind metadata"
        val payloads = mutableListOf<Any>()
        fixture.adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                if (payload != null) payloads += payload
            }
        })
        fixture.layout(fixture.narrow)
        assertEquals(1, payloads.size)
        fixture.adapter.onBindViewHolder(holder, 0, payloads)
        assertEquals("Do not rebind metadata", title.text.toString())
        assertTrue(holder.itemView.findViewById<View>(R.id.cover_wrapper).layoutParams.width < fixture.normalWidth)
    }

    @Test fun aHiddenFullscreenLoadUsesTheViewportMeasuredAfterReturning() = withGrid { fixture ->
        fixture.layout(fixture.wide)
        fixture.list.visibility = View.GONE
        fixture.controller.setVideoType(HanimeInfo.NORMAL)
        fixture.list.visibility = View.VISIBLE
        fixture.layout(fixture.narrow)
        fixture.assertGrid(HanimeInfo.NORMAL)
    }

    @Test fun simplifiedCardsAlsoReflowAndExcludeRecyclerViewPadding() = withGrid(HanimeInfo.SIMPLIFIED) { fixture ->
        fixture.layout(fixture.wide)
        fixture.list.setPadding(12, 0, 12, 0)
        fixture.layout(fixture.narrow)
        fixture.assertGrid(HanimeInfo.SIMPLIFIED)
    }

    @Test fun disposedViewDoesNotKeepUpdatingItsGrid() = withGrid { fixture ->
        fixture.layout(fixture.wide)
        val columns = fixture.manager.spanCount
        fixture.controller.dispose()
        fixture.layout(fixture.narrow)
        assertEquals(columns, fixture.manager.spanCount)
    }
}
