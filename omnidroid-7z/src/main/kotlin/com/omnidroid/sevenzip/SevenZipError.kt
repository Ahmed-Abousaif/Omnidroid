package com.omnidroid.sevenzip

import java.io.IOException

sealed class SevenZipError(
    val code: Int,
    val message: String,
) {
    data object InvalidArgument : SevenZipError(-1, "Invalid 7z archive argument")

    data object IoError : SevenZipError(-2, "I/O error reading or writing 7z archive")

    data object NotSeekable : SevenZipError(-3, "Archive file descriptor is not seekable")

    data object NotAnArchive : SevenZipError(-4, "File is not a valid 7z archive")

    data object CorruptArchive : SevenZipError(-5, "Corrupt or truncated 7z archive")

    data object UnsupportedCoder : SevenZipError(-6, "Unsupported 7z compression method")

    data object Encrypted : SevenZipError(-7, "Encrypted 7z archives are not supported")

    data object MemoryLimitExceeded : SevenZipError(-8, "7z dictionary exceeds memory limit")

    data object CrcMismatch : SevenZipError(-9, "7z entry failed CRC32 verification")

    data object DiskFull : SevenZipError(-10, "Insufficient storage space for 7z extraction")

    data object Cancelled : SevenZipError(-11, "7z extraction was cancelled")

    data object IndexOutOfRange : SevenZipError(-12, "7z entry index out of range")

    data object IsDirectory : SevenZipError(-13, "Selected 7z entry is a directory")

    data object Busy : SevenZipError(-14, "7z archive is already extracting")

    data class Unknown(
        val rawCode: Int,
    ) : SevenZipError(rawCode, "Native 7z error ($rawCode)")

    companion object {
        fun fromCode(code: Int): SevenZipError? =
            when (code) {
                0 -> null
                -1 -> InvalidArgument
                -2 -> IoError
                -3 -> NotSeekable
                -4 -> NotAnArchive
                -5 -> CorruptArchive
                -6 -> UnsupportedCoder
                -7 -> Encrypted
                -8 -> MemoryLimitExceeded
                -9 -> CrcMismatch
                -10 -> DiskFull
                -11 -> Cancelled
                -12 -> IndexOutOfRange
                -13 -> IsDirectory
                -14 -> Busy
                else -> Unknown(code)
            }
    }
}

class SevenZipException(
    val error: SevenZipError,
) : IOException(error.message)
