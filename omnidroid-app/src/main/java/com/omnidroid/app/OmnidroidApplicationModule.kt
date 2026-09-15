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

package com.omnidroid.app

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.omnidroid.app.shared.cast.CastDisplayManager
import com.omnidroid.app.shared.game.GameLauncher
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.app.shared.rumble.RumbleManager
import com.omnidroid.app.shared.settings.ControllerConfigsManager
import com.omnidroid.app.tv.channel.ChannelHandler
import com.omnidroid.app.tv.settings.BiosPreferences
import com.omnidroid.app.tv.settings.CoresSelectionPreferences
import com.omnidroid.ext.feature.core.CoreUpdaterImpl
import com.omnidroid.ext.feature.review.ReviewManager
import com.omnidroid.ext.feature.savesync.SaveSyncManagerImpl
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.game.GameLoader
import com.omnidroid.lib.library.OmnidroidLibrary
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.dao.GameSearchDao
import com.omnidroid.lib.library.db.dao.Migrations
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.migration.DesmumeMigrationHandler
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.saves.SavesCoherencyEngine
import com.omnidroid.lib.saves.SavesManager
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import com.omnidroid.lib.savesync.GameCloudSyncPreferences
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.storage.DirectoriesManager
import com.omnidroid.lib.storage.StorageProvider
import com.omnidroid.lib.storage.StorageProviderRegistry
import com.omnidroid.lib.storage.local.LocalStorageProvider
import com.omnidroid.lib.storage.local.StorageAccessFrameworkProvider
import com.omnidroid.metadata.libretrodb.LibretroDBMetadataProvider
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import dagger.Lazy
import android.app.Activity
import com.omnidroid.app.mobile.feature.main.MainActivity
import com.omnidroid.app.shared.GameInteractor
import com.omnidroid.app.shared.settings.SettingsInteractor
import com.omnidroid.app.tv.shared.TVHelper
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
abstract class OmnidroidApplicationBindsModule {
    @dagger.Binds
    abstract fun saveSyncManager(saveSyncManagerImpl: SaveSyncManagerImpl): SaveSyncManager
}

@Module
@InstallIn(ActivityComponent::class)
object OmnidroidActivityModule {
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
object OmnidroidApplicationModule {
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
    fun omnidroidLibrary(
        db: RetrogradeDatabase,
        storageProviderRegistry: Lazy<StorageProviderRegistry>,
        gameMetadataProvider: Lazy<GameMetadataProvider>,
        biosManager: BiosManager,
    ) = OmnidroidLibrary(db, storageProviderRegistry, gameMetadataProvider, biosManager)

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
        omnidroidLibrary: OmnidroidLibrary,
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
        omnidroidLibrary,
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
