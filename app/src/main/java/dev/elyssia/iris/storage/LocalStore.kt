package dev.elyssia.iris.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class HistoryEntry(val url: String, val title: String, val at: Long)
data class Bookmark(val url: String, val title: String)

class LocalStore(context: Context) {
    val db: SQLiteDatabase = context.openOrCreateDatabase("iris.db", Context.MODE_PRIVATE, null).also {
        it.execSQL("CREATE TABLE IF NOT EXISTS history (url TEXT PRIMARY KEY, title TEXT, at INTEGER)")
        it.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (url TEXT PRIMARY KEY, title TEXT)")
        it.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
    }

    fun addHistory(url: String, title: String, at: Long = System.currentTimeMillis()) {
        db.execSQL("INSERT OR REPLACE INTO history (url, title, at) VALUES (?, ?, ?)", arrayOf(url, title, at))
    }

    fun listHistory(limit: Int = 100): List<HistoryEntry> {
        val out = mutableListOf<HistoryEntry>()
        db.rawQuery("SELECT url, title, at FROM history ORDER BY at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out += HistoryEntry(c.getString(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    fun clearHistory() = db.execSQL("DELETE FROM history")

    fun upsertBookmark(url: String, title: String) =
        db.execSQL("INSERT OR REPLACE INTO bookmarks (url, title) VALUES (?, ?)", arrayOf(url, title))

    fun deleteBookmark(url: String) = db.execSQL("DELETE FROM bookmarks WHERE url = ?", arrayOf(url))

    fun listBookmarks(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        db.rawQuery("SELECT url, title FROM bookmarks ORDER BY title", null).use { c ->
            while (c.moveToNext()) out += Bookmark(c.getString(0), c.getString(1))
        }
        return out
    }

    fun wipeAll() {
        clearHistory()
        db.execSQL("DELETE FROM bookmarks")
    }

    fun getSetting(key: String): String? {
        var out: String? = null
        db.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) out = c.getString(0)
        }
        return out
    }

    fun setSetting(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", arrayOf(key, value))
    }
}
