package com.audiora.domain.util

import android.net.Uri

/**
 * Converts a raw SAF content URI or absolute file path into a clean, human-readable
 * relative path for display purposes (e.g. "/Downloads/Audiobooks").
 *
 * SAF tree URIs produce nested paths like:
 *   tree/primary:Downloads/Audiobooks/document/primary:Downloads/Audiobooks/file.m4b
 * This function sequentially strips all known SAF prefixes so only the meaningful
 * file path remains. The display path is for UI only — the original URI is preserved
 * for all file access operations.
 */
fun toDisplayPath(uriStr: String?): String {
    if (uriStr.isNullOrBlank()) return ""

    return try {
        val uri = Uri.parse(uriStr)
        if (uri.scheme == "content") {
            val path = uri.path ?: return uriStr

            // Extract meaningful subpath after the first SAF prefix
            var subPath: String? = when {
                path.contains("tree/primary:") ->
                    path.substringAfter("tree/primary:")
                path.contains("document/primary:") ->
                    path.substringAfter("document/primary:")
                path.contains("primary:") ->
                    path.substringAfter("primary:")
                else -> null
            }

            if (subPath != null) {
                // Nested SAF paths (tree/primary:X/document/primary:X/file) need
                // a second pass to strip the inner document/primary: prefix
                if ("document/primary:" in subPath) {
                    subPath = subPath.substringAfter("document/primary:")
                } else if ("tree/primary:" in subPath) {
                    subPath = subPath.substringAfter("tree/primary:")
                }
                val decoded = decodePath(subPath)
                return "/$decoded"
            }

            // Fallback: extract the last path segment
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank()) {
                val decoded = java.net.URLDecoder.decode(lastSegment, "UTF-8")
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

        // Final cleanup: strip any remaining SAF artifact prefixes
        uriStr
            .removePrefix("primary:")
            .removePrefix("/primary:")
            .removePrefix("document/primary:")
            .removePrefix("tree/primary:")
    } catch (e: Exception) {
        uriStr
    }
}

private fun decodePath(encoded: String): String {
    val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
    return if (decoded.startsWith("primary:")) {
        decoded.removePrefix("primary:")
    } else {
        decoded
    }
}
