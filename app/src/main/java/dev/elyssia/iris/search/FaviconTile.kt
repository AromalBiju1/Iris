package dev.elyssia.iris.search

import java.net.URI

/**
 * Letter-tile data for results — favicon-style visual without any third-party
 * network request (privacy: favicons would leak your searches to an icon CDN).
 */
fun faviconTile(url: String): FaviconTile {
    val host = try {
        URI(url).host.orEmpty().removePrefix("www.")
    } catch (_: Exception) {
        ""
    }
    val letter = host.firstOrNull()?.uppercase() ?: "#"
    val palette = listOf(
        0xFF6C5CE7, 0xFF00B894, 0xFF0984E3, 0xFFE17055,
        0xFF00CEC9, 0xFFD63031, 0xFFE84393, 0xFF2D3436,
        0xFF8E44AD, 0xFF16A085,
    )
    val colorIndex = kotlin.math.abs(host.hashCode()) % palette.size
    return FaviconTile(letter = letter, color = palette[colorIndex])
}

data class FaviconTile(val letter: String, val color: Long)
