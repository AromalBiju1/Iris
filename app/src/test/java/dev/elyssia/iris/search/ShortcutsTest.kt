package dev.elyssia.iris.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    @Test fun youtube() =
        assertEquals("https://www.youtube.com/results?search_query=lo%20fi", Shortcuts.resolve("!yt lo fi", null))
    @Test fun github() =
        assertEquals("https://github.com/search?q=compose", Shortcuts.resolve("!gh compose", null))
    @Test fun wikipedia() =
        assertEquals("https://en.wikipedia.org/wiki/Special:Search?search=Iris", Shortcuts.resolve("!wiki Iris", null))
    @Test fun duckduckgo() =
        assertEquals("https://duckduckgo.com/?q=test", Shortcuts.resolve("!ddg test", null))
    @Test fun searxNeedsBase() = assertNull(Shortcuts.resolve("!sx test", null))
    @Test fun searxUsesBase() =
        assertEquals("http://10.0.0.2:8888/search?q=test", Shortcuts.resolve("!sx test", "http://10.0.0.2:8888"))
    @Test fun aliasCaseInsensitive() =
        assertEquals("https://duckduckgo.com/?q=x", Shortcuts.resolve("!DDG x", null))
    @Test fun unknownShortcutPassesThrough() = assertNull(Shortcuts.resolve("!foo bar", null))
    @Test fun plainTextPassesThrough() = assertNull(Shortcuts.resolve("machine learning", null))
}
