package com.omnidroid.app.mobile.feature.profile

import android.content.Context
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Persistent store for lightweight profile metadata.
 *
 * Heavy data (total XP, total playtime, session history) is stored in Room via [GameSessionDao]
 * and consumed through flows. This store only holds what needs to survive independently of sessions:
 * display identity (tag, picture), streak counters, and profile creation timestamp.
 *
 * Uses the Harmony [FlowSharedPreferences] pattern identical to [PlayTimeStore] and
 * [RegisteredSystemsStore], and is safe to instantiate from any process.
 */
class UserProfileStore(context: Context) {

    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)
    private val flowPreferences = FlowSharedPreferences(preferences)

    // ──────────────────────────────────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Idempotent. Writes default values only if the profile has never been created.
     * Safe to call on every app launch from [MainProcessInitializer].
     */
    fun ensureCreated() {
        if (!preferences.contains(KEY_CREATED_AT)) {
            preferences.edit()
                .putLong(KEY_CREATED_AT, System.currentTimeMillis())
                .putString(KEY_TAG, DEFAULT_TAG)
                .putInt(KEY_CURRENT_STREAK, 0)
                .putInt(KEY_BEST_STREAK, 0)
                .putString(KEY_LAST_PLAY_DATE, "")
                .apply()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tag
    // ──────────────────────────────────────────────────────────────────────────

    fun getTag(): String = preferences.getString(KEY_TAG, DEFAULT_TAG) ?: DEFAULT_TAG

    fun setTag(tag: String) {
        preferences.edit().putString(KEY_TAG, tag.trim().take(MAX_TAG_LENGTH)).apply()
    }

    fun observeTag(): Flow<String> =
        flowPreferences.getString(KEY_TAG, DEFAULT_TAG).asFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Profile picture
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns null when the player has not set a custom picture (use the bundled robot drawable). */
    fun getProfilePicUri(): String? = preferences.getString(KEY_PIC_URI, null)

    fun setProfilePicUri(uri: String?) {
        preferences.edit().putString(KEY_PIC_URI, uri).apply()
    }

    fun observeProfilePicUri(): Flow<String?> =
        flowPreferences.getNullableString(KEY_PIC_URI, null).asFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Streak
    // ──────────────────────────────────────────────────────────────────────────

    fun getCurrentStreak(): Int = preferences.getInt(KEY_CURRENT_STREAK, 0)

    fun getBestStreak(): Int = preferences.getInt(KEY_BEST_STREAK, 0)

    fun observeCurrentStreak(): Flow<Int> =
        flowPreferences.getInt(KEY_CURRENT_STREAK, 0).asFlow()

    fun observeBestStreak(): Flow<Int> =
        flowPreferences.getInt(KEY_BEST_STREAK, 0).asFlow()

    /**
     * Records that a play session happened today. Updates streak counters using calendar-day
     * (midnight local time) boundaries:
     *
     * - Same day as last session → no-op (streak already counted for today).
     * - Yesterday → streak increments.
     * - Any earlier → streak resets to 1.
     */
    fun recordSessionStreak() {
        val today = todayDateString()
        val lastDate = preferences.getString(KEY_LAST_PLAY_DATE, "") ?: ""

        when {
            lastDate == today -> {
                // Already logged a session today — nothing to update.
                return
            }
            lastDate == yesterdayDateString() -> {
                val newStreak = preferences.getInt(KEY_CURRENT_STREAK, 0) + 1
                val bestStreak = preferences.getInt(KEY_BEST_STREAK, 0)
                preferences.edit()
                    .putInt(KEY_CURRENT_STREAK, newStreak)
                    .putInt(KEY_BEST_STREAK, maxOf(bestStreak, newStreak))
                    .putString(KEY_LAST_PLAY_DATE, today)
                    .apply()
            }
            else -> {
                // Gap in days → reset streak to 1
                preferences.edit()
                    .putInt(KEY_CURRENT_STREAK, 1)
                    .putString(KEY_LAST_PLAY_DATE, today)
                    .apply()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cloud sync serialization
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Serializes profile state to a JSON string for writing to profile/profile.json before cloud sync.
     * Intentionally omits profilePicUri — that is a device-local URI and would be invalid on other devices.
     */
    fun exportToJson(): String {
        return JSONObject()
            .put(JSON_TAG, getTag())
            .put(JSON_CREATED_AT, preferences.getLong(KEY_CREATED_AT, 0L))
            .put(JSON_CURRENT_STREAK, getCurrentStreak())
            .put(JSON_BEST_STREAK, getBestStreak())
            .put(JSON_LAST_PLAY_DATE, preferences.getString(KEY_LAST_PLAY_DATE, "") ?: "")
            .toString()
    }

    /**
     * Merges remote profile data after a cloud sync. Conflict resolution rules:
     * - [bestStreak]: take the highest across devices.
     * - [currentStreak] / [lastPlayDate]: take remote only if remote's lastPlayDate is more recent.
     * - [tag]: take remote if the remote JSON has a custom tag (non-empty, non-default) and
     *   the local tag is still the default. Otherwise keep local (user's explicit choice wins).
     * - [createdAt]: keep the earliest (preserve original account age).
     */
    fun importFromJson(json: String) {
        runCatching {
            val obj = JSONObject(json)
            val remoteTag = obj.optString(JSON_TAG, "")
            val remoteCreatedAt = obj.optLong(JSON_CREATED_AT, 0L)
            val remoteCurrentStreak = obj.optInt(JSON_CURRENT_STREAK, 0)
            val remoteBestStreak = obj.optInt(JSON_BEST_STREAK, 0)
            val remoteLastDate = obj.optString(JSON_LAST_PLAY_DATE, "")

            val localCreatedAt = preferences.getLong(KEY_CREATED_AT, Long.MAX_VALUE)
            val localTag = getTag()
            val localBestStreak = getBestStreak()
            val localLastDate = preferences.getString(KEY_LAST_PLAY_DATE, "") ?: ""

            preferences.edit().apply {
                // Preserve oldest creation date
                if (remoteCreatedAt > 0 && remoteCreatedAt < localCreatedAt) {
                    putLong(KEY_CREATED_AT, remoteCreatedAt)
                }
                // Remote tag wins if local is still the default
                if (remoteTag.isNotEmpty() && localTag == DEFAULT_TAG && remoteTag != DEFAULT_TAG) {
                    putString(KEY_TAG, remoteTag)
                }
                // Best streak is the max across devices
                putInt(KEY_BEST_STREAK, maxOf(localBestStreak, remoteBestStreak))
                // Take remote streak data if remote last-play date is more recent
                if (remoteLastDate > localLastDate) {
                    putInt(KEY_CURRENT_STREAK, remoteCurrentStreak)
                    putString(KEY_LAST_PLAY_DATE, remoteLastDate)
                }
                apply()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun todayDateString(): String =
        DATE_FORMAT.format(Date())

    private fun yesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return DATE_FORMAT.format(cal.time)
    }

    companion object {
        const val DEFAULT_TAG = "Pro Gamer"
        const val MAX_TAG_LENGTH = 32

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        private const val KEY_TAG = "profile_tag"
        private const val KEY_PIC_URI = "profile_pic_uri"
        private const val KEY_CREATED_AT = "profile_created_at"
        private const val KEY_CURRENT_STREAK = "profile_current_streak"
        private const val KEY_BEST_STREAK = "profile_best_streak"
        private const val KEY_LAST_PLAY_DATE = "profile_last_play_date"

        private const val JSON_TAG = "tag"
        private const val JSON_CREATED_AT = "createdAt"
        private const val JSON_CURRENT_STREAK = "currentStreak"
        private const val JSON_BEST_STREAK = "bestStreak"
        private const val JSON_LAST_PLAY_DATE = "lastPlayDate"
    }
}
