package com.eduvark.app.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats an ISO-8601 timestamp (as returned by the API) into the short
 * locale-aware label used across the designs, e.g. "20 Nov 2026" (id) / "Nov 20, 2026" style patterns kept generic.
 */
fun formatShortDate(iso: String?): String? {
    val parsed = parseIso(iso) ?: return null
    val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    return formatter.format(parsed)
}

/** Formats an ISO-8601 timestamp as "20 Nov 2026, 10.24". */
fun formatDateTime(iso: String?): String? {
    val parsed = parseIso(iso) ?: return null
    val formatter = SimpleDateFormat("d MMM yyyy, HH.mm", Locale.getDefault())
    return formatter.format(parsed)
}

/** Formats an ISO-8601 timestamp as "27 Nov 2026, 23.59 UTC+07:00". */
fun formatDeadline(iso: String?): String? {
    val parsed = parseIso(iso) ?: return null
    val formatter = SimpleDateFormat("d MMM yyyy, HH.mm 'UTC'XXX", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    return formatter.format(parsed)
}

private fun parseIso(iso: String?): Date? {
    if (iso.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in patterns) {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        val result = runCatching { formatter.parse(iso) }.getOrNull()
        if (result != null) return result
    }
    return null
}

/** Human readable file size, e.g. 24_800_000 bytes -> "24,8 MB". */
fun formatBytes(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val value = if (mb >= 1) {
        String.format(Locale.getDefault(), "%.1f", mb) + " MB"
    } else {
        String.format(Locale.getDefault(), "%.0f", kb) + " KB"
    }
    return value
}
