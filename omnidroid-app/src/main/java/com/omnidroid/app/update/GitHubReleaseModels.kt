package com.omnidroid.app.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0,
)

data class ParsedGitHubRelease(
    val version: String,
    val notes: String,
    val apkDownloadUrl: String,
    val apkAssetName: String,
    val sha256DownloadUrl: String?,
    val sha256FromNotes: String?,
)

object GitHubReleaseParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val sha256Hex = Regex("\\b([a-fA-F0-9]{64})\\b")

    fun parseLatestReleaseJson(raw: String): ParsedGitHubRelease? {
        val dto = json.decodeFromString(GitHubReleaseDto.serializer(), raw)
        if (dto.draft || dto.prerelease) return null

        val version = SemVer.normalize(dto.tagName)
        if (version.isEmpty()) return null

        val apk =
            dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return null

        val shaAsset =
            dto.assets.firstOrNull {
                it.name.endsWith(".sha256", ignoreCase = true) ||
                    it.name.equals("${apk.name}.sha256", ignoreCase = true)
            }

        val notes = dto.body.orEmpty()
        val shaFromNotes =
            sha256Hex.find(notes)?.groupValues?.getOrNull(1)?.lowercase()

        return ParsedGitHubRelease(
            version = version,
            notes = notes.trim(),
            apkDownloadUrl = apk.browserDownloadUrl,
            apkAssetName = apk.name,
            sha256DownloadUrl = shaAsset?.browserDownloadUrl,
            sha256FromNotes = shaFromNotes,
        )
    }

    /** Accepts bare hex or `hash  filename` / `hash *filename` lines. */
    fun parseSha256FileContents(raw: String): String? {
        val firstLine = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val match = sha256Hex.find(firstLine) ?: return null
        return match.groupValues[1].lowercase()
    }
}
