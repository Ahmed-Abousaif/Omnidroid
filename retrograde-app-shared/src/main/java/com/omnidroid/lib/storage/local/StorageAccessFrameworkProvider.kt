package com.omnidroid.lib.storage.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.leanback.preference.LeanbackPreferenceFragment
import com.omnidroid.common.kotlin.extractEntryToFile
import com.omnidroid.common.kotlin.isZipped
import com.omnidroid.common.kotlin.writeToFile
import com.omnidroid.lib.R
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.scan.ChdSectors
import com.omnidroid.lib.library.scan.LibraryFileFilter
import com.omnidroid.lib.library.scan.RandomAccessBytes
import com.omnidroid.lib.library.scan.SafDirectorySkipCache
import com.omnidroid.lib.library.scan.SkippedSubtreeTracker
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.RomFiles
import com.omnidroid.lib.storage.StorageFile
import com.omnidroid.lib.storage.StorageListOptions
import com.omnidroid.lib.storage.StorageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class StorageAccessFrameworkProvider(
    private val context: Context,
    private val skipCache: SafDirectorySkipCache? = null,
) : StorageProvider {
    override val id: String = "access_framework"

    override val name: String = context.getString(R.string.local_storage)

    override val uriSchemes = listOf("content")

    override val prefsFragmentClass: Class<LeanbackPreferenceFragment>? = null

    override val enabledByDefault = true

    private val skippedTracker = SkippedSubtreeTracker()

    override fun listBaseStorageFiles(options: StorageListOptions): Flow<List<BaseStorageFile>> {
        return getExternalFolder()?.let { folder ->
            traverseDirectoryEntries(Uri.parse(folder), options)
        } ?: emptyFlow()
    }

    override fun lastSkippedDocumentIds(): Set<String> = skippedTracker.snapshot()

    override fun isLibraryRootReadable(): Boolean {
        val folder = getExternalFolder() ?: return false
        return runCatching {
            val rootUri = Uri.parse(folder)
            val documentId = DocumentsContract.getTreeDocumentId(rootUri) ?: return false
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { true } ?: false
        }.getOrDefault(false)
    }

    override fun openRandomAccess(uri: Uri): RandomAccessBytes? = ChannelBytes.open(context, uri)

    override fun openChd(uri: Uri): ChdSectors? = ChdImages.open(context, uri)

    override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? {
        return DocumentFileParser.parseDocumentFile(context, baseStorageFile)
    }

    private fun getExternalFolder(): String? {
        val prefString = context.getString(R.string.pref_key_extenral_folder)
        val preferenceManager = SharedPreferencesHelper.getLegacySharedPreferences(context)
        return preferenceManager.getString(prefString, null)
    }

    private fun traverseDirectoryEntries(
        rootUri: Uri,
        options: StorageListOptions,
    ): Flow<List<BaseStorageFile>> =
        flow {
            skippedTracker.clear()
            val treeUriString = rootUri.toString()
            val rootId = DocumentsContract.getTreeDocumentId(rootUri) ?: return@flow

            val cache = skipCache
            val reliability = cache?.reliability(treeUriString) ?: SafDirectorySkipCache.Reliability.UNKNOWN
            val forceDeep =
                options.forceDeep ||
                    cache == null ||
                    reliability != SafDirectorySkipCache.Reliability.RELIABLE ||
                    cache.shouldForceDeepThisRun(treeUriString)

            if (forceDeep) {
                Timber.i("SAF deep listing for $treeUriString (force=${options.forceDeep}, reliability=$reliability)")
            }

            data class DirJob(val documentId: String, val knownMtime: Long)

            val queue = ArrayDeque<DirJob>()
            queue.add(DirJob(rootId, knownMtime = 0L))

            val seenDirectoryMtimes = mutableListOf<Long>()
            val visitedDirectoryIds = mutableSetOf<String>()
            val directoryMtimesToStore = mutableMapOf<String, Long>()

            while (queue.isNotEmpty()) {
                val job = queue.removeFirst()
                if (!visitedDirectoryIds.add(job.documentId)) continue

                // !forceDeep implies cache != null (null cache always forces a deep walk).
                if (!forceDeep &&
                    job.knownMtime > 0L &&
                    cache!!.cachedMtime(treeUriString, job.documentId) == job.knownMtime
                ) {
                    skippedTracker.add(job.documentId)
                    directoryMtimesToStore[job.documentId] = job.knownMtime
                    continue
                }

                val result =
                    runCatching {
                        listChildren(rootUri, job.documentId)
                    }
                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "Error while listing files")
                }

                val listing =
                    result.getOrDefault(
                        Listing(emptyList(), emptyList()),
                    )

                emit(listing.files)

                if (job.knownMtime > 0L) {
                    directoryMtimesToStore[job.documentId] = job.knownMtime
                    seenDirectoryMtimes += job.knownMtime
                }

                for (dir in listing.directories) {
                    seenDirectoryMtimes += dir.lastModified
                    if (dir.lastModified > 0L) {
                        directoryMtimesToStore[dir.documentId] = dir.lastModified
                    }
                    queue.add(DirJob(dir.documentId, dir.lastModified))
                }
            }

            cache?.upsertAll(treeUriString, directoryMtimesToStore)
            cache?.pruneMissing(treeUriString, visitedDirectoryIds + directoryMtimesToStore.keys)
            if (forceDeep) {
                cache?.evaluateProbe(treeUriString, seenDirectoryMtimes)
            }

            SkippedSubtreeTracker.lastSkipped = skippedTracker.snapshot()
        }

    private data class DirEntry(val documentId: String, val lastModified: Long)

    private data class Listing(
        val files: List<BaseStorageFile>,
        val directories: List<DirEntry>,
    )

    private fun listChildren(
        treeUri: Uri,
        rootDocumentId: String,
    ): Listing {
        val resultFiles = mutableListOf<BaseStorageFile>()
        val resultDirectories = mutableListOf<DirEntry>()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)

        Timber.d("Querying files in directory: $childrenUri")

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use {
            while (it.moveToNext()) {
                val documentId = it.getString(0)
                val documentName = it.getString(1)
                val documentSize = it.getLong(2)
                val mimeType = it.getString(3)
                val lastModified = if (it.isNull(4)) 0L else it.getLong(4)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    resultDirectories.add(DirEntry(documentId, lastModified))
                } else if (LibraryFileFilter.accept(documentName, documentSize)) {
                    val documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            documentId,
                        )
                    resultFiles.add(
                        BaseStorageFile(
                            name = documentName,
                            size = documentSize,
                            uri = documentUri,
                            path = documentUri.path,
                            lastModified = lastModified,
                        ),
                    )
                }
            }
        }

        return Listing(resultFiles, resultDirectories)
    }

    override fun getGameRomFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val originalDocumentUri = Uri.parse(game.fileUri)
        val originalDocument = DocumentFile.fromSingleUri(context, originalDocumentUri)!!

        val isZipped =
            originalDocument.isZipped() &&
                originalDocument.name != game.fileName &&
                !game.fileName.endsWith(".zip", ignoreCase = true)

        return when {
            isZipped && dataFiles.isEmpty() -> getGameRomFilesZipped(game, originalDocument)
            allowVirtualFiles -> getGameRomFilesVirtual(game, dataFiles)
            else -> getGameRomFilesStandard(game, dataFiles, originalDocument)
        }
    }

    private fun getGameRomFilesStandard(
        game: Game,
        dataFiles: List<DataFile>,
        originalDocument: DocumentFile,
    ): RomFiles {
        val gameEntry = getGameRomStandard(game, originalDocument)
        val dataEntries = dataFiles.map { getDataFileStandard(game, it) }
        return RomFiles.Standard(listOf(gameEntry) + dataEntries)
    }

    private fun getGameRomFilesZipped(
        game: Game,
        originalDocument: DocumentFile,
    ): RomFiles {
        val cacheFile = GameCacheUtils.getCacheFileForGame(SAF_CACHE_SUBFOLDER, context, game)
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return RomFiles.Standard(listOf(cacheFile))
        }

        val rawStream = context.contentResolver.openInputStream(originalDocument.uri)
            ?: error("Cannot open stream for ${originalDocument.uri}")
        val stream = ZipInputStream(BufferedInputStream(rawStream))
        stream.extractEntryToFile(game.fileName, cacheFile)
        return RomFiles.Standard(listOf(cacheFile))
    }

    private fun getGameRomFilesVirtual(
        game: Game,
        dataFiles: List<DataFile>,
    ): RomFiles {
        val gameEntry = getGameRomVirtual(game)
        val dataEntries = dataFiles.map { getDataFileVirtual(it) }
        return RomFiles.Virtual(listOf(gameEntry) + dataEntries)
    }

    private fun getDataFileVirtual(dataFile: DataFile): RomFiles.Virtual.Entry {
        return RomFiles.Virtual.Entry(
            "$VIRTUAL_FILE_PATH/${dataFile.fileName}",
            context.contentResolver.openFileDescriptor(Uri.parse(dataFile.fileUri), "r")!!,
        )
    }

    private fun getDataFileStandard(
        game: Game,
        dataFile: DataFile,
    ): File {
        val cacheFile =
            GameCacheUtils.getDataFileForGame(
                SAF_CACHE_SUBFOLDER,
                context,
                game,
                dataFile,
            )

        if (cacheFile.exists()) {
            return cacheFile
        }

        val stream = context.contentResolver.openInputStream(Uri.parse(dataFile.fileUri))!!
        stream.writeToFile(cacheFile)
        return cacheFile
    }

    private fun getGameRomVirtual(game: Game): RomFiles.Virtual.Entry {
        return RomFiles.Virtual.Entry(
            "$VIRTUAL_FILE_PATH/${game.fileName}",
            context.contentResolver.openFileDescriptor(Uri.parse(game.fileUri), "r")!!,
        )
    }

    private fun getGameRomStandard(
        game: Game,
        originalDocument: DocumentFile,
    ): File {
        val cacheFile = GameCacheUtils.getCacheFileForGame(SAF_CACHE_SUBFOLDER, context, game)

        if (cacheFile.exists()) {
            return cacheFile
        }

        val stream = context.contentResolver.openInputStream(originalDocument.uri)!!
        stream.writeToFile(cacheFile)
        return cacheFile
    }

    override fun getInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    companion object {
        const val SAF_CACHE_SUBFOLDER = "storage-framework-games"
        const val VIRTUAL_FILE_PATH = "/virtual/file/path"
    }
}
