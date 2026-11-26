package com.omnidroid.app.shared.metadata

import android.content.Context
import com.omnidroid.app.shared.covers.CoverCacheStore
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.CatalogLookup
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameArtwork
import com.omnidroid.lib.library.db.entity.GameIdentity
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.metadata.catalog.CatalogGame
import com.omnidroid.lib.library.metadata.catalog.CatalogIdentity
import com.omnidroid.lib.library.metadata.catalog.CatalogKeys
import com.omnidroid.lib.library.metadata.catalog.CatalogResolution
import com.omnidroid.metadata.libretrodb.CoverSearchResult
import com.omnidroid.metadata.libretrodb.CoverSearcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataRepository
    internal constructor(
        private val context: Context,
        private val database: RetrogradeDatabase,
        private val local: GameMetadataProvider,
        private val client: CatalogClient,
        private val settings: MetadataSettings,
        private val cacheProvider: () -> CoverCacheStore,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
            database: RetrogradeDatabase,
            local: GameMetadataProvider,
            client: CatalogClient,
            settings: MetadataSettings,
        ) : this(context, database, local, client, settings, { CoverUtils.coverCache(context) })

        private val dao get() = database.catalogDao()
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
        private val cache: CoverCacheStore get() = cacheProvider().also { it.attachAttempts(dao) }

        suspend fun syncCorrections() {
            if (!settings.enabled || !settings.configured) return
            try {
                repeat(5) {
                    val changes = client.changes(settings.catalogRevision)
                    for (change in changes.changes) {
                        // Retain corrections even before a ROM is imported, so an older offline
                        // seed cannot later resurrect the superseded provider association.
                        dao.putLookup(
                            CatalogLookup(
                                change.key,
                                "matched",
                                client.json.encodeToString(change),
                                0,
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    settings.catalogRevision = changes.revision
                    if (!changes.hasMore) return
                }
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                // Permanent local matches remain valid without the service.
            }
        }

        suspend fun identity(game: Game): GameIdentity {
            val existing = dao.identity(game.id)
            existing
                ?.takeIf {
                    it.system == game.systemId &&
                        (
                            !game.isManualMatch ||
                                CatalogKeys.normalize(it.title) == CatalogKeys.normalize(game.title)
                        )
                }?.let { return it }
            // Existing libraries are backfilled without opening ROMs or forcing a deep scan.
            val described =
                try {
                    if (game.isManualMatch) {
                        null
                    } else {
                        local.describe(
                            game.systemId,
                            game.title,
                            null,
                            game.fileSize ?: 0,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            val title = CatalogKeys.cleanTitle(described?.name ?: game.title)
            val value =
                GameIdentity(
                    gameId = game.id,
                    identityKey = CatalogKeys.fingerprint(game.systemId, title, null, null),
                    lookupKey = CatalogKeys.lookup(game.systemId, title),
                    system = game.systemId,
                    title = title,
                    alternateTitle =
                        if (game.isManualMatch) {
                            null
                        } else {
                            CatalogKeys.cleanTitle(game.fileName.substringBeforeLast('.')).takeIf {
                                CatalogKeys.normalize(it) !=
                                    CatalogKeys.normalize(title)
                            }
                        },
                    region = CatalogKeys.region(described?.rawTitle ?: game.fileName),
                    keyType = null,
                    keyValue = null,
                    matchMethod = if (game.isManualMatch) "manual" else described?.matchMethod ?: "filename",
                    libretroUrl =
                        described?.thumbnail
                            ?: game.coverFrontUrl?.takeIf { CoverSearcher.libretroMirrorUrl(it) != null }
                            ?: CoverSearcher.computeCoverUrl(
                                GameSystem.findById(game.systemId),
                                title,
                            ),
                    thumbnailIndexed = described?.thumbnailIndexed ?: false,
                    updatedAt = System.currentTimeMillis(),
                    fileSize = game.fileSize,
                    fileLastModified = game.fileLastModified,
                )
            if (existing == null) dao.backfillIdentity(value) else dao.putIdentity(value)
            return dao.identity(game.id) ?: value
        }

        private fun decode(lookup: CatalogLookup?): CatalogResolution? =
            lookup?.let {
                runCatching { client.json.decodeFromString<CatalogResolution>(it.payload) }.getOrNull()
            }

        private suspend fun lookup(identity: GameIdentity): CatalogLookup? {
            dao.lookup(identity.lookupKey)?.let { return it }
            val payload = local.catalogSeed(identity.system, identity.lookupKey) ?: return null
            val seed =
                runCatching { client.json.decodeFromString<CatalogResolution>(payload) }.getOrNull() ?: return null
            val match = seed.match ?: return null
            if (seed.state != "matched") return null
            val value =
                CatalogLookup(
                    identity.lookupKey,
                    "matched",
                    client.json.encodeToString(seed.copy(key = identity.lookupKey)),
                    0,
                    match.fetchedAt,
                )
            dao.putLookup(value)
            return value
        }

        /** Local artwork is always attempted before an online catalog lookup. */
        suspend fun enrich(games: List<Game>): Boolean {
            if (!settings.enabled) return false
            var retry = false
            val pending = ArrayList<Pair<Game, GameIdentity>>()
            for (snapshot in games) {
                val game = database.gameDao().selectById(snapshot.id) ?: continue
                if (!game.isAvailable ||
                    game.isManualMatch ||
                    (game.customCoverPath != null && game.customCoverOrigin != "embedded")
                ) {
                    continue
                }
                val identity = identity(game)
                val localUrl = identity.libretroUrl
                if (localUrl != null) {
                    when (resolveLibretroArtwork(game, identity, localUrl)) {
                        LibretroOutcome.APPLIED -> continue
                        LibretroOutcome.DEFERRED -> {
                            retry = true
                            continue
                        }
                        LibretroOutcome.MISSING -> Unit
                    }
                }
                val cached = lookup(identity)
                val result = decode(cached)
                if (result?.state == "matched") {
                    result.match?.let { apply(game, identity, it) }
                } else if (cached == null || cached.retryAt <= System.currentTimeMillis()) {
                    pending += game to identity
                } else if (cached.state == "retry") {
                    retry = true
                }
            }
            for (chunk in pending.chunked(10)) {
                if (!settings.configured) break
                // One device batch lock also coalesces background/visible/detail lookups.
                locks.getOrPut("batch") { Mutex() }.withLock {
                    val due =
                        chunk.filter { (_, id) ->
                            val row = dao.lookup(id.lookupKey)
                            row == null || (row.state != "matched" && row.retryAt <= System.currentTimeMillis())
                        }
                    if (due.isEmpty()) return@withLock
                    val identities = due.map { it.second.request() }.distinctBy { it.key }
                    val results = fetch(identities)
                    if (results.any { it.state == "retry" }) retry = true
                    for ((game, identity) in due) {
                        results.find { it.key == identity.lookupKey }?.match?.let {
                            apply(
                                game,
                                identity,
                                it,
                            )
                        }
                    }
                }
            }
            return retry
        }

        private enum class LibretroOutcome { APPLIED, DEFERRED, MISSING }

        private suspend fun resolveLibretroArtwork(
            game: Game,
            identity: GameIdentity,
            localUrl: String,
        ): LibretroOutcome {
            val resolved = CoverUtils.ensureWithMirror(cache, game.id, localUrl, CoverCacheStore.Tier.GRID)
            if (resolved != null) {
                val (source, _) = resolved
                val artwork = dao.artwork(game.id)
                if (game.coverFrontUrl != source ||
                    artwork?.source != "libretro" ||
                    artwork.sourceUrl != source ||
                    artwork.identityKey != identity.identityKey
                ) {
                    dao.applyArtwork(GameArtwork(game.id, identity.identityKey, "libretro", source))
                }
                return LibretroOutcome.APPLIED
            }
            // Always try the official GitHub mirror first; only a definitive miss there (or on the
            // primary URL when no mirror applies) permits falling back to IGDB.
            val terminalSource = CoverSearcher.libretroSources(localUrl).last()
            return if (cache.failure(terminalSource)?.state == "missing") {
                LibretroOutcome.MISSING
            } else {
                LibretroOutcome.DEFERRED
            }
        }

        private suspend fun fetch(identities: List<CatalogIdentity>): List<CatalogResolution> {
            val results =
                try {
                    client.resolve(identities).results
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (error: Exception) {
                    val retryAt = (error as? CatalogUnavailable)?.retryAt ?: System.currentTimeMillis() + 60_000
                    Timber.d("Metadata lookup deferred")
                    identities.map { CatalogResolution(it.key, "retry", retryAt = retryAt) }
                }
            for (identity in identities) {
                val result =
                    results.find { it.key == identity.key }
                        ?: CatalogResolution(identity.key, "retry", retryAt = System.currentTimeMillis() + 60_000)
                val previous = decode(dao.lookup(identity.key))
                // A transient details refresh cannot erase a permanent provider association.
                val retained =
                    if (result.state != "matched" &&
                        previous?.state == "matched"
                    ) {
                        previous.copy(retryAt = result.retryAt)
                    } else {
                        result
                    }
                val retryAt =
                    retained.retryAt
                        ?: if (retained.state == "matched") 0 else System.currentTimeMillis() + 14 * 86_400_000L
                val assetRetry =
                    if (retained.state == "matched" &&
                        retained.match?.assets?.none { it.kind == "cover" } == true
                    ) {
                        System.currentTimeMillis() +
                            14 * 86_400_000L
                    } else {
                        0
                    }
                dao.putLookup(
                    CatalogLookup(
                        identity.key,
                        retained.state,
                        client.json.encodeToString(retained),
                        retryAt,
                        System.currentTimeMillis(),
                        assetRetry,
                    ),
                )
            }
            return results
        }

        private suspend fun apply(
            game: Game,
            identity: GameIdentity,
            match: CatalogGame,
        ) {
            val artwork = match.artwork("cover", identity.region)
            if (artwork == null) {
                refreshArtwork(game, identity, match)
                return
            }
            if (!CoverUtils.isAllowedRemoteCoverUrl(artwork.url)) return
            if (cache.ensureGrid(game.id, artwork.url) == null) {
                if (cache.failure(artwork.url)?.state == "missing") refreshArtwork(game, identity, match)
                return
            }
            dao.applyArtwork(
                GameArtwork(game.id, identity.identityKey, artwork.provider ?: match.provider, artwork.url),
            )
            match.developer?.takeIf { it.isNotBlank() }?.let { dao.fillDeveloper(game.id, identity.identityKey, it) }
        }

        private suspend fun refreshArtwork(
            game: Game,
            identity: GameIdentity,
            match: CatalogGame,
        ) {
            val row = dao.lookup(identity.lookupKey) ?: return
            val now = System.currentTimeMillis()
            if (match.fetchedAt >= now - 86_400_000 || row.retryAt > now || row.assetRetryAt > now) return
            locks.getOrPut("asset:${identity.lookupKey}") { Mutex() }.withLock {
                val current = dao.lookup(identity.lookupKey) ?: return@withLock
                if (current.assetRetryAt > System.currentTimeMillis()) return@withLock
                // A failed/unchanged asset refresh does not start a loop across duplicate ROMs.
                dao.putLookup(current.copy(assetRetryAt = now + 7 * 86_400_000L))
                val refreshed =
                    fetch(listOf(identity.request().copy(refreshAssets = true))).firstOrNull()?.match ?: return@withLock
                val replacement = refreshed.artwork("cover", identity.region)
                val previous = match.artwork("cover", identity.region)
                val updated = dao.lookup(identity.lookupKey)
                if (updated != null) dao.putLookup(updated.copy(assetRetryAt = now + 7 * 86_400_000L))
                if (replacement != null && replacement.url != previous?.url) apply(game, identity, refreshed)
            }
        }

        suspend fun details(game: Game): CatalogGame? {
            val id = identity(game)
            return locks.getOrPut("batch") { Mutex() }.withLock {
                val cached = lookup(id)
                val stored = decode(cached)?.match
                if (stored?.detailsComplete == true ||
                    (cached != null && cached.retryAt > System.currentTimeMillis())
                ) {
                    return@withLock stored
                }
                fetch(listOf(id.request(details = true)))
                decode(dao.lookup(id.lookupKey))?.match?.also { match ->
                    match.developer?.takeIf { it.isNotBlank() }?.let { dao.fillDeveloper(game.id, id.identityKey, it) }
                }
            }
        }

        /** Invoked explicitly, never on every keystroke. A selected candidate remains user-local. */
        suspend fun search(
            game: Game,
            query: String,
        ): List<CoverSearchResult> {
            val id = identity(game)
            val request =
                id.request().copy(
                    key = CatalogKeys.lookup(game.systemId, query),
                    title = CatalogKeys.cleanTitle(query),
                    alternateTitle = null,
                )
            val existing = dao.lookup(request.key)
            val resolution =
                if (existing != null &&
                    (existing.state == "matched" || existing.retryAt > System.currentTimeMillis())
                ) {
                    decode(existing)
                } else {
                    val result = client.search(request).results.firstOrNull()
                    if (result !=
                        null
                    ) {
                        dao.putLookup(
                            CatalogLookup(
                                request.key,
                                result.state,
                                client.json.encodeToString(result),
                                result.retryAt ?: if (result.state ==
                                    "matched"
                                ) {
                                    0
                                } else {
                                    System.currentTimeMillis() + 7 * 86_400_000L
                                },
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    result
                }
            return (listOfNotNull(resolution?.match) + resolution?.candidates.orEmpty())
                .mapNotNull { record ->
                    record.artwork("cover", id.region)?.let {
                        CoverSearchResult(
                            record.title,
                            it.url,
                            if (resolution?.state ==
                                "matched"
                            ) {
                                100
                            } else {
                                0
                            },
                        )
                    }
                }.filter { CoverUtils.isAllowedRemoteCoverUrl(it.coverUrl) }
                .distinctBy { it.coverUrl }
        }

        companion object {
            fun get(context: Context): MetadataRepository =
                EntryPointAccessors
                    .fromApplication(
                        context.applicationContext,
                        MetadataEntryPoint::class.java,
                    ).metadataRepository()
        }
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MetadataEntryPoint {
    fun metadataRepository(): MetadataRepository
}
