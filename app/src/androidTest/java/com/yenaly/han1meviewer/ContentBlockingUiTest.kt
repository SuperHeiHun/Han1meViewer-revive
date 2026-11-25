package com.yenaly.han1meviewer

import android.content.Context
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.blocking.BlockKind
import com.yenaly.han1meviewer.logic.blocking.BlocklistStore
import com.yenaly.han1meviewer.logic.blocking.ContentBlocking
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.util.showVideoBlockMenu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentBlockingUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val author = "BlockAuthorUiTest_4e8f"
    private val tag = "BlockTagUiTest_4e8f"

    @Before fun setUp() = cleanUp()
    @After fun cleanUp() = runBlocking {
        ContentBlocking.store.setBlocked(BlockKind.AUTHOR, author, false)
        ContentBlocking.store.setBlocked(BlockKind.TAG, tag, false)
    }

    private fun waitFor(kind: BlockKind, name: String, blocked: Boolean) = runBlocking {
        withTimeout(5_000) { ContentBlocking.rules.first { it.contains(kind, name) == blocked } }
    }

    private fun openSettings() {
        onView(withText(R.string.content_blocking)).perform(click())
        onView(withText(R.string.add_blocked_author)).check(matches(isDisplayed()))
    }

    @Test fun authorCanBeAddedSurvivesRecreationAndCanBeUnblocked() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            openSettings()
            onView(withText(R.string.add_blocked_author)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText(author), closeSoftKeyboard())
            onView(withText(R.string.confirm)).perform(click())
            waitFor(BlockKind.AUTHOR, author, true)
            scenario.recreate()
            onView(withText(author)).perform(click())
            onView(withText(R.string.confirm)).perform(click())
            waitFor(BlockKind.AUTHOR, author, false)
            val prefs = context.getSharedPreferences("content_blocklist", Context.MODE_PRIVATE)
            assertFalse(BlocklistStore(prefs).rules.value.contains(BlockKind.AUTHOR, author))
        }
    }

    @Test fun tagEntryRejectsBlankAndDuplicateNames() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openSettings()
            onView(withText(R.string.add_blocked_tag)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText(" # "))
            onView(withText(R.string.confirm)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).check(matches(hasErrorText(
                context.getString(R.string.block_name_required)
            )))
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText(" #$tag "), closeSoftKeyboard())
            onView(withText(R.string.confirm)).perform(click())
            waitFor(BlockKind.TAG, tag, true)
            onView(withText(R.string.add_blocked_tag)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText(tag.uppercase()), closeSoftKeyboard())
            onView(withText(R.string.confirm)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).check(matches(hasErrorText(
                context.getString(R.string.block_rule_exists)
            )))
            onView(withText(R.string.cancel)).perform(click())
        }
    }

    @Test fun videoMenuSupportsTagsAndAuthorsWithoutLogin() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.showVideoBlockMenu(HanimeVideo(
                    title = "Test video", coverUrl = "", chineseTitle = null,
                    introduction = null, uploadTime = null, videoUrls = linkedMapOf(),
                    tags = listOf(tag),
                    artist = HanimeVideo.Artist(author, "", "3D"),
                ), activity.lifecycleScope)
            }
            onView(withText(context.getString(R.string.block_author_named, author)))
                .check(matches(isDisplayed()))
            onView(withText(context.getString(R.string.block_tag_named, tag))).perform(click())
            onView(withText(R.string.confirm)).perform(click())
            waitFor(BlockKind.TAG, tag, true)
            assertTrue(ContentBlocking.rules.value.contains(BlockKind.TAG, tag))
        }
    }
}
