package com.yenaly.han1meviewer

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.video.VideoIntroductionFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the view detached, as it is during ViewPager2's initial fragment creation. */
@RunWith(AndroidJUnit4::class)
class VideoIntroductionInitializationTest {
    @Test fun initialLoadingDoesNotRequireAnAttachedParentPager() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = VideoIntroductionFragment()
                val manager = activity.supportFragmentManager
                manager.beginTransaction().add(fragment, "detached-video-introduction").commitNow()
                try {
                    assertNull(fragment.requireView().parent)
                    assertEquals(VideoLoadingState.Loading, fragment.viewModel.hanimeVideoStateFlow.value)
                    assertEquals(
                        View.GONE,
                        fragment.requireView().findViewById<View>(R.id.rv_video_intro).visibility,
                    )
                } finally {
                    manager.beginTransaction().remove(fragment).commitNow()
                }
            }
        }
    }
}
