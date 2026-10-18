package dev.elyssia.iris.browser

class TabManager {
    private val _tabs = mutableListOf<TabState>()
    val tabs: List<TabState> get() = _tabs.toList()
    private var counter = 0
    private var activeIndex = -1

    fun newTab(url: String? = null): TabState {
        val t = TabState(id = counter++, url = url, title = "New Tab")
        _tabs.add(t)
        activeIndex = _tabs.size - 1
        return t
    }

    fun active(): TabState? = if (activeIndex in _tabs.indices) _tabs[activeIndex] else null

    fun close(tab: TabState) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        _tabs.removeAt(i)
        activeIndex = when {
            _tabs.isEmpty() -> -1
            activeIndex >= _tabs.size -> _tabs.size - 1
            i < activeIndex -> activeIndex - 1
            else -> activeIndex
        }
    }

    fun select(tab: TabState) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) activeIndex = i
    }

    fun openedUrl(tab: TabState, url: String) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) _tabs[i] = TabState(tab.id, url, _tabs[i].title)
    }

    fun retitle(tab: TabState, title: String) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) _tabs[i] = TabState(_tabs[i].id, _tabs[i].url, title)
    }
}
