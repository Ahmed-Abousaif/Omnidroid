package com.omnidroid.app.testing

import com.omnidroid.lib.library.OmnidroidLibrary
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Access to the real debug app's library for connected-device integration checks. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface Ps3PhoneTestDependencies {
    fun library(): OmnidroidLibrary
    fun database(): RetrogradeDatabase
}
