package com.omnidroid.app.shared.game

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.omnidroid.R
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.displayToast
import com.omnidroid.lib.launch.ExternalLaunchResolver
import com.omnidroid.lib.launch.FileUriNormalizer
import com.omnidroid.lib.launch.ResolvedLaunchInput
import com.omnidroid.lib.launch.SystemDetector
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.provider.OpenableColumns
import com.omnidroid.lib.library.GameSystem
import java.io.BufferedInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Public exported entry point for opening games from external frontends (Daijishō, ES-DE, Pegasus,
 * Beacon, Arc Browser, DIG, Reset Collection) and Android TV preview programs.
 * Runs as a lightweight trampoline in the main process and boots directly into GameActivity.
 */
@AndroidEntryPoint
class ExternalGameLauncherActivity : AppCompatActivity() {

    @Inject
    lateinit var gameLauncher: GameLauncher

    @Inject
    lateinit var retrogradeDatabase: RetrogradeDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resolved = ExternalLaunchResolver.resolve(intent)
        if (resolved == null) {
            Timber.e("ExternalGameLauncherActivity: Could not resolve input from intent: %s", intent)
            displayToast(R.string.game_loader_error_load_game)
            finish()
            return
        }

        // Handle URI permissions and verify read access
        if (!validateAndTakePermissions(resolved)) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val game = lookupOrBuildGame(resolved)
            if (game == null) {
                withContext(Dispatchers.Main) {
                    Timber.e("ExternalGameLauncherActivity: Unable to determine game system for %s", resolved.uriString)
                    displayToast(R.string.game_loader_error_load_game)
                    finish()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val launched = gameLauncher.launchGameAsync(
                    activity = this@ExternalGameLauncherActivity,
                    game = game,
                    loadSave = true,
                    leanback = TVHelper.isTV(applicationContext),
                    isExternalLaunch = true,
                    onLaunched = {
                        finish()
                    },
                )
                if (!launched) {
                    Timber.e("ExternalGameLauncherActivity: GameLauncher could not launch game")
                    finish()
                }
            }
        }
    }

    private fun validateAndTakePermissions(resolved: ResolvedLaunchInput): Boolean {
        val uri = Uri.parse(resolved.normalizedUriString)
        if (uri.scheme.equals("content", ignoreCase = true)) {
            try {
                val flags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                Timber.d("takePersistableUriPermission not offered or not allowed: %s", e.message)
            }

            val readable = try {
                contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (e: Exception) {
                Timber.e(e, "Cannot open stream for content URI: %s", uri)
                false
            }
            if (!readable) {
                Timber.e("Content URI cannot be read: %s", uri)
                displayToast(R.string.game_loader_error_load_game)
                return false
            }
        } else if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path
            val readable = path != null && File(path).canRead()
            if (!readable) {
                Timber.e("File path cannot be read: %s", path)
                displayToast(R.string.game_loader_error_load_game)
                return false
            }
        }
        return true
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme?.equals("content", ignoreCase = true) == true) {
            val cursor = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            }.getOrNull()
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        val name = it.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return null
    }

    private data class ZipInnerRomInfo(val entryName: String, val extension: String, val system: GameSystem)

    private fun peekZipInnerRom(uri: Uri): ZipInnerRomInfo? {
        return runCatching {
            val inputStream = contentResolver.openInputStream(uri) ?: return@runCatching null
            ZipInputStream(BufferedInputStream(inputStream)).use { zipStream ->
                var entry = zipStream.nextEntry
                var count = 0
                while (entry != null && count < 25) {
                    if (!entry.isDirectory) {
                        val fullName = entry.name
                        val simpleName = fullName.substringAfterLast('/')
                        val ext = simpleName.substringAfterLast('.', "").lowercase(Locale.US)
                        if (ext.isNotEmpty() && ext != "txt" && ext != "nfo" && ext != "diz" && ext != "xml" && ext != "png" && ext != "jpg") {
                            val candidateSystem = SystemDetector.detectSystem(ext)
                            if (candidateSystem != null) {
                                return@runCatching ZipInnerRomInfo(
                                    entryName = simpleName,
                                    extension = ext,
                                    system = candidateSystem,
                                )
                            }
                        }
                    }
                    entry = zipStream.nextEntry
                    count++
                }
                null
            }
        }.getOrNull()
    }

    private suspend fun lookupOrBuildGame(resolved: ResolvedLaunchInput): Game? {
        val gameDao = retrogradeDatabase.gameDao()
        val parsedUri = Uri.parse(resolved.uriString)
        val contentDisplayName = resolveDisplayName(parsedUri)
        val rawFileName = contentDisplayName ?: FileUriNormalizer.extractFileName(resolved.uriString)
        val rawExt = rawFileName?.let { FileUriNormalizer.extractExtension(it) }
            ?: FileUriNormalizer.extractExtension(resolved.uriString)

        val innerRom = if (rawExt.equals("zip", ignoreCase = true)) {
            peekZipInnerRom(parsedUri)
        } else {
            null
        }

        // 1. Direct match on exact stored fileUri
        var matched = gameDao.asyncSelectByFileUri(resolved.uriString)
            ?: gameDao.asyncSelectByFileUri(resolved.normalizedUriString)

        // 2. Fallback: match candidates by fileName (using raw file name OR inner ROM entry name)
        if (matched == null) {
            val candidateNames = listOfNotNull(innerRom?.entryName, rawFileName).distinct()
            for (candidateName in candidateNames) {
                val candidates = gameDao.selectByFileName(candidateName)
                matched = candidates.firstOrNull { FileUriNormalizer.matches(resolved.uriString, it.fileUri) }
                    ?: if (candidates.size == 1) candidates.first() else null
                if (matched != null) break
            }
        }

        // 3. Fallback: check all library games for URI normalization / canonical path match
        if (matched == null) {
            val allGames = gameDao.selectAll()
            matched = allGames.firstOrNull { FileUriNormalizer.matches(resolved.uriString, it.fileUri) }
        }

        if (matched != null) {
            return matched
        }

        // 4. Not in library: detect system from inner ROM or outer file extension + optional SYSTEM extra
        val fileName = innerRom?.entryName ?: rawFileName ?: "Unknown"
        val extension = innerRom?.extension
            ?: rawExt
            ?: return null

        val system = innerRom?.system?.takeIf { resolved.systemExtra.isNullOrBlank() }
            ?: SystemDetector.detectSystem(extension, resolved.systemExtra)
            ?: return null

        val title = (rawFileName ?: fileName).substringBeforeLast('.')

        return Game(
            id = 0,
            fileName = fileName,
            fileUri = resolved.normalizedUriString,
            title = title,
            systemId = system.id.dbname,
            developer = null,
            coverFrontUrl = null,
            lastIndexedAt = System.currentTimeMillis(),
            lastPlayedAt = null,
            isFavorite = false,
        )
    }
}
