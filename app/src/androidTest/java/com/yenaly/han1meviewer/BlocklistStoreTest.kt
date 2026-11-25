package com.yenaly.han1meviewer

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.blocking.BlockKind
import com.yenaly.han1meviewer.logic.blocking.BlocklistStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlocklistStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("blocklist_isolated_test", Context.MODE_PRIVATE)

    @Before fun setUp() { preferences.edit().clear().commit() }
    @After fun tearDown() { preferences.edit().clear().commit() }

    @Test fun newStoreStartsEmpty() {
        assertTrue(BlocklistStore(preferences).rules.value.isEmpty)
    }

    @Test fun rulesSurviveStoreRecreation() = runBlocking {
        val store = BlocklistStore(preferences)
        store.setBlocked(BlockKind.TAG, "tag", true)
        store.setBlocked(BlockKind.AUTHOR, "author", true)
        assertEquals(store.rules.value, BlocklistStore(preferences).rules.value)
    }

    @Test fun removalIsPersisted() = runBlocking {
        val store = BlocklistStore(preferences)
        store.setBlocked(BlockKind.TAG, "tag", true)
        store.setBlocked(BlockKind.TAG, "TAG", false)
        assertTrue(BlocklistStore(preferences).rules.value.isEmpty)
    }

    @Test fun equivalentNamesDoNotDuplicate() = runBlocking {
        val store = BlocklistStore(preferences)
        store.setBlocked(BlockKind.TAG, " #Ｔａｇ ", true)
        store.setBlocked(BlockKind.TAG, "tag", true)
        assertEquals(setOf("tag"), store.rules.value.tags)
    }

    @Test fun tagAndAuthorWithSameNameAreIndependent() = runBlocking {
        val store = BlocklistStore(preferences)
        store.setBlocked(BlockKind.TAG, "same", true)
        store.setBlocked(BlockKind.AUTHOR, "same", true)
        store.setBlocked(BlockKind.TAG, "same", false)
        assertEquals(setOf("same"), store.rules.value.authors)
        assertTrue(store.rules.value.tags.isEmpty())
    }

    @Test fun concurrentChangesDoNotLoseRules() = runBlocking {
        val store = BlocklistStore(preferences)
        coroutineScope {
            repeat(20) { index -> launch { store.setBlocked(BlockKind.TAG, "tag$index", true) } }
        }
        assertEquals(20, store.rules.value.tags.size)
        assertEquals(store.rules.value, BlocklistStore(preferences).rules.value)
    }

    @Test fun snapshotsAreNotMutatedByLaterEdits() = runBlocking {
        val store = BlocklistStore(preferences)
        store.setBlocked(BlockKind.TAG, "first", true)
        val snapshot = store.rules.value
        store.setBlocked(BlockKind.TAG, "second", true)
        assertEquals(setOf("first"), snapshot.tags)
    }

    @Test fun emptyNamesCannotBePersisted() = runBlocking {
        val store = BlocklistStore(preferences)
        try {
            store.setBlocked(BlockKind.AUTHOR, "  ", true)
            fail("Empty author must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertTrue(BlocklistStore(preferences).rules.value.isEmpty)
    }

    @Test fun failedWriteDoesNotPublishASuccessfulRule() = runBlocking {
        val failing = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                        editor.putStringSet(key, values)
                        return this
                    }
                    override fun commit() = false
                }
            }
        }
        val store = BlocklistStore(failing)
        try {
            store.setBlocked(BlockKind.TAG, "tag", true)
            fail("A failed write must not be reported as success")
        } catch (_: IllegalStateException) { }
        assertTrue(store.rules.value.isEmpty)
    }
}
