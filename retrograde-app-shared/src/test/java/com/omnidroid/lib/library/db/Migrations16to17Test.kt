package com.omnidroid.lib.library.db

import com.omnidroid.lib.library.db.dao.Migrations
import org.junit.Assert.assertEquals
import org.junit.Test

class Migrations16to17Test {
    @Test
    fun migrationTargetsVersion17() {
        assertEquals(16, Migrations.VERSION_16_17.startVersion)
        assertEquals(17, Migrations.VERSION_16_17.endVersion)
    }

    @Test
    fun priorMigrationsRemainChained() {
        assertEquals(15, Migrations.VERSION_15_16.startVersion)
        assertEquals(16, Migrations.VERSION_15_16.endVersion)
        assertEquals(14, Migrations.VERSION_14_15.startVersion)
        assertEquals(15, Migrations.VERSION_14_15.endVersion)
    }
}
