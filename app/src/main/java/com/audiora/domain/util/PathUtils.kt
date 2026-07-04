package com.audiora.domain.util

import android.net.Uri

/**
 * Converts a raw SAF content URI or absolute file path into a clean, human-readable
 * relative path for display purposes (e.g. "/Downloads/Audiobooks").
 *
 * Strips content URI scheme, authority, percent-encoding, and device root prefixes
 * so the user sees only the meaningful portion of the path.
 */
fun toDisplayPath(uriStr: String?): String {
    if (uriStr.isNullOrBlank()) return ""

    return try {
        val uri = Uri.parse(uriStr)
        if (uri.scheme == "content") {
            val path = uri.path ?: return uriStr
            val subPath = when {
                path.contains("tree/primary:") ->
                    path.substringAfter("tree/primary:")
                path.contains("document/primary:") ->
                    path.substringAfter("document/primary:")
                path.contains("primary:") ->
                    path.substringAfter("primary:")
                else -> null
            }
            if (subPath != null) {
                val decoded = decodePath(subPath)
                return "/$decoded"
            }
            // Try to extract any meaningful path from the URI
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank()) {
                val decoded = java.net.URLDecoder.decode(lastSegment, "UTF-8")
                // Remove "primary:" prefix if present
                return if (decoded.startsWith("primary:")) {
                    "/${decoded.removePrefix("primary:")}"
                } else {
                    "/$decoded"
                }
            }
        }

        // Handle absolute file paths (/storage/emulated/0/... or /data/user/0/...)
        val knownPrefixes = listOf(
            "/storage/emulated/0/",
            "/data/user/0/",
            "/data/data/"
        )
        for (prefix in knownPrefixes) {
            if (uriStr.startsWith(prefix)) {
                return "/${uriStr.removePrefix(prefix)}"
            }
        }

        // If it doesn't look like a raw URI, return as-is
        uriStr
    } catch (e: Exception) {
        uriStr
    }
}

private fun decodePath(encoded: String): String {
    val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
    // Remove "primary:" if the decode didn't handle it
    return if (decoded.startsWith("primary:")) {
        decoded.removePrefix("primary:")
    } else {
        decoded
    }
}
