package com.omnidroid.metadata.libretrodb.db

import android.content.Context
import androidx.room.Room
import com.omnidroid.metadata.libretrodb.detect.LibretroDetectDatabase

class LibretroDBManager(private val context: Context) {
    companion object {
        private const val DB_NAME = "libretro-db"
        private const val DETECT_NAME = "libretro-detect"
    }

    val dbInstance: LibretroDatabase by lazy {
        Room.databaseBuilder(context, LibretroDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    val detectInstance: LibretroDetectDatabase by lazy {
        Room.databaseBuilder(context, LibretroDetectDatabase::class.java, DETECT_NAME)
            .createFromAsset("libretro-detect.sqlite")
            .fallbackToDestructiveMigration()
            .build()
    }
}
