package com.omnidroid.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun parsesLatestReleaseWithApkAndNotes() {
        val json =
            """
            {
              "tag_name": "v2.7.0",
              "body": "Fixes and features\nSHA256: abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "Omnidroid-dynamic-universal-release-v2.7.0.apk",
                  "browser_download_url": "https://example.com/app.apk",
                  "size": 123
                },
                {
                  "name": "Omnidroid-dynamic-universal-release-v2.7.0.apk.sha256",
                  "browser_download_url": "https://example.com/app.apk.sha256",
                  "size": 64
                }
              ]
            }
            """.trimIndent()

        val parsed = GitHubReleaseParser.parseLatestReleaseJson(json)
        assertNotNull(parsed)
        assertEquals("2.7.0", parsed!!.version)
        assertTrue(parsed.notes.contains("Fixes and features"))
        assertEquals("https://example.com/app.apk", parsed.apkDownloadUrl)
        assertEquals("https://example.com/app.apk.sha256", parsed.sha256DownloadUrl)
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            parsed.sha256FromNotes,
        )
    }

    @Test
    fun ignoresDraftsAndPrereleases() {
        val draft =
            """
            {"tag_name":"v9.0.0","draft":true,"prerelease":false,"assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}
            """.trimIndent()
        val pre =
            """
            {"tag_name":"v9.0.0","draft":false,"prerelease":true,"assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}
            """.trimIndent()
        assertNull(GitHubReleaseParser.parseLatestReleaseJson(draft))
        assertNull(GitHubReleaseParser.parseLatestReleaseJson(pre))
    }

    @Test
    fun returnsNullWhenNoApkAsset() {
        val json =
            """
            {"tag_name":"v1.0.0","draft":false,"prerelease":false,"assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"}]}
            """.trimIndent()
        assertNull(GitHubReleaseParser.parseLatestReleaseJson(json))
    }

    @Test
    fun parseSha256FileContentsAcceptsBareAndGnuFormats() {
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            GitHubReleaseParser.parseSha256FileContents(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ),
        )
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            GitHubReleaseParser.parseSha256FileContents(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  app.apk",
            ),
        )
        assertEquals(
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            GitHubReleaseParser.parseSha256FileContents(
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc *app.apk\n",
            ),
        )
        assertNull(GitHubReleaseParser.parseSha256FileContents("not-a-hash"))
    }
}
