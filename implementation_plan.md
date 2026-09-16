# User Profile, Console Achievements, Leveling & Play Streaks — v2

Add a persistent user profile that is auto-created on first launch, tracks console unlock achievements, accumulates total playtime into a leveling system, rewards consecutive daily play sessions with streak-based XP bonuses, stores session history in Room, and cloud-syncs profile data when the user has cloud enabled.

## Changes from v1

- ✅ **Session history in Room** — New `GameSession` entity + DAO + migration (v11→v12) instead of deriving playtime only from SharedPreferences.
- ✅ **Cloud-backed profile** — Profile data is serialized to a `profile.json` file and synced via the existing cloud save infrastructure (`CloudSaveFolder.PROFILE`).
- ✅ **Robot icon** — Default profile picture is a bundled robot vector drawable.
- ✅ **XP pacing** — `level = floor(sqrt(totalXP / 100))` curve confirmed.
- ✅ **Day boundaries** — Midnight local time confirmed for streak counting.

---

## Proposed Changes

### Component 1 — Session History in Room (retrograde-app-shared)

A proper session history table in the existing Room database so XP, streaks, and playtime are all derived from real recorded data rather than a running counter.

#### [MODIFY] [RetrogradeDatabase.kt](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/RetrogradeDatabase.kt)

Add `GameSession` to the `@Database` entities list, bump version from 11 → 12, expose a `gameSessionDao()` accessor.

```diff
 @Database(
-    entities = [Game::class, DataFile::class],
-    version = 11,
+    entities = [Game::class, DataFile::class, GameSession::class],
+    version = 12,
     exportSchema = true,
 )
 abstract class RetrogradeDatabase : RoomDatabase() {
     ...
     abstract fun gameDao(): GameDao
     abstract fun dataFileDao(): DataFileDao
+    abstract fun gameSessionDao(): GameSessionDao
 }
```

---

#### [NEW] [GameSession.kt](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/entity/GameSession.kt)

Room entity storing one row per completed play session.

```kotlin
@Entity(
    tableName = "game_sessions",
    indices = [
        Index("id", unique = true),
        Index("gameId"),
        Index("playedAt"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GameSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Int,
    val durationMs: Long,           // session length in milliseconds
    val playedAt: Long,             // epoch ms when session ended
    val xpEarned: Long,             // XP awarded for this session (base × streak multiplier)
    val streakDay: Int,             // the streak count at time of this session
)
```

---

#### [NEW] [GameSessionDao.kt](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/dao/GameSessionDao.kt)

```kotlin
@Dao
interface GameSessionDao {
    @Insert
    suspend fun insert(session: GameSession): Long

    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<GameSession>>

    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<GameSession>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM game_sessions")
    fun observeTotalPlayTime(): Flow<Long>

    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM game_sessions")
    fun observeTotalXP(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM game_sessions WHERE gameId = :gameId")
    fun observePlayTimeForGame(gameId: Int): Flow<Long>

    @Query("SELECT COUNT(DISTINCT date(playedAt / 1000, 'unixepoch', 'localtime')) FROM game_sessions")
    suspend fun countDistinctPlayDays(): Int

    @Query("SELECT * FROM game_sessions WHERE gameId = :gameId ORDER BY playedAt DESC")
    fun observeSessionsForGame(gameId: Int): Flow<List<GameSession>>
}
```

---

#### [MODIFY] [Migrations.kt](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/library/db/dao/Migrations.kt)

Add `VERSION_11_12` migration to create the `game_sessions` table:

```diff
     val VERSION_10_11: Migration =
         object : Migration(10, 11) { ... }
+
+    val VERSION_11_12: Migration =
+        object : Migration(11, 12) {
+            override fun migrate(database: SupportSQLiteDatabase) {
+                database.execSQL("""
+                    CREATE TABLE IF NOT EXISTS `game_sessions` (
+                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
+                        `gameId` INTEGER NOT NULL,
+                        `durationMs` INTEGER NOT NULL,
+                        `playedAt` INTEGER NOT NULL,
+                        `xpEarned` INTEGER NOT NULL,
+                        `streakDay` INTEGER NOT NULL,
+                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON DELETE CASCADE
+                    )
+                """.trimIndent())
+                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_game_sessions_id` ON `game_sessions` (`id`)")
+                database.execSQL("CREATE INDEX IF NOT EXISTS `index_game_sessions_gameId` ON `game_sessions` (`gameId`)")
+                database.execSQL("CREATE INDEX IF NOT EXISTS `index_game_sessions_playedAt` ON `game_sessions` (`playedAt`)")
+            }
+        }
 }
```

---

#### [MODIFY] [OmnidroidApplicationModule.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/OmnidroidApplicationModule.kt)

Register the new migration in the Room builder:

```diff
         .addMigrations(
             GameSearchDao.MIGRATION,
             Migrations.VERSION_8_9,
             Migrations.VERSION_9_10,
             Migrations.VERSION_10_11,
+            Migrations.VERSION_11_12,
         )
```

---

### Component 2 — Profile Data Store (`profile` package — new)

All new files in `omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/`.

#### [NEW] [UserProfileStore.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/UserProfileStore.kt)

SharedPreferences-backed store for lightweight profile metadata, following the [`PlayTimeStore`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamedetails/PlayTimeStore.kt) pattern. Uses `FlowSharedPreferences` for reactive observation.

**Stored keys:**

| Key | Type | Default | Purpose |
|---|---|---|---|
| `profile_tag` | `String` | `"Pro Gamer"` | User's display tag |
| `profile_pic_uri` | `String?` | `null` (→ bundled robot drawable) | Custom profile picture URI |
| `profile_created_at` | `Long` | First-launch timestamp | Profile creation date |
| `profile_current_streak` | `Int` | `0` | Consecutive calendar days played |
| `profile_best_streak` | `Int` | `0` | All-time best streak |
| `profile_last_play_date` | `String` | `""` | ISO date (`yyyy-MM-dd`) of last session |

**Public API:**
- `fun ensureCreated()` — Idempotent; writes defaults only if `profile_created_at` is missing.
- `fun getTag(): String` / `fun setTag(tag: String)`
- `fun getProfilePicUri(): String?` / `fun setProfilePicUri(uri: String?)`
- `fun getCurrentStreak(): Int` / `fun getBestStreak(): Int`
- `fun recordSessionStreak()` — Called per session to update streak counters using day-boundary logic.
- `fun observe*()` — Flow-based reactive observers.
- `fun exportToJson(): String` — Serializes profile fields to JSON for cloud sync.
- `fun importFromJson(json: String)` — Merges remote profile (uses latest `profile_created_at`, highest `best_streak`, keeps local tag/pic if newer).

**Streak logic inside `recordSessionStreak()`:**
1. Get today's date as `yyyy-MM-dd`.
2. If `last_play_date` == today → no-op (already counted today).
3. If `last_play_date` == yesterday → increment `current_streak`, update `best_streak` if needed.
4. Otherwise → reset `current_streak` to 1.
5. Set `last_play_date` = today.

> [!NOTE]
> XP totals are **not** stored in SharedPreferences. They are computed live from Room via `GameSessionDao.observeTotalXP()`. This makes session history the single source of truth for XP/playtime.

---

#### [NEW] [ConsoleAchievementsStore.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/ConsoleAchievementsStore.kt)

Reads from [`RegisteredSystemsStore`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/library/RegisteredSystemsStore.kt) and Room `GameDao.selectSystemsWithCount()` to determine unlocked consoles.

```kotlin
data class ConsoleAchievement(
    val metaSystemID: MetaSystemID,
    val name: String,
    val imageResId: Int,
    val unlocked: Boolean,
)
```

**Milestone tiers:**

| Badge | Threshold | Name |
|---|---|---|
| 🥉 | 3 consoles | "Collector" |
| 🥈 | 10 consoles | "Enthusiast" |
| 🥇 | 15 consoles | "Historian" |
| 💎 | 21 consoles (all) | "Omnidroid Master" |

**Public API:**
- `fun observeAchievements(): Flow<List<ConsoleAchievement>>`
- `fun observeUnlockedCount(): Flow<Int>`
- `fun getMilestoneBadge(unlockedCount: Int): String?`

---

### Component 3 — XP Calculator (pure utility)

#### [NEW] [XPCalculator.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/XPCalculator.kt)

Pure Kotlin object, no Android dependencies, easily unit-testable.

```kotlin
object XPCalculator {
    /** XP required to reach a given level (cumulative). */
    fun xpForLevel(level: Int): Long = (level.toLong() * level) * 100

    /** Current level from total XP. */
    fun levelFromXP(totalXP: Long): Int = floor(sqrt(totalXP.toDouble() / 100)).toInt()

    /** Progress fraction [0.0, 1.0) within the current level. */
    fun progressInLevel(totalXP: Long): Float { ... }

    /** Streak multiplier: 1.0 at streak 0–1, up to 1.7 at streak ≥ 7. */
    fun streakMultiplier(streak: Int): Double = 1.0 + (min(streak, 7) * 0.1)

    /** Base XP from a session: 1 XP per minute played, minimum 1. */
    fun baseXPFromSession(durationMs: Long): Long = max(1, durationMs / 60_000)
}
```

**Level examples:**

| Level | Total XP | Approx. playtime (no streak) |
|---|---|---|
| 1 | 100 | ~1h 40m |
| 5 | 2,500 | ~41h |
| 10 | 10,000 | ~166h |
| 20 | 40,000 | ~666h |

---

### Component 4 — Cloud Sync Integration

Profile data syncs to cloud providers alongside game saves. The approach uses the existing file-based sync infrastructure rather than inventing a new channel.

#### [MODIFY] [CloudSaveFolder.kt](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/savesync/CloudSaveFolder.kt)

Add a `PROFILE` enum entry:

```diff
 enum class CloudSaveFolder(val remoteName: String) {
     SAVES("saves"),
     STATES("states"),
     STATE_PREVIEWS("state-previews"),
+    PROFILE("profile"),
 }
```

---

#### [MODIFY] [DirectoriesManager.kt](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/omnidroid/lib/storage/DirectoriesManager.kt)

Add a profile directory accessor:

```diff
+    fun getProfileDirectory(): File =
+        File(appContext.getExternalFilesDir(null), "profile").apply {
+            mkdirs()
+        }
```

---

#### [NEW] [ProfileSyncHelper.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/ProfileSyncHelper.kt)

Handles writing `profile.json` to the local profile directory before sync, and reading it after sync to merge remote data.

```kotlin
class ProfileSyncHelper(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
    private val userProfileStore: UserProfileStore,
) {
    /** Write current profile to profile/profile.json before sync. */
    fun exportBeforeSync() {
        val json = userProfileStore.exportToJson()
        val file = File(directoriesManager.getProfileDirectory(), "profile.json")
        file.writeText(json)
    }

    /** After sync, read remote profile.json and merge. */
    fun importAfterSync() {
        val file = File(directoriesManager.getProfileDirectory(), "profile.json")
        if (file.exists()) {
            userProfileStore.importFromJson(file.readText())
        }
    }
}
```

---

#### [MODIFY] [SaveSyncManagerImpl.kt](file:///d:/Work/Omnidroid/omnidroid-app-ext-play/src/main/java/com/omnidroid/ext/feature/savesync/SaveSyncManagerImpl.kt)

Two changes to include profile data in the sync:

1. Add `CloudSaveFolder.PROFILE` to the folder map via a new `localRoot` mapping:

```diff
     private fun localRoot(folder: CloudSaveFolder): File =
         when (folder) {
             CloudSaveFolder.SAVES -> directoriesManager.getSavesDirectory()
             CloudSaveFolder.STATES -> directoriesManager.getStatesDirectory()
             CloudSaveFolder.STATE_PREVIEWS -> directoriesManager.getStatesPreviewDirectory()
+            CloudSaveFolder.PROFILE -> directoriesManager.getProfileDirectory()
         }
```

2. In `foldersFor()`, always include `CloudSaveFolder.PROFILE` when any sync is requested:

```diff
     private fun foldersFor(request: SaveSyncRequest): List<CloudSaveFolder> {
         val folders = mutableListOf<CloudSaveFolder>()
+        folders += CloudSaveFolder.PROFILE
         if (request.includeSaves || ...) {
             folders += CloudSaveFolder.SAVES
         }
```

3. Profile files should always be included (not filtered by game filename). In `shouldInclude()`, add early return:

```diff
     private fun shouldInclude(...): Boolean {
+        if (folder == CloudSaveFolder.PROFILE) return true
         if (SaveSyncFileMatcher.matchesAnyGame(relativePath, request.excludedFileNames)) {
```

---

#### [MODIFY] [SaveSyncWork.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/savesync/SaveSyncWork.kt)

Call `ProfileSyncHelper.exportBeforeSync()` before the sync call and `importAfterSync()` after:

```diff
+    @Inject lateinit var directoriesManager: DirectoriesManager

     override suspend fun doWork(): Result {
         ...
+        val profileSync = ProfileSyncHelper(
+            applicationContext,
+            directoriesManager,
+            UserProfileStore(applicationContext),
+        )
+        profileSync.exportBeforeSync()
+
         try {
             saveSyncManager.sync(...)
         } catch (e: Throwable) { ... }
+
+        profileSync.importAfterSync()
+
         return Result.success()
     }
```

---

#### [MODIFY] [SaveBackupManager.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/savesync/SaveBackupManager.kt)

Add `CloudSaveFolder.PROFILE` to the `folderMap()` so profile data is included in export/import backups:

```diff
     private fun folderMap() =
         mapOf(
             CloudSaveFolder.SAVES to directoriesManager.getSavesDirectory(),
             CloudSaveFolder.STATES to directoriesManager.getStatesDirectory(),
             CloudSaveFolder.STATE_PREVIEWS to directoriesManager.getStatesPreviewDirectory(),
+            CloudSaveFolder.PROFILE to directoriesManager.getProfileDirectory(),
         )
```

---

### Component 5 — Profile UI

#### [NEW] [ProfileScreen.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/ProfileScreen.kt)

Full-screen Compose UI. Layout sections:

1. **Header** — Circular profile picture (robot icon default, tappable to pick from gallery) + tag (editable inline via dialog) + level badge.
2. **Level & XP Card** — Level number, animated XP progress bar, `currentXP / nextLevelXP`, total playtime derived from `GameSessionDao.observeTotalPlayTime()`.
3. **Play Streak Card** — Current streak 🔥 with day-dots (last 7 days), best streak, streak multiplier display (`×1.3` etc).
4. **Console Achievements Card** — Grid of console icons; unlocked = full-color, locked = greyed silhouette. Progress bar `X / 21`. Milestone badges.
5. **Recent Sessions** — Last 10 sessions from `GameSessionDao.observeRecent(10)` showing game name, duration, XP earned, date.

Uses [`AppTheme`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/shared/compose/ui/OmnidroidTheme.kt) dark-mode with `HomeChromeBackground` and `LibraryNeonGreen`.

---

#### [NEW] [ProfileViewModel.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/profile/ProfileViewModel.kt)

Standard `ViewModel` + `ViewModelProvider.Factory` (same pattern as [`GameDetailsViewModel`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/gamedetails/GameDetailsViewModel.kt)).

```kotlin
data class UiState(
    val tag: String = "Pro Gamer",
    val profilePicUri: String? = null,
    val level: Int = 0,
    val xpProgress: Float = 0f,
    val xpCurrent: Long = 0,
    val xpForNext: Long = 100,
    val totalPlayTimeMs: Long = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val streakMultiplier: Double = 1.0,
    val achievements: List<ConsoleAchievement> = emptyList(),
    val unlockedConsoles: Int = 0,
    val totalConsoles: Int = 21,
    val milestoneBadge: String? = null,
    val recentSessions: List<GameSession> = emptyList(),
)
```

State combines flows from:
- `UserProfileStore.observe*()` for tag, pic, streak
- `GameSessionDao.observeTotalXP()` → fed into `XPCalculator` for level/progress
- `GameSessionDao.observeTotalPlayTime()` for playtime
- `ConsoleAchievementsStore.observeAchievements()` for console grid
- `GameSessionDao.observeRecent(10)` for recent sessions list

**Actions:** `updateTag(String)`, `updateProfilePic(Uri)`, `removeProfilePic()`

---

### Component 6 — Profile Auto-Creation & Session Recording Hooks

#### [MODIFY] [MainProcessInitializer.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/startup/MainProcessInitializer.kt)

Call `ensureCreated()` on first launch:

```diff
 override fun create(context: Context) {
     Timber.i("Requested initialization of main process tasks")
+    UserProfileStore(context).ensureCreated()
     SaveSyncWork.enqueueAutoWork(context, 0)
```

---

#### [MODIFY] [GameLaunchTaskHandler.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/shared/main/GameLaunchTaskHandler.kt)

After the existing `PlayTimeStore.add()`, record a `GameSession` in Room and update streaks. The constructor needs `RetrogradeDatabase` (already injected).

```diff
 class GameLaunchTaskHandler(
     private val reviewManager: ReviewManager,
     private val retrogradeDb: RetrogradeDatabase,
 ) {
     ...
     private suspend fun handleSuccessfulGameFinish(...) {
         val duration = data?.extras?.getLong(...)
         val game = data?.extras?.getSerializable(...) as Game

         updateGamePlayedTimestamp(game)
         PlayTimeStore(activity).add(game.id, duration)
+
+        // Record session & award XP
+        val profileStore = UserProfileStore(activity)
+        profileStore.recordSessionStreak()
+        val streak = profileStore.getCurrentStreak()
+        val baseXP = XPCalculator.baseXPFromSession(duration)
+        val multiplier = XPCalculator.streakMultiplier(streak)
+        val xpEarned = (baseXP * multiplier).toLong()
+
+        retrogradeDb.gameSessionDao().insert(
+            GameSession(
+                gameId = game.id,
+                durationMs = duration,
+                playedAt = System.currentTimeMillis(),
+                xpEarned = xpEarned,
+                streakDay = streak,
+            )
+        )

         if (enableRatingFlow) {
             displayReviewRequest(activity, duration)
         }
     }
```

---

### Component 7 — Navigation & Top Bar

#### [MODIFY] [MainNavigationRoutes.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/MainNavigationRoutes.kt)

Add `PROFILE` route:

```diff
     SETTINGS_DEVICE_PROFILE(...),
+    PROFILE(
+        route = "profile",
+        titleId = R.string.title_profile,
+        parent = HOME,
+        showBottomNavigation = false,
+    ),
     ;
```

---

#### [MODIFY] [MainTopBar.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/MainTopBar.kt)

Add a robot/avatar icon button in `OmnidroidTopBarActions()`:

```diff
     Row {
+        CompactBarIconButton(
+            onClick = { navController.navigate(MainRoute.PROFILE.route) },
+        ) {
+            Icon(
+                painter = painterResource(R.drawable.ic_profile_robot),
+                contentDescription = stringResource(R.string.title_profile),
+                modifier = Modifier.size(18.dp),
+            )
+        }
         CompactBarIconButton(
             onClick = onHelpPressed,
```

---

#### [MODIFY] [MainActivity.kt](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/main/MainActivity.kt)

Add `composable(MainRoute.PROFILE) { ProfileScreen(...) }` in the NavHost graph.

---

### Component 8 — Resources

#### [MODIFY] [strings.xml](file:///d:/Work/Omnidroid/omnidroid-app/src/main/res/values/strings.xml)

```xml
<!-- Profile -->
<string name="title_profile">Profile</string>
<string name="profile_default_tag">Pro Gamer</string>
<string name="profile_level">Level %d</string>
<string name="profile_xp_progress">%1$s / %2$s XP</string>
<string name="profile_total_playtime">Total Playtime</string>
<string name="profile_streak_current">Current Streak</string>
<string name="profile_streak_best">Best Streak</string>
<string name="profile_streak_multiplier">XP Bonus: ×%1$.1f</string>
<string name="profile_streak_days">%d days</string>
<string name="profile_consoles_unlocked">%1$d / %2$d Consoles Unlocked</string>
<string name="profile_change_picture">Change Picture</string>
<string name="profile_change_tag">Change Tag</string>
<string name="profile_badge_collector">Collector</string>
<string name="profile_badge_enthusiast">Enthusiast</string>
<string name="profile_badge_historian">Historian</string>
<string name="profile_badge_master">Omnidroid Master</string>
<string name="profile_achievements">Console Collection</string>
<string name="profile_recent_sessions">Recent Sessions</string>
<string name="profile_session_xp">+%d XP</string>
```

---

#### [NEW] `ic_profile_robot.xml` in `omnidroid-app/src/main/res/drawable/`

Vector drawable — a stylized robot head icon (circular face, antenna, rectangular eyes). Used as the default profile picture and the top-bar navigation icon.

---

## File Summary

| Action | File | Component |
|---|---|---|
| MODIFY | `retrograde-app-shared/.../db/RetrogradeDatabase.kt` | Room |
| NEW | `retrograde-app-shared/.../db/entity/GameSession.kt` | Room |
| NEW | `retrograde-app-shared/.../db/dao/GameSessionDao.kt` | Room |
| MODIFY | `retrograde-app-shared/.../db/dao/Migrations.kt` | Room |
| MODIFY | `omnidroid-app/.../OmnidroidApplicationModule.kt` | Room |
| NEW | `mobile/feature/profile/UserProfileStore.kt` | Data |
| NEW | `mobile/feature/profile/ConsoleAchievementsStore.kt` | Data |
| NEW | `mobile/feature/profile/XPCalculator.kt` | Data |
| NEW | `mobile/feature/profile/ProfileSyncHelper.kt` | Cloud |
| MODIFY | `retrograde-app-shared/.../savesync/CloudSaveFolder.kt` | Cloud |
| MODIFY | `retrograde-app-shared/.../storage/DirectoriesManager.kt` | Cloud |
| MODIFY | `omnidroid-app-ext-play/.../SaveSyncManagerImpl.kt` | Cloud |
| MODIFY | `omnidroid-app/.../savesync/SaveSyncWork.kt` | Cloud |
| MODIFY | `omnidroid-app/.../savesync/SaveBackupManager.kt` | Cloud |
| NEW | `mobile/feature/profile/ProfileScreen.kt` | UI |
| NEW | `mobile/feature/profile/ProfileViewModel.kt` | UI |
| MODIFY | `shared/startup/MainProcessInitializer.kt` | Hook |
| MODIFY | `shared/main/GameLaunchTaskHandler.kt` | Hook |
| MODIFY | `mobile/feature/main/MainNavigationRoutes.kt` | Nav |
| MODIFY | `mobile/feature/main/MainTopBar.kt` | Nav |
| MODIFY | `mobile/feature/main/MainActivity.kt` | Nav |
| MODIFY | `res/values/strings.xml` | Resources |
| NEW | `res/drawable/ic_profile_robot.xml` | Resources |

---

## Verification Plan

### Automated Tests

#### Unit tests for `XPCalculator`:
```bash
./gradlew :omnidroid-app:testDebugUnitTest --tests "*XPCalculatorTest*"
```
- Verify `levelFromXP()` at boundary values (0, 99, 100, 400, 10000).
- Verify `streakMultiplier()` capping at streak 7.
- Verify `baseXPFromSession()` minimum-1 for short sessions.
- Verify `progressInLevel()` returns 0.0 at level boundary.

#### Unit tests for `UserProfileStore` streak logic:
```bash
./gradlew :omnidroid-app:testDebugUnitTest --tests "*UserProfileStoreTest*"
```
- Same-day double play does not double-count streak.
- Consecutive-day play increments streak.
- Gap in days resets streak to 1.
- Best streak preserved across resets.
- `exportToJson()` / `importFromJson()` round-trip.

#### Room migration test:
```bash
./gradlew :retrograde-app-shared:testDebugUnitTest --tests "*MigrationTest*"
```
- Verify v11→v12 migration creates `game_sessions` table with correct schema.
- Verify foreign key cascade (deleting a Game deletes its sessions).

### Build verification:
```bash
./gradlew :omnidroid-app:assemblePlayDebug
```

### Manual Verification
- **First launch** — Profile auto-creates with tag "Pro Gamer" and robot icon.
- **Tap robot icon** in top bar → navigates to profile screen.
- **Edit tag** — Persists across app restart.
- **Change picture** — Pick from gallery, circular crop displays. Remove reverts to robot.
- **Play a game** for 2+ minutes → close → profile shows XP gain, session in recent list.
- **Play on consecutive days** → streak increments, multiplier updates.
- **Install a console** via Add Consoles → unlocked count increments, achievement grid updates.
- **Unlock 3 consoles** → "Collector" badge appears.
- **Enable cloud sync** → play a session → trigger manual sync → check that `profile/profile.json` appears in cloud storage.
- **Install on second device** → sync → verify profile tag, best streak, and session history arrive.
- **Export/import backup** → verify profile.json is included in the zip.
