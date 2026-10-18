package dev.elyssia.iris.storage

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalStoreTest {
    private lateinit var store: LocalStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.deleteDatabase("iris.db")
        store = LocalStore(context)
    }

    @Test
    fun historyRoundTrip() {
        store.addHistory("https://a", "A", at = 1000)
        store.addHistory("https://b", "B", at = 2000)
        val list = store.listHistory()
        assertEquals(listOf(2000L, 1000L), list.map { it.at })
    }

    @Test
    fun dedupesByAddress() {
        store.addHistory("https://a", "A", at = 1000)
        store.addHistory("https://a", "A2", at = 2000)
        assertEquals(1, store.listHistory().size)
        assertEquals("A2", store.listHistory().first().title)
    }

    @Test
    fun bookmarksUpsertAndDelete() {
        store.upsertBookmark("https://b", "B")
        store.upsertBookmark("https://b", "B2")
        assertEquals(listOf("B2"), store.listBookmarks().map { it.title })
        store.deleteBookmark("https://b")
        assertTrue(store.listBookmarks().isEmpty())
    }

    @Test
    fun clearHistoryOnlyAffectsHistory() {
        store.addHistory("https://a", "A")
        store.upsertBookmark("https://b", "B")
        store.clearHistory()
        assertTrue(store.listHistory().isEmpty())
        assertEquals(1, store.listBookmarks().size)
    }

    @Test
    fun wipeAllClearsEverything() {
        store.addHistory("https://a", "A")
        store.upsertBookmark("https://b", "B")
        store.wipeAll()
        assertTrue(store.listHistory().isEmpty() && store.listBookmarks().isEmpty())
    }

    @Test
    fun settingsRoundTrip() {
        store.setSetting("k", "v1")
        assertEquals("v1", store.getSetting("k"))
        store.setSetting("k", "v2")
        assertEquals("v2", store.getSetting("k"))
    }
}
