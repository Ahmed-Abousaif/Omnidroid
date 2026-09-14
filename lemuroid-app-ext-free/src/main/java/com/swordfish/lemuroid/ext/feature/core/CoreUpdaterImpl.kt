/*
 * CoreManager.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.ext.feature.core

import android.content.Context
import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.GithubCoreDownloader
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import retrofit2.Retrofit

class CoreUpdaterImpl(
    private val directoriesManager: DirectoriesManager,
    retrofit: Retrofit,
) : CoreUpdater {
    private val api = retrofit.create(CoreUpdater.CoreManagerApi::class.java)
    private val githubCoreDownloader = GithubCoreDownloader(directoriesManager, api)

    override suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        val sharedPreferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        coreIDs.asFlow()
            .onEach { retrieveAssets(it, sharedPreferences) }
            .onEach { retrieveFile(context, it) }
            .collect()
    }

    private suspend fun retrieveFile(
        context: Context,
        coreID: CoreID,
    ) {
        CoreLibraryLocator.find(context, coreID) ?: githubCoreDownloader.retrieve(coreID)
    }

    private suspend fun retrieveAssets(
        coreID: CoreID,
        sharedPreferences: SharedPreferences,
    ) {
        CoreID.getAssetManager(coreID)
            .retrieveAssetsIfNeeded(api, directoriesManager, sharedPreferences)
    }
}
