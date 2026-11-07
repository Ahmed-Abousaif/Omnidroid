package com.omnidroid.app.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.omnidroid.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

class GitHubAppUpdater(
    context: Context,
    private val okHttpClient: OkHttpClient,
) : AppUpdater {
    private val appContext = context.applicationContext
    private val prefs = AppUpdatePreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    private var pendingRelease: ParsedGitHubRelease? = null

    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    override val supportsGitHubOptions: Boolean = true

    init {
        AppUpdateBus.setListener { update ->
            _state.value = update
        }
        restoreCachedAvailable()
    }

    override suspend fun checkForUpdate(force: Boolean) {
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO
        if (owner.startsWith("TODO") || repo.startsWith("TODO") || owner.isBlank() || repo.isBlank()) {
            Timber.w("GitHub update check skipped: set GITHUB_OWNER / GITHUB_REPO BuildConfig fields")
            _state.value = if (force) UpdateState.Error("GitHub repo not configured") else UpdateState.Idle
            return
        }

        _state.value = UpdateState.Checking
        val result =
            withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease(owner, repo) }
            }

        result.fold(
            onSuccess = { release ->
                if (release == null) {
                    prefs.clearCachedAvailable()
                    pendingRelease = null
                    _state.value = if (force) UpdateState.UpToDate else UpdateState.Idle
                    return
                }
                val current = BuildConfig.VERSION_NAME
                if (!SemVer.isNewerThan(release.version, current)) {
                    prefs.clearCachedAvailable()
                    pendingRelease = null
                    _state.value = if (force) UpdateState.UpToDate else UpdateState.Idle
                    return
                }
                if (prefs.skippedVersion != null &&
                    SemVer.compare(prefs.skippedVersion!!, release.version) == 0
                ) {
                    pendingRelease = null
                    _state.value = UpdateState.Idle
                    return
                }
                pendingRelease = release
                prefs.cacheAvailable(release)
                _state.value = UpdateState.Available(release.version, release.notes)
            },
            onFailure = { error ->
                Timber.d(error, "GitHub update check failed")
                restoreCachedAvailable()
                if (force && _state.value !is UpdateState.Available) {
                    _state.value = UpdateState.Error(friendlyError(error))
                } else if (_state.value is UpdateState.Checking) {
                    _state.value = UpdateState.Idle
                }
            },
        )
    }

    override fun startUpdate(
        activity: Activity,
        @Suppress("UNUSED_PARAMETER") updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        val release =
            pendingRelease
                ?: cachedReleaseOrNull()
                ?: return
        pendingRelease = release
        scope.launch {
            downloadAndInstall(activity, release)
        }
    }

    override fun completeFlexibleUpdate() = Unit

    override fun dismissAvailable() {
        if (_state.value is UpdateState.Available ||
            _state.value is UpdateState.UpToDate ||
            _state.value is UpdateState.Error
        ) {
            _state.value = UpdateState.Idle
        }
    }

    override fun skipCurrentVersion() {
        val version =
            (pendingRelease?.version)
                ?: prefs.cachedAvailableVersion
                ?: (_state.value as? UpdateState.Available)?.version
                ?: return
        prefs.skippedVersion = SemVer.normalize(version)
        _state.value = UpdateState.Idle
    }

    override fun onHostResumed(activity: Activity) {
        if (_state.value is UpdateState.ReadyToInstall || prefs.pendingApkPath != null) {
            onUnknownSourcesReturned(activity)
        }
    }

    override fun onUnknownSourcesReturned(activity: Activity) {
        val path = prefs.pendingApkPath ?: return
        val apk = File(path)
        if (!apk.exists()) {
            prefs.pendingApkPath = null
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { installApk(activity, apk) }
                    .onFailure { error ->
                        Timber.w(error, "Resume install failed")
                        apk.delete()
                        prefs.pendingApkPath = null
                        _state.value = UpdateState.Error(error.message)
                    }
            }
        }
    }

    private fun restoreCachedAvailable() {
        val version = prefs.cachedAvailableVersion ?: return
        if (prefs.skippedVersion != null && SemVer.compare(prefs.skippedVersion!!, version) == 0) {
            return
        }
        if (!SemVer.isNewerThan(version, BuildConfig.VERSION_NAME)) {
            prefs.clearCachedAvailable()
            return
        }
        pendingRelease = cachedReleaseOrNull()
        _state.value =
            UpdateState.Available(
                version = version,
                notes = prefs.cachedAvailableNotes.orEmpty(),
            )
    }

    private fun cachedReleaseOrNull(): ParsedGitHubRelease? {
        val version = prefs.cachedAvailableVersion ?: return null
        val url = prefs.cachedApkUrl ?: return null
        return ParsedGitHubRelease(
            version = version,
            notes = prefs.cachedAvailableNotes.orEmpty(),
            apkDownloadUrl = url,
            apkAssetName = url.substringAfterLast('/'),
            sha256DownloadUrl = prefs.cachedSha256Url,
            sha256FromNotes = prefs.cachedSha256Notes,
        )
    }

    private fun fetchLatestRelease(
        owner: String,
        repo: String,
    ): ParsedGitHubRelease? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Omnidroid-Updater")
                .get()
                .build()
        okHttpClient.newCall(request).execute().use { response ->
            when (response.code) {
                403, 429 -> error("GitHub rate limited (${response.code})")
                in 200..299 -> {
                    val body = response.body?.string().orEmpty()
                    return GitHubReleaseParser.parseLatestReleaseJson(body)
                }
                else -> error("GitHub HTTP ${response.code}")
            }
        }
    }

    private suspend fun downloadAndInstall(
        activity: Activity,
        release: ParsedGitHubRelease,
    ) {
        _state.value = UpdateState.Downloading(0f)
        val apkFile =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(appContext.cacheDir, "app-updates").apply { mkdirs() }
                    val dest = File(dir, release.apkAssetName.ifBlank { "update.apk" })
                    if (dest.exists()) dest.delete()
                    downloadWithProgress(release.apkDownloadUrl, dest) { progress ->
                        _state.value = UpdateState.Downloading(progress)
                    }
                    verifySha256(release, dest)
                    dest
                }.getOrElse { error ->
                    Timber.w(error, "APK download/verify failed")
                    _state.value = UpdateState.Error(friendlyError(error))
                    null
                }
            } ?: return

        prefs.pendingApkPath = apkFile.absolutePath

        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.ReadyToInstall
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            runCatching { activity.startActivity(intent) }
            return
        }

        withContext(Dispatchers.IO) {
            runCatching { installApk(activity, apkFile) }
                .onSuccess {
                    // System confirmation UI takes over; clear the in-app banner.
                    _state.value = UpdateState.Idle
                }
                .onFailure { error ->
                    Timber.w(error, "PackageInstaller failed")
                    apkFile.delete()
                    prefs.pendingApkPath = null
                    _state.value = UpdateState.Error(error.message)
                }
        }
    }

    private fun downloadWithProgress(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ) {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "Omnidroid-Updater")
                .get()
                .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download HTTP ${response.code}")
            val body = response.body ?: error("Empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0L) {
                            onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
        }
        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            error("Downloaded APK was empty")
        }
    }

    private fun verifySha256(
        release: ParsedGitHubRelease,
        apk: File,
    ) {
        val expected =
            release.sha256FromNotes
                ?: release.sha256DownloadUrl?.let { fetchSha256(it) }
                ?: return
        val actual = sha256Of(apk)
        if (!actual.equals(expected, ignoreCase = true)) {
            apk.delete()
            error("SHA-256 mismatch")
        }
    }

    private fun fetchSha256(url: String): String? {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "Omnidroid-Updater")
                .get()
                .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return GitHubReleaseParser.parseSha256FileContents(response.body?.string().orEmpty())
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun installApk(
        context: Context,
        apk: File,
    ) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("omnidroid-update", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val intent =
                Intent(context, AppUpdateInstallReceiver::class.java).apply {
                    action = AppUpdateInstallReceiver.ACTION
                }
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending =
                PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun friendlyError(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("rate limited", ignoreCase = true) -> msg
            msg.contains("Unable to resolve host", ignoreCase = true) -> "Offline"
            msg.contains("Failed to connect", ignoreCase = true) -> "Offline"
            msg.isNotBlank() -> msg
            else -> "Update check failed"
        }
    }
}
