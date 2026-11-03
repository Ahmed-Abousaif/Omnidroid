package org.yuzu.yuzu_emu.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.Keep
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

@Keep
object FileUtil {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getContext(): Context =
        appContext ?: error("FileUtil appContext not initialized")

    @JvmStatic
    fun openContentUri(path: String, openMode: String?): Int {
        return try {
            val context = getContext()
            val uri = Uri.parse(path)
            val pfd = context.contentResolver.openFileDescriptor(uri, openMode ?: "r")
            pfd?.detachFd() ?: -1
        } catch (e: Exception) {
            Timber.e(e, "Cannot open content uri $path")
            -1
        }
    }

    @JvmStatic
    fun getFileSize(path: String): Long {
        if (path.startsWith("content://")) {
            return try {
                val context = getContext()
                context.contentResolver.query(Uri.parse(path), null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        cursor.getLong(sizeIndex)
                    } else {
                        0L
                    }
                } ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    @JvmStatic
    fun exists(path: String): Boolean {
        if (path.startsWith("content://")) {
            return try {
                val context = getContext()
                context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
            } catch (e: Exception) {
                false
            }
        }
        return File(path).exists()
    }

    @JvmStatic
    fun isDirectory(path: String): Boolean {
        return File(path).isDirectory
    }

    @JvmStatic
    fun getFilename(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                val context = getContext()
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } catch (e: Exception) {
                // Fall back to path parsing
            }
        }
        val path = uri.path ?: ""
        val cut = path.lastIndexOf('/')
        return if (cut != -1) path.substring(cut + 1) else path
    }

    @JvmStatic
    fun getExtension(uri: Uri): String {
        val filename = getFilename(uri)
        val dot = filename.lastIndexOf('.')
        return if (dot != -1) filename.substring(dot + 1).lowercase() else ""
    }

    @JvmStatic
    fun copyUriToInternalStorage(sourceUri: Uri, destDir: String): String? {
        return try {
            val context = getContext()
            val filename = getFilename(sourceUri)
            val destFile = File(destDir, filename)
            destFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "copyUriToInternalStorage error")
            null
        }
    }
}
