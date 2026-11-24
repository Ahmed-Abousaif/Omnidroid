package com.omnidroid.app.shared.covers

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.metadata.libretrodb.db.LibretroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class CatalogSeedMigrationTest {
    @Test fun optionalSeedMigrationPreservesInstalledDatabaseAndRoomValidatesSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val current = Room.inMemoryDatabaseBuilder(context, LibretroDatabase::class.java).allowMainThreadQueries().build()
        val statements = ArrayList<String>()
        current.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE type IN ('table', 'index') AND sql IS NOT NULL AND name NOT IN ('sqlite_sequence','android_metadata','room_master_table','catalog_seeds') ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END").use { cursor ->
            while (cursor.moveToNext()) statements += cursor.getString(0)
        }
        current.close()
        val name = "catalog-seeds-migration-test"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(14) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                statements.forEach(db::execSQL)
                db.execSQL("INSERT INTO games (name, system) VALUES ('Preserved game', 'snes')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.writableDatabase
        helper.close()
        val upgraded = Room.databaseBuilder(context, LibretroDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(LibretroDatabase.CATALOG_SEED_MIGRATION).build()
        try {
            upgraded.openHelper.writableDatabase.query("SELECT name FROM games").use { assertTrue(it.moveToFirst()); assertEquals("Preserved game", it.getString(0)) }
            assertNull(upgraded.catalogSeedDao().payload("snes", "missing"))
            upgraded.openHelper.writableDatabase.execSQL("INSERT INTO catalog_seeds(system, lookupKey, payload) VALUES ('snes', 'key', '{}')")
            assertEquals("{}", upgraded.catalogSeedDao().payload("snes", "key"))
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
