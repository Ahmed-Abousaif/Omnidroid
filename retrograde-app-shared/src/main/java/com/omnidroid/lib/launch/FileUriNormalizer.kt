package com.omnidroid.lib.launch

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Normalizes and compares file URIs and paths across different representations
 * (SAF tree-derived URIs, single document URIs, file:// URIs, and raw filesystem paths).
 */
object FileUriNormalizer {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOCUMENT_SUBPATH = "/document/"
    private const val PRIMARY_EMULATED_PREFIX = "/storage/emulated/0"
    private const val SDCARD_PREFIX = "/sdcard"
    private const val LEGACY_STORAGE_PREFIX = "/storage/emulated/legacy"

    /**
     * Converts a raw string (which may be a filesystem path or a URI) into a standard URI string.
     * Raw filesystem paths starting with "/" are converted to "file://" URIs.
     */
    fun normalizeToUriString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        return if (trimmed.startsWith("/")) {
            val canonicalPath = canonicalizePath(trimmed)
            "file://$canonicalPath"
        } else if (trimmed.startsWith("file://", ignoreCase = true)) {
            val pathPart = trimmed.substring("file://".length)
            val decodedPath = safeUrlDecode(pathPart)
            val canonicalPath = canonicalizePath(decodedPath)
            "file://$canonicalPath"
        } else {
            trimmed
        }
    }

    /**
     * Resolves the canonical filesystem path where derivable.
     *
     * Rules:
     * - "file://..." or raw paths: resolves "/sdcard/" and "/storage/emulated/legacy/" to "/storage/emulated/0/".
     * - "content://com.android.externalstorage.documents/...": extracts the document ID.
     *   If document ID is "primary:<relPath>", resolves to "/storage/emulated/0/<relPath>".
     *   If document ID is "<uuid>:<relPath>", resolves to "/storage/<uuid>/<relPath>".
     * - Returns null if the URI belongs to another content provider whose filesystem mapping is not known.
     */
    fun getCanonicalPath(rawOrUri: String): String? {
        val trimmed = rawOrUri.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("/")) {
            return canonicalizePath(trimmed)
        }

        if (trimmed.startsWith("file://", ignoreCase = true)) {
            val rawPath = trimmed.substring("file://".length)
            val decoded = safeUrlDecode(rawPath)
            return canonicalizePath(decoded)
        }

        if (trimmed.startsWith("content://", ignoreCase = true)) {
            val docId = getDocumentId(trimmed) ?: return null
            val colonIndex = docId.indexOf(':')
            if (colonIndex != -1) {
                val volume = docId.substring(0, colonIndex)
                val relPath = docId.substring(colonIndex + 1).trimStart('/')
                return if (volume.equals("primary", ignoreCase = true)) {
                    canonicalizePath("$PRIMARY_EMULATED_PREFIX/$relPath")
                } else {
                    canonicalizePath("/storage/$volume/$relPath")
                }
            }
        }

        return null
    }

    /**
     * Extracts the external storage documentId (e.g. "primary:Roms/nes/mario.nes")
     * from an externalstorage SAF URI, or derives it from a canonical filesystem path.
     */
    fun getDocumentId(rawOrUri: String): String? {
        val trimmed = rawOrUri.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("content://", ignoreCase = true)) {
            if (trimmed.contains(EXTERNAL_STORAGE_AUTHORITY)) {
                val docIndex = trimmed.indexOf(DOCUMENT_SUBPATH)
                if (docIndex != -1) {
                    val afterDoc = trimmed.substring(docIndex + DOCUMENT_SUBPATH.length)
                        .substringBefore('?')
                        .substringBefore('#')
                    return safeUrlDecode(afterDoc)
                }
            }
            return null
        }

        val canonical = getCanonicalPath(trimmed) ?: return null
        return if (canonical.startsWith(PRIMARY_EMULATED_PREFIX)) {
            val rel = canonical.substring(PRIMARY_EMULATED_PREFIX.length).trimStart('/')
            "primary:$rel"
        } else if (canonical.startsWith("/storage/")) {
            val afterStorage = canonical.substring("/storage/".length).trimStart('/')
            val slashIdx = afterStorage.indexOf('/')
            if (slashIdx != -1) {
                val volume = afterStorage.substring(0, slashIdx)
                val rel = afterStorage.substring(slashIdx + 1)
                "$volume:$rel"
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Extracts the display/file name with percent-encoding decoded.
     * E.g. "Super%20Mario.nes" -> "Super Mario.nes".
     */
    fun extractFileName(rawOrUri: String): String? {
        val trimmed = rawOrUri.trim()
        if (trimmed.isEmpty()) return null

        val stripped = trimmed.substringBefore('?').substringBefore('#')
        val decoded = safeUrlDecode(stripped)

        val lastSlash = decoded.lastIndexOf('/')
        val candidate = if (lastSlash != -1) {
            decoded.substring(lastSlash + 1)
        } else {
            decoded
        }

        val lastColon = candidate.lastIndexOf(':')
        val result = if (lastColon != -1 && lastColon < candidate.length - 1) {
            candidate.substring(lastColon + 1)
        } else {
            candidate
        }

        return result.takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts the lowercased file extension without a leading dot.
     */
    fun extractExtension(rawOrUri: String): String? {
        val fileName = extractFileName(rawOrUri) ?: return null
        val dotIdx = fileName.lastIndexOf('.')
        if (dotIdx == -1 || dotIdx == fileName.length - 1) return null
        return fileName.substring(dotIdx + 1).lowercase(Locale.US)
    }

    /**
     * Determines whether two URIs or paths represent the same game.
     *
     * Matching rules:
     * 1. Direct or decoded string equality.
     * 2. Canonical filesystem path match (case-insensitive for external storage).
     * 3. External storage documentId match (case-insensitive).
     *
     * Never matches on fileName alone.
     */
    fun matches(uriOrPathA: String, uriOrPathB: String): Boolean {
        val trimmedA = uriOrPathA.trim()
        val trimmedB = uriOrPathB.trim()

        if (trimmedA.isEmpty() || trimmedB.isEmpty()) return false

        if (trimmedA.equals(trimmedB, ignoreCase = true)) return true

        val decodedA = safeUrlDecode(trimmedA)
        val decodedB = safeUrlDecode(trimmedB)
        if (decodedA.equals(decodedB, ignoreCase = true)) return true

        val pathA = getCanonicalPath(trimmedA)
        val pathB = getCanonicalPath(trimmedB)
        if (pathA != null && pathB != null && pathA.equals(pathB, ignoreCase = true)) {
            return true
        }

        val docA = getDocumentId(trimmedA)
        val docB = getDocumentId(trimmedB)
        if (docA != null && docB != null && docA.equals(docB, ignoreCase = true)) {
            return true
        }

        return false
    }

    private fun canonicalizePath(path: String): String {
        var clean = path.replace('\\', '/')
        while (clean.contains("//")) {
            clean = clean.replace("//", "/")
        }

        clean = when {
            clean.startsWith(SDCARD_PREFIX) -> PRIMARY_EMULATED_PREFIX + clean.substring(SDCARD_PREFIX.length)
            clean.startsWith(LEGACY_STORAGE_PREFIX) -> PRIMARY_EMULATED_PREFIX + clean.substring(LEGACY_STORAGE_PREFIX.length)
            else -> clean
        }

        val isAbsolute = clean.startsWith("/")
        val segments = clean.split('/')
        val resolved = mutableListOf<String>()
        for (seg in segments) {
            when (seg) {
                "", "." -> {
                    // skip empty or current dir
                }
                ".." -> {
                    if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                }
                else -> resolved.add(seg)
            }
        }
        val prefix = if (isAbsolute) "/" else ""
        return prefix + resolved.joinToString("/")
    }

    fun safeUrlDecode(str: String): String {
        return try {
            URLDecoder.decode(str, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            str
        }
    }
}
