package com.omnidroid.metadata.libretrodb

import android.content.Context
import android.util.Log
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SliceCatalog
import com.omnidroid.lib.core.SliceInstallListener
import com.omnidroid.lib.library.CoreID
import com.omnidroid.metadata.libretrodb.db.LibretroDatabase
import com.omnidroid.metadata.libretrodb.db.entity.VerifiedManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class MetadataSliceInstallerImpl(
    private val database: LibretroDatabase,
    private val http: OkHttpClient,
    private val listener: SliceInstallListener,
) : MetadataSliceInstaller {
    private val mutex = Mutex()

    override suspend fun ensureSlices(
        context: Context,
        coreIDs: List<CoreID>,
    ): Set<String> {
        val installed = mutableSetOf<String>()
        coreIDs.distinct().forEach { core ->
            runCatching { installed += ensureCore(context, core) }
                .onFailure {
                    Log.e(TAG, "Slice install failed for ${core.coreName}", it)
                    Timber.w(it, "Slice install failed for %s", core.coreName)
                }
        }
        if (installed.isNotEmpty()) {
            listener.onSlicesInstalled(installed)
        }
        return installed
    }

    private suspend fun ensureCore(
        context: Context,
        core: CoreID,
    ): Set<String> {
        val expected = SliceCatalog.forCore(core)
        if (expected.isEmpty()) return emptySet()
        val dao = database.sliceDao()
        val missing =
            expected.any { slice ->
                dao.find(slice.id)?.takeIf { it.schemaVersion == SliceCatalog.SCHEMA_VERSION } == null
            }
        if (!missing && dao.verified(core.coreName, GithubCoreDownloader.CORES_VERSION) != null) {
            return emptySet()
        }
        val bundle = openBundle(context, core)
        if (bundle == null) {
            Log.e(TAG, "No slice source for ${core.coreName}")
            return emptySet()
        }
        val manifest =
            runCatching { SliceManifest.parse(bundle.manifestJson) }.getOrElse {
                Log.e(TAG, "Bad slice manifest for ${core.coreName}", it)
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
                    complete = false
                }
                return@forEach
            }
            val imported =
                runCatching {
                    import(context, core, slice, entry, bundle)
                }.onFailure {
                    Log.e(TAG, "Failed to install slice ${slice.id}", it)
                    Timber.w(it, "Failed to install slice %s", slice.id)
                }.isSuccess
            if (imported) installed += slice.id else complete = false
        }
        if (complete) {
            mutex.withLock {
                dao.insertVerified(
                    VerifiedManifest(
                        coreName = core.coreName,
                        coresVersion = GithubCoreDownloader.CORES_VERSION,
                        manifestSha = manifest.manifestSha,
                    ),
                )
            }
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
            error("Slice ${slice.id} checksum or size did not match (got ${gzip.size}, expected ${entry.size})")
        }
        val sqlite = File(context.cacheDir, "slices/${slice.id}.sqlite")
        sqlite.parentFile?.mkdirs()
        GZIPInputStream(gzip.inputStream()).use { input ->
            sqlite.outputStream().use { output -> input.copyTo(output) }
        }
        mutex.withLock {
            val db = database.openHelper.writableDatabase
            val path = sqlite.absolutePath.replace("'", "''")
            val systems = slice.systems.joinToString(",") { "'$it'" }
            // ATTACH/DETACH must sit outside the write transaction. DETACH inside an open
            // transaction fails with "database slice is locked", leaving the alias attached
            // so every later slice hits "database slice is already in use".
            runCatching { db.execSQL("DETACH DATABASE slice") }
            db.execSQL("ATTACH DATABASE '$path' AS slice")
            try {
                db.beginTransaction()
                try {
                    db.execSQL("DELETE FROM games WHERE system IN ($systems)")
                    db.execSQL(
                        """
                        INSERT INTO games (name, system, crc32, serial, code, size, romHash, normalizedName, rawName)
                        SELECT name, system, crc32, serial, code, size, romHash, normalizedName, rawName FROM slice.games
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
                    db.endTransaction()
                }
            } finally {
                runCatching { db.execSQL("DETACH DATABASE slice") }
            }
        }
        sqlite.delete()
    }

    private suspend fun openBundle(
        context: Context,
        core: CoreID,
    ): SliceBundle? {
        assetBundle(context, "libretro-db/${core.coreName}")?.let { return it }
        assetBundle(context, "libretro-db/bundled")?.let { return it }
        return githubBundle(core)
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

    private suspend fun githubBundle(core: CoreID): SliceBundle? {
        val base =
            GithubCoreDownloader.BASE_URI.buildUpon()
                .appendEncodedPath(
                    "${GithubCoreDownloader.CORES_VERSION}/omnidroid_core_${core.coreName}/src/main/assets/libretro-db/${core.coreName}",
                )
                .build()
                .toString()
                .trimEnd('/')
        val manifestUrl = "$base/manifest.json"
        val manifest =
            runCatching { httpGet(manifestUrl) }.getOrElse {
                Log.e(TAG, "Slice manifest failed for ${core.coreName}: $manifestUrl", it)
                return null
            }?.toString(Charsets.UTF_8) ?: return null
        return object : SliceBundle {
            override val manifestJson = manifest

            override suspend fun read(fileName: String): ByteArray {
                return httpGet("$base/$fileName")
                    ?: error("Empty slice download for $fileName")
            }
        }
    }

    private suspend fun httpGet(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val response =
                http.newCall(Request.Builder().url(url).get().build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    Log.e(TAG, "HTTP ${it.code} for $url")
                    return@withContext null
                }
                it.body?.bytes()
            }
        }
}

private const val TAG = "OmnidroidSlices"

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
