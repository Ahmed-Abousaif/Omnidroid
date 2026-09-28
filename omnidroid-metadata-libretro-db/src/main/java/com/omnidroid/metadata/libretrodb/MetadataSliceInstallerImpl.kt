package com.omnidroid.metadata.libretrodb

import android.content.Context
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SliceCatalog
import com.omnidroid.lib.core.SliceInstallListener
import com.omnidroid.lib.library.CoreID
import com.omnidroid.metadata.libretrodb.db.LibretroDatabase
import com.omnidroid.metadata.libretrodb.db.entity.VerifiedManifest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class MetadataSliceInstallerImpl(
    private val database: LibretroDatabase,
    private val api: CoreUpdater.CoreManagerApi,
    private val listener: SliceInstallListener,
) : MetadataSliceInstaller {
    private val mutex = Mutex()

    override suspend fun ensureSlices(
        context: Context,
        coreIDs: List<CoreID>,
    ): Set<String> =
        mutex.withLock {
            val installed = mutableSetOf<String>()
            coreIDs.distinct().forEach { core ->
                installed += ensureCore(context, core)
            }
            if (installed.isNotEmpty()) {
                listener.onSlicesInstalled(installed)
            }
            installed
        }

    private suspend fun ensureCore(
        context: Context,
        core: CoreID,
    ): Set<String> {
        val expected = SliceCatalog.forCore(core)
        if (expected.isEmpty()) return emptySet()
        val dao = database.sliceDao()
        if (dao.verified(core.coreName, GithubCoreDownloader.CORES_VERSION) != null) {
            return emptySet()
        }
        val bundle = openBundle(context, core) ?: return emptySet()
        val manifest =
            runCatching { SliceManifest.parse(bundle.manifestJson) }.getOrElse {
                Timber.w(it, "Bad slice manifest for %s", core.coreName)
                return emptySet()
            }
        val installed = mutableSetOf<String>()
        var complete = true
        expected.forEach { slice ->
            val entry = manifest.slices.firstOrNull { it.sliceId == slice.id }
            if (entry == null) {
                complete = false
                return@forEach
            }
            val current = dao.find(slice.id)
            if (
                !SliceCatalog.needsInstall(
                    current?.sha256,
                    current?.schemaVersion,
                    entry.sha256,
                    entry.schemaVersion,
                )
            ) {
                if (entry.schemaVersion != SliceCatalog.SCHEMA_VERSION) {
                    Timber.w("Skipping slice %s schema %d", slice.id, entry.schemaVersion)
                    complete = false
                }
                return@forEach
            }
            val imported =
                runCatching {
                    import(context, core, slice, entry, bundle)
                }.onFailure {
                    Timber.w(it, "Failed to install slice %s", slice.id)
                }.isSuccess
            if (imported) installed += slice.id else complete = false
        }
        if (complete) {
            dao.insertVerified(
                VerifiedManifest(
                    coreName = core.coreName,
                    coresVersion = GithubCoreDownloader.CORES_VERSION,
                    manifestSha = manifest.manifestSha,
                ),
            )
        }
        return installed
    }

    private suspend fun import(
        context: Context,
        core: CoreID,
        slice: SliceCatalog.Slice,
        entry: SliceManifestEntry,
        bundle: SliceBundle,
    ) {
        val gzip = bundle.read(entry.file)
        if (gzip.size != entry.size || sha256(gzip) != entry.sha256) {
            error("Slice ${slice.id} checksum or size did not match")
        }
        val sqlite = File(context.cacheDir, "slices/${slice.id}.sqlite")
        sqlite.parentFile?.mkdirs()
        GZIPInputStream(gzip.inputStream()).use { input ->
            sqlite.outputStream().use { output -> input.copyTo(output) }
        }
        val db = database.openHelper.writableDatabase
        val path = sqlite.absolutePath.replace("'", "''")
        val systems = slice.systems.joinToString(",") { "'$it'" }
        db.beginTransaction()
        try {
            db.execSQL("ATTACH DATABASE '$path' AS slice")
            db.execSQL("DELETE FROM games WHERE system IN ($systems)")
            db.execSQL(
                """
                INSERT INTO games (name, system, crc32, serial, code, size, romHash)
                SELECT name, system, crc32, serial, code, size, romHash FROM slice.games
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO installed_slices
                (sliceId, sha256, schemaVersion, rows, sourceCore, installedAt, systems)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    slice.id,
                    entry.sha256,
                    entry.schemaVersion,
                    entry.rows,
                    core.coreName,
                    System.currentTimeMillis(),
                    slice.systems.joinToString(","),
                ),
            )
            db.setTransactionSuccessful()
        } finally {
            runCatching { db.execSQL("DETACH DATABASE slice") }
            db.endTransaction()
        }
        sqlite.delete()
    }

    private suspend fun openBundle(
        context: Context,
        core: CoreID,
    ): SliceBundle? {
        assetBundle(context, "libretro-db/${core.coreName}")?.let { return it }
        assetBundle(context, "libretro-db/bundled")?.let { return it }
        return githubBundle(context, core)
    }

    private fun assetBundle(
        context: Context,
        directory: String,
    ): SliceBundle? {
        val manifest =
            runCatching { context.assets.open("$directory/manifest.json").bufferedReader().readText() }.getOrNull()
                ?: return null
        return object : SliceBundle {
            override val manifestJson = manifest

            override suspend fun read(fileName: String): ByteArray =
        context.assets.open("$directory/$fileName").use { it.readBytes() }
        }
    }

    private suspend fun githubBundle(
        context: Context,
        core: CoreID,
    ): SliceBundle? {
        val base =
            GithubCoreDownloader.BASE_URI.buildUpon()
                .appendEncodedPath(
                    "${GithubCoreDownloader.CORES_VERSION}/omnidroid_core_${core.coreName}/src/main/assets/libretro-db/${core.coreName}",
                )
                .build()
        val manifestResponse = api.downloadFile(base.buildUpon().appendPath("manifest.json").build().toString())
        if (!manifestResponse.isSuccessful) return null
        val manifest = manifestResponse.body()?.use { it.string() } ?: return null
        return object : SliceBundle {
            override val manifestJson = manifest

            override suspend fun read(fileName: String): ByteArray {
                val response = api.downloadFile(base.buildUpon().appendPath(fileName).build().toString())
                if (!response.isSuccessful) error("Slice download failed")
                return response.body()?.use { it.bytes() } ?: error("Empty slice download")
            }
        }
    }
}

private interface SliceBundle {
    val manifestJson: String

    suspend fun read(fileName: String): ByteArray
}

internal data class SliceManifest(
    val schemaVersion: Int,
    val manifestSha: String,
    val slices: List<SliceManifestEntry>,
) {
    companion object {
        fun parse(json: String): SliceManifest {
            val root = JSONObject(json)
            val slices = root.getJSONArray("slices")
            return SliceManifest(
                schemaVersion = root.getInt("schemaVersion"),
                manifestSha = root.optString("manifestSha"),
                slices =
                    List(slices.length()) { index ->
                        val item = slices.getJSONObject(index)
                        val systems = item.getJSONArray("systems")
                        SliceManifestEntry(
                            sliceId = item.getString("sliceId"),
                            systems = List(systems.length()) { systems.getString(it) },
                            file = item.getString("file"),
                            sha256 = item.getString("sha256"),
                            size = item.getInt("size"),
                            rows = item.getInt("rows"),
                            schemaVersion = root.getInt("schemaVersion"),
                        )
                    },
            )
        }
    }
}

internal data class SliceManifestEntry(
    val sliceId: String,
    val systems: List<String>,
    val file: String,
    val sha256: String,
    val size: Int,
    val rows: Int,
    val schemaVersion: Int,
)

private fun sha256(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
