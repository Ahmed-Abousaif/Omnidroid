/*
 * RetrogradeApplicationModule.kt
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

package com.swordfish.lemuroid.app

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.cast.CastDisplayManager
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.tv.channel.ChannelHandler
import com.swordfish.lemuroid.app.tv.settings.BiosPreferences
import com.swordfish.lemuroid.app.tv.settings.CoresSelectionPreferences
import com.swordfish.lemuroid.ext.feature.core.CoreUpdaterImpl
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.ext.feature.savesync.SaveSyncManagerImpl
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.dao.GameSearchDao
import com.swordfish.lemuroid.lib.library.db.dao.Migrations
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.migration.DesmumeMigrationHandler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.saves.SavesCoherencyEngine
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.lemuroid.lib.savesync.GameCloudSyncPreferences
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.StorageProvider
import com.swordfish.lemuroid.lib.storage.StorageProviderRegistry
import com.swordfish.lemuroid.lib.storage.local.LocalStorageProvider
import com.swordfish.lemuroid.lib.storage.local.StorageAccessFrameworkProvider
import com.swordfish.lemuroid.metadata.libretrodb.LibretroDBMetadataProvider
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import dagger.Lazy
import android.app.Activity
import com.swordfish.lemuroid.app.mobile.feature.main.MainActivity
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LemuroidApplicationBindsModule {
    @dagger.Binds
    abstract fun saveSyncManager(saveSyncManagerImpl: SaveSyncManagerImpl): SaveSyncManager
}

@Module
@InstallIn(ActivityComponent::class)
object LemuroidActivityModule {
    @Provides
    @ActivityScoped
    fun settingsInteractor(
        activity: Activity,
        directoriesManager: DirectoriesManager,
    ) = SettingsInteractor(activity, directoriesManager)

    @Provides
    @ActivityScoped
    fun gameInteractor(
        activity: Activity,
        retrogradeDb: RetrogradeDatabase,
        shortcutsGenerator: ShortcutsGenerator,
        gameLauncher: GameLauncher,
        saveSyncManager: SaveSyncManager,
    ): GameInteractor {
        val mainActivity = activity as MainActivity
        return GameInteractor(
            mainActivity,
            retrogradeDb,
            TVHelper.isTV(activity),
            shortcutsGenerator,
            gameLauncher,
            saveSyncManager,
            mainActivity::requestNotificationPermission,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LemuroidApplicationModule {
    @Provides
    @Singleton
    fun libretroDBManager(
        @ApplicationContext context: Context,
    ) = LibretroDBManager(context)

    @Provides
    @Singleton
    fun retrogradeDb(
        @ApplicationContext context: Context,
    ) = Room.databaseBuilder(context, RetrogradeDatabase::class.java, RetrogradeDatabase.DB_NAME)
            .addCallback(GameSearchDao.CALLBACK)
            .addMigrations(
                GameSearchDao.MIGRATION,
                Migrations.VERSION_8_9,
                Migrations.VERSION_9_10,
                Migrations.VERSION_10_11,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun gameMetadataProvider(libretroDBManager: LibretroDBManager): GameMetadataProvider =
        LibretroDBMetadataProvider(libretroDBManager)

    @Provides
    @Singleton
    @IntoSet
    fun localSAFStorageProvider(
        @ApplicationContext context: Context,
    ): StorageProvider = StorageAccessFrameworkProvider(context)

    @Provides
    @Singleton
    @IntoSet
    fun localGameStorageProvider(
        @ApplicationContext context: Context,
        directoriesManager: DirectoriesManager,
    ): StorageProvider = LocalStorageProvider(context, directoriesManager)

    @Provides
    @Singleton
    fun gameStorageProviderRegistry(
        @ApplicationContext context: Context,
        providers: Set<@JvmSuppressWildcards StorageProvider>,
    ) = StorageProviderRegistry(context, providers)

    @Provides
    @Singleton
    fun lemuroidLibrary(
        db: RetrogradeDatabase,
        storageProviderRegistry: Lazy<StorageProviderRegistry>,
        gameMetadataProvider: Lazy<GameMetadataProvider>,
        biosManager: BiosManager,
    ) = LemuroidLibrary(db, storageProviderRegistry, gameMetadataProvider, biosManager)

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .build()

    @Provides
    @Singleton
    fun retrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://example.com/")
            .build()

    @Provides
    @Singleton
    fun directoriesManager(
        @ApplicationContext context: Context,
    ) = DirectoriesManager(context)

    @Provides
    @Singleton
    fun statesManager(directoriesManager: DirectoriesManager) = StatesManager(directoriesManager)

    @Provides
    @Singleton
    fun savesManager(directoriesManager: DirectoriesManager) = SavesManager(directoriesManager)

    @Provides
    @Singleton
    fun statesPreviewManager(directoriesManager: DirectoriesManager) = StatesPreviewManager(directoriesManager)

    @Provides
    @Singleton
    fun coreManager(
        directoriesManager: DirectoriesManager,
        retrofit: Retrofit,
    ): CoreUpdater = CoreUpdaterImpl(directoriesManager, retrofit)

    @Provides
    @Singleton
    fun coreVariablesManager(sharedPreferences: Lazy<SharedPreferences>) = CoreVariablesManager(sharedPreferences)

    @Provides
    @Singleton
    fun gameLoader(
        lemuroidLibrary: LemuroidLibrary,
        statesManager: StatesManager,
        savesManager: SavesManager,
        coreVariablesManager: CoreVariablesManager,
        retrogradeDatabase: RetrogradeDatabase,
        savesCoherencyEngine: SavesCoherencyEngine,
        directoriesManager: DirectoriesManager,
        biosManager: BiosManager,
        desmumeMigrationHandler: DesmumeMigrationHandler,
        coreUpdater: CoreUpdater,
    ) = GameLoader(
        lemuroidLibrary,
        statesManager,
        savesManager,
        coreVariablesManager,
        retrogradeDatabase,
        savesCoherencyEngine,
        directoriesManager,
        biosManager,
        desmumeMigrationHandler,
        coreUpdater,
    )

    @Provides
    @Singleton
    fun inputDeviceManager(
        @ApplicationContext context: Context,
        sharedPreferences: Lazy<SharedPreferences>,
    ) = InputDeviceManager(context, sharedPreferences)

    @Provides
    @Singleton
    fun biosManager(directoriesManager: DirectoriesManager) = BiosManager(directoriesManager)

    @Provides
    @Singleton
    fun biosPreferences(biosManager: BiosManager) = BiosPreferences(biosManager)

    @Provides
    @Singleton
    fun coresSelection(
        sharedPreferences: Lazy<SharedPreferences>,
        desmumeMigrationHandler: DesmumeMigrationHandler,
    ) = CoresSelection(sharedPreferences, desmumeMigrationHandler)

    @Provides
    @Singleton
    fun coreSelectionPreferences() = CoresSelectionPreferences()

    @Provides
    @Singleton
    fun savesCoherencyEngine(
        savesManager: SavesManager,
        statesManager: StatesManager,
    ) = SavesCoherencyEngine(savesManager, statesManager)

    @Provides
    @Singleton
    fun saveSyncManagerImpl(
        @ApplicationContext context: Context,
        directoriesManager: DirectoriesManager,
    ) = SaveSyncManagerImpl(context, directoriesManager)

    @Provides
    @Singleton
    fun desmumeMigrationHandler(directoriesManager: DirectoriesManager) =
        DesmumeMigrationHandler(directoriesManager)

    @Provides
    @Singleton
    fun postGameHandler(retrogradeDatabase: RetrogradeDatabase) =
        GameLaunchTaskHandler(ReviewManager(), retrogradeDatabase)

    @Provides
    @Singleton
    fun shortcutsGenerator(
        @ApplicationContext context: Context,
        retrofit: Retrofit,
    ) = ShortcutsGenerator(context, retrofit)

    @Provides
    @Singleton
    fun channelHandler(
        @ApplicationContext context: Context,
        retrogradeDatabase: RetrogradeDatabase,
        retrofit: Retrofit,
    ) = ChannelHandler(context, retrogradeDatabase, retrofit)

    @Provides
    @Singleton
    fun retroControllerManager(sharedPreferences: Lazy<SharedPreferences>) =
        ControllerConfigsManager(sharedPreferences)

    @Provides
    @Singleton
    fun settingsManager(
        @ApplicationContext context: Context,
        sharedPreferences: Lazy<SharedPreferences>,
    ) = SettingsManager(context, sharedPreferences)

    @Provides
    @Singleton
    fun sharedPreferences(
        @ApplicationContext context: Context,
    ) = SharedPreferencesHelper.getSharedPreferences(context)

    @Provides
    @Singleton
    fun gameCloudSyncPreferences(
        @ApplicationContext context: Context,
    ) = GameCloudSyncPreferences(context)

    @Provides
    @Singleton
    fun castDisplayManager(
        @ApplicationContext context: Context,
    ) = CastDisplayManager(context)

    @Provides
    @Singleton
    fun gameLauncher(
        coresSelection: CoresSelection,
        gameLaunchTaskHandler: GameLaunchTaskHandler,
        saveSyncManager: SaveSyncManager,
        settingsManager: SettingsManager,
        gameCloudSyncPreferences: GameCloudSyncPreferences,
        castDisplayManager: CastDisplayManager,
        inputDeviceManager: InputDeviceManager,
    ) = GameLauncher(
        coresSelection,
        gameLaunchTaskHandler,
        saveSyncManager,
        settingsManager,
        gameCloudSyncPreferences,
        castDisplayManager,
        inputDeviceManager,
    )

    @Provides
    @Singleton
    fun rumbleManager(
        @ApplicationContext context: Context,
        settingsManager: SettingsManager,
        inputDeviceManager: InputDeviceManager,
    ) = RumbleManager(context, settingsManager, inputDeviceManager)
}
