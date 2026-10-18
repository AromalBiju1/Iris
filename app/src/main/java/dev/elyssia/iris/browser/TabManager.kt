package dev.elyssia.iris.browser

import androidx.compose.runtime.mutableStateListOf

/**
 * Spec §7 tab management: stable ids, new/close/switch, per-tab title/url,
 * private-vs-normal session designation and reopen-recently-closed.
 */
class TabManager {
    private val _tabs = mutableStateListOf<TabState>()
    val tabs: List<TabState> get() = _tabs.toList()
    private var counter = 0
    private var activeIndex = -1

    private sealed interface Closed {
        data class Snapshot(val tab: TabState) : Closed
        data object None : Closed
    }
    private var lastClosed: Closed = Closed.None

    fun newTab(url: String? = null, isPrivate: Boolean = false): TabState {
        val t = TabState(id = counter++, url = url, title = "New Tab", isPrivate = isPrivate)
        _tabs.add(t)
        activeIndex = _tabs.size - 1
        return t
    }

    fun active(): TabState? = if (activeIndex in _tabs.indices) _tabs[activeIndex] else null

    fun close(tab: TabState) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        lastClosed = Closed.Snapshot(_tabs[i])
        _tabs.removeAt(i)
        activeIndex = when {
            _tabs.isEmpty() -> -1
            activeIndex >= _tabs.size -> _tabs.size - 1
            i < activeIndex -> activeIndex - 1
            else -> activeIndex
        }
    }

    /** Reopen recently closed tab. Private tabs are never restored. */
    fun reopenClosed(): TabState? {
        val snap = (lastClosed as? Closed.Snapshot)?.takeIf { !it.tab.isPrivate } ?: return null
        lastClosed = Closed.None
        val t = TabState(id = counter++, url = snap.tab.url, title = snap.tab.title)
        _tabs.add(t)
        activeIndex = _tabs.size - 1
        return t
    }

    fun hasReopenable(): Boolean = (lastClosed as? Closed.Snapshot)?.tab?.isPrivate == false

    fun select(tab: TabState) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) activeIndex = i
    }

    fun openedUrl(tab: TabState, url: String) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) _tabs[i] = TabState(_tabs[i].id, url, _tabs[i].title, _tabs[i].isPrivate)
    }

    fun retitle(tab: TabState, title: String) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) _tabs[i] = TabState(_tabs[i].id, _tabs[i].url, title, _tabs[i].isPrivate)
    }

    fun urlFor(tab: TabState): String? = _tabs.firstOrNull { it.id == tab.id }?.url
    fun titleFor(tab: TabState): String = _tabs.firstOrNull { it.id == tab.id }?.title ?: tab.title
}
