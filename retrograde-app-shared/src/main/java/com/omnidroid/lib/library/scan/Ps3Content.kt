package com.omnidroid.lib.library.scan

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Ps3Content(val titleId: String, val title: String, val category: String) {
    val playable: Boolean get() = category in setOf("DG", "HG")

    companion object {
        fun read(context: android.content.Context, uri: android.net.Uri): Ps3Content? = runCatching {
            com.omnidroid.lib.storage.local.ChannelBytes.open(context, uri)?.use { source ->
                val bytes = if (uri.lastPathSegment?.endsWith(".sfo", true) == true) {
                    source.read(0, minOf(source.size, 1024 * 1024L).toInt())
                } else {
                    Iso9660.readFile(DiscImages.userSectors(source, SectorLayout(2048, 0)), "PS3_GAME/PARAM.SFO")
                }
                bytes?.let { parse(it) }
            }
        }.getOrNull() ?: cached(context, uri)

        private fun cacheKey(uri: android.net.Uri) = "${uri.scheme}:${uri.authority.orEmpty()}:${uri.path}"

        fun cache(context: android.content.Context, uri: android.net.Uri, info: Ps3Content) {
            context.getSharedPreferences("ps3-metadata", android.content.Context.MODE_PRIVATE).edit()
                .putString(cacheKey(uri), "${info.titleId}\n${info.category}\n${info.title}")
                .putString(cacheKey(uri) + ":stamp", fingerprint(context, uri)).apply()
        }

        private fun cached(context: android.content.Context, uri: android.net.Uri): Ps3Content? {
            val prefs = context.getSharedPreferences("ps3-metadata", android.content.Context.MODE_PRIVATE)
            val stamp = fingerprint(context, uri) ?: return null
            if (prefs.getString(cacheKey(uri) + ":stamp", null) != stamp) return null
            val fields = prefs.getString(cacheKey(uri), null)?.split('\n', limit = 3) ?: return null
            return if (fields.size == 3 && fields[0].matches(Regex("[A-Z]{4}[0-9]{5}"))) Ps3Content(fields[0], fields[2], fields[1]) else null
        }

        private fun fingerprint(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
            if (uri.scheme == "file") {
                val file = java.io.File(requireNotNull(uri.path))
                if (file.isFile) "${file.length()}:${file.lastModified()}" else null
            } else {
                val columns = arrayOf(android.provider.DocumentsContract.Document.COLUMN_SIZE, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                context.contentResolver.query(uri, columns, null, null, null)?.use {
                    if (it.moveToFirst()) "${it.getLong(0)}:${it.getLong(1)}" else null
                }
            }
        }.getOrNull()

        /** Read only the bounded SFO index/string tables, never game payloads. */
        fun parse(bytes: ByteArray): Ps3Content? = runCatching {
            if (bytes.size !in 20..(1024 * 1024) || !bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0, 0x50, 0x53, 0x46))) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val keys = buffer.getInt(8)
            val data = buffer.getInt(12)
            val count = buffer.getInt(16)
            if (keys !in 20..bytes.size || data !in keys..bytes.size || count !in 1..256 || 20L + count * 16L > keys) return null
            val values = mutableMapOf<String, String>()
            repeat(count) { index ->
                val offset = 20 + index * 16
                val keyOffset = keys.toLong() + (buffer.getShort(offset).toInt() and 0xffff)
                val format = buffer.getShort(offset + 2).toInt() and 0xffff
                val size = buffer.getInt(offset + 4)
                val valueOffset = data.toLong() + buffer.getInt(offset + 12)
                if (keyOffset !in keys.toLong() until data.toLong() || valueOffset < data || size !in 0..65536 || valueOffset + size > bytes.size) return null
                val keyEnd = (keyOffset.toInt() until data).firstOrNull { bytes[it] == 0.toByte() } ?: return null
                if (format == 0x0204) {
                    val key = bytes.copyOfRange(keyOffset.toInt(), keyEnd).toString(Charsets.UTF_8)
                    values[key] = bytes.copyOfRange(valueOffset.toInt(), valueOffset.toInt() + size).toString(Charsets.UTF_8).trimEnd('\u0000').trim()
                }
            }
            val id = values["TITLE_ID"]?.takeIf { it.matches(Regex("[A-Z]{4}[0-9]{5}")) } ?: return null
            val title = values["TITLE"]?.takeIf { it.isNotBlank() } ?: id
            Ps3Content(id, title, values["CATEGORY"] ?: return null)
        }.getOrNull()
    }
}
