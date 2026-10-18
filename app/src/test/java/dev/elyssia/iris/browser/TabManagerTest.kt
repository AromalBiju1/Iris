package dev.elyssia.iris.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerTest {
    @Test
    fun newTabStartsWithNullUrlWhenUnspecified() {
        val tm = TabManager()
        assertEquals(0, tm.tabs.size)
        val t = tm.newTab()
        assertEquals(null, t.url)
        assertEquals(1, tm.tabs.size)
    }

    @Test
    fun idsAreUnique() {
        val tm = TabManager()
        val a = tm.newTab(); val b = tm.newTab()
        assertTrue(a.id != b.id)
    }

    @Test
    fun closeRemovesTab() {
        val tm = TabManager()
        val t = tm.newTab("https://a")
        tm.close(t)
        assertEquals(0, tm.tabs.size)
    }

    @Test
    fun retitlePropagates() {
        val tm = TabManager()
        val t = tm.newTab("https://a")
        tm.retitle(t, "Some Page")
        assertEquals(listOf("Some Page"), tm.tabs.map { it.title })
    }

    @Test
    fun activeTracksSelection() {
        val tm = TabManager()
        val a = tm.newTab("https://a"); tm.newTab("https://b")
        tm.select(a)
        assertEquals("https://a", tm.active()?.url)
        tm.close(tm.tabs.first { it.id != a.id })
        assertEquals("https://a", tm.active()?.url)
    }
}
