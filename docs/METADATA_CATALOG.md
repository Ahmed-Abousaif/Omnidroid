# Permanent metadata catalog and artwork cascade

## Resolution policy

ROM detection remains offline in `OmnidroidLibrary` and `LibretroDBMetadataProvider`.
CRC, serial, product-code and arcade evidence is recorded in `game_identities`.
The enrichment worker does not guess a console, change emulator selection, or
replace a user-pinned match.

Automatic artwork order is:

1. Preserve user-selected covers and manual matches.
2. Resolve installed local game metadata and attempt its libretro thumbnail.
3. Reuse a permanent device catalog association.
4. Consult the shared Omnidroid catalog.
5. Search registered remote providers only for unknown catalog identities.

Generated libretro URLs are not proof of availability. An actual image GET is
reused for display; HTTP 404/410 permits fallback. Offline errors, 429 and 5xx
defer work instead of causing a library-wide provider cascade. Indexed thumbnail
paths are retained. Embedded Switch icons have separate provenance from user art.
When the thumbnail host misses or fails for a libretro thumbnail, its exact path in
the official `libretro-thumbnails` GitHub repository is always attempted before IGDB.
IGDB fallback still proceeds only after a definitive 404/410 miss rather than offline,
429, or 5xx failures. This does not search or guess a different filename. Only libretro
image paths under that GitHub organization are permitted by the image policy.
Existing covers are conservatively treated as user artwork during upgrade.

`ArtworkEnrichmentWork` is independent connected WorkManager work. It pages the
library, resumes after its time budget, retries temporary failures, and defaults
to unmetered networks. Viewport prefetch prioritizes visible missing covers.
Online enrichment and its background network constraint are configurable in
Library settings. Explicit online cover search is never fired by typing.

## Permanent associations versus refreshable content

Accepted provider links have **no expiry** in Postgres or Room. A no-cover result
does not discard the game match. Details and repair requests use the saved
provider game ID, never another title search. Missing artwork gets a separate
retry date; request failures and ambiguous/no-match searches have bounded retry
periods. Correction history is append-only. `GET /api/metadata/changes` propagates
admin-reviewed corrections without expiring all device associations.

Local schema 19 adds `game_identities`, `catalog_lookups`, `game_artwork` and
`artwork_attempts`, plus `games.customCoverOrigin`. The 18→19 migration is additive.
User selections, names, favorites and play history are preserved. Artwork writes
are transactional and check the identity fingerprint, file facts and latest user
choices before applying a result. Rescanning retains a validated fallback until
a higher-priority image is actually available.

Titles are normalized without collapsing sequel numbers or meaningful editions.
Dump/region/disc tags are separated from game identity. Latin accents normalize
for lookup while Japanese voicing marks and other non-Latin distinctions remain.
The server computes its own correlation keys and accepts automatic associations
only for a unique exact title or a provider-confirmed alternative on a supported
platform. Ambiguous candidates are offered to the user, not published as global
truth. A user selection stays local. Strong ROM keys and filesystem paths are
not uploaded.

## Android configuration

Supply the deployed server's HTTPS origin at build time:

```powershell
$env:OMNIDROID_METADATA_BASE_URL = 'https://omnidroid.site'
.\gradlew.bat :omnidroid-app:assembleFreeDynamicRelease
```

The default origin is `https://omnidroid.site`. The equivalent override is
`-Pomnidroid.metadataBaseUrl=https://...`.
Only the public service address enters `BuildConfig`; provider secrets/tokens do
not. An unset origin keeps remote resolution disabled while offline scanning,
local metadata, libretro downloads and existing covers remain usable.

`CoverCacheStore` coordinates downloads by source URL. Coil, viewport prefetch,
worker and detail tier share its fetcher. Original validated image bytes are
reused to create grid/detail variants and to serve duplicate ROMs. Image reads
are bounded, processed files are published by same-directory rename, redirect following is
disabled, hosts are allowlisted, and source/processed files share a 300 MiB LRU.
Permanent catalog records are not part of that LRU.

## Server implementation

The companion changes live on the same-named branch in `Omnidroid-website`.
Postgres schema installation is invoked by the existing boot migration plugin.
Accepted aliases, canonical games, provider records and correction history are
durable application data. Back up the Postgres volume; do not configure a TTL or
an eviction job for those tables. Temporary lookup leases, rate buckets and API
concurrency slots are separate records.

Set private runtime environment variables:

```text
NUXT_IGDB_CLIENT_ID=<application client ID>
NUXT_IGDB_CLIENT_SECRET=<application secret>
```

The existing `NUXT_DATABASE_URL` and a persistent, random `NUXT_SESSION_SECRET`
are required. Application tokens are reused and encrypted in Postgres with a key
derived from the session secret. No authentication material is returned to the
app. `.env` is ignored; `.env.example` contains placeholders only.

The provider gate is shared across server instances: at most two new upstream
HTTP requests per second, four in-flight requests, retry-after and
per-identity leases. Read cached associations without consuming upstream quota.
Metrics separately count HTTP calls, logical queries, permanent hits and outcomes.
There is no daily request cap; legitimate new matches continue throughout the day.
IGDB multiquery is attempted once; an omitted/empty envelope switches the gate
to individual `/games` calls. It is never treated as a no-match response. To
reprobe a repaired multiquery endpoint, an operator may set
`catalog_provider_control.multiquery_supported = true` and
`multiquery_verified = false` for `igdb`. A database advisory lock permits only
one capability probe across instances. Lookup claims also check for accepted
matches under the same lock as acceptance, so a request with an older initial
read cannot repeat a search that another request has just completed.

## API and administration

`POST /api/metadata/resolve` accepts `{"identities":[...]}` with at most ten
identities. Each identity includes `key`, `system`, `title`, optional
`alternateTitle`, `region`, `details`, and `refreshAssets`. Keys are opaque client
correlation values, not authority to write shared associations. The response has
`schemaVersion: 1` and `results`; result states are `matched`, `not_found`,
`ambiguous`, `unsupported`, or `retry`. Retry times are Unix milliseconds.
`match` contains catalog/provider IDs, artwork candidates and field provenance.
The endpoint validates bounded JSON and does not expose raw APICalypse or URL
proxying. IGDB searches use a narrow expanded field set and platform filters.
The first necessary search also retrieves reusable text and image references,
so a later details view can use that permanent record without another API call.
Games fully served by local artwork only request IGDB metadata when details are opened.

- `POST /api/metadata/search`: explicit discovery, `{"identity":{...}}`; user
  selections are not submitted globally.
- `GET /api/metadata/changes?after=0`: paginated reviewed corrections. No provider
  calls; clients keep a revision cursor.
- `GET /api/metadata/catalog?system=snes&offset=0`: paginated public catalog export
  for offline metadata-slice tools. No provider calls.
- `GET /api/admin/catalog?system=snes&offset=0`: authenticated records/metrics.
  The response also includes unresolved lookups and their candidate evidence.
- `POST /api/admin/catalog`: authenticated correction to an existing compatible
  catalog record, with `lookupKey`, `gameId`, `provider`, and a mandatory `reason`.
  The previous association is retained in history.

To approve an unresolved alias, first resolve the desired provider-confirmed title
through the structured metadata endpoint to obtain its canonical catalog ID, then
review the pending lookup against that compatible record through the admin POST.
That approval is added to the correction feed. Public alternate-title input alone
cannot establish an unrelated permanent alias.

No bulk crawl or full-catalog refresh runs on a user's scan. Provider data is
attributed in About, online cover search and populated game details. Production
use must satisfy the provider's applicable partnership/attribution terms.

### Shipping permanent matches in existing metadata slices

Run the existing `libretro-db-builder` with `OMNIDROID_CATALOG_ORIGIN=https://omnidroid.site`
or its optional `--catalog-origin` argument. It downloads only the public accepted
catalog export, embeds mappings in an optional `catalog_seeds` table inside each
system's SQLite payload, then uses the existing gzip/checksum/manifest pipeline.
It never queries a provider. Without that option, builds remain completely
independent of the server and include an empty seeds table.

The metadata database's 14→15 migration adds a seed table without removing
installed game databases. The slice installer accepts both legacy slices with
no seed table and new seeded slices. Enrichment imports a verified seed into the
permanent device catalog before considering any service request. No ROM data is
uploaded and no extra sidecar download or unsigned manifest is introduced.

## Adding providers

Implement `CatalogProvider` (`supports`, `resolve`, `details`) in the server's
`server/utils/catalog` directory, map supported platforms explicitly, and register
the adapter in the ordered `providers` list. Return standard artwork kinds and
provider IDs, retain provenance, distinguish retryable failure from no match,
and respect that provider's authentication and sharing rules. Unsupported fields
remain missing for subsequent providers. Do not add provider branches to ROM
detection or Compose.

The Android image-host policy must explicitly permit any new provider CDN before
that provider's URLs are rendered. Reuse `GameArtwork` transactional selection and
the coordinated downloader. A lower-priority provider fills missing artwork and
metadata; it does not replace a working higher-priority or user-selected cover.

## Verification

Affected JVM suites cover title identity distinctions, additive Room upgrade,
permanent association retention, stale-result rejection, preservation of manual
choices, scan/fallback precedence, one source GET across duplicate ROMs and
grid/detail tiers, and persistent typed download failures.

```powershell
.\gradlew.bat :retrograde-app-shared:testDebugUnitTest :omnidroid-metadata-libretro-db:testDebugUnitTest :omnidroid-app:testFreeDynamicDebugUnitTest --offline -PabiFilters=arm64-v8a
```

The website's `scripts/test-metadata-cycle.mjs` is an opt-in live integration
suite, authorized for this implementation. Run a separate local Postgres database
named `catalog_test` on port 55432 and start Nuxt on port 3300 with
`NUXT_DATABASE_URL` pointing to that database. The suite refuses nonlocal origins
or database names outside its test namespace:

```powershell
node scripts/test-metadata-cycle.mjs --reset
# Restart the Nuxt process against the same database, then:
node scripts/test-metadata-cycle.mjs --restart-check
```

It covers boot migrations, live IGDB requests, saved matches, duplicate and
concurrent requests, negative/ambiguous results, validation, rate limiting,
admin correction history/feed, process restarts and encrypted token reuse.
Reports are saved under the ignored website `.test-output` directory. `--reset`
only clears the isolated test catalog tables; never point this at production.

`MetadataEndToEndTest` is an opt-in Android instrumentation test. It scans
generated synthetic `.sfc` files, uses CRC fixtures in the real local metadata
SQLite database, downloads libretro and IGDB artwork, calls the actual local
HTTP endpoints, decodes the cover through Coil, reopens Room and verifies
offline reuse plus manual-match preservation. It does not include ROM payloads.
After building/installing the debug and Android-test APKs on an emulator:

```powershell
adb reverse tcp:3300 tcp:3300
adb shell am instrument -w -e class com.omnidroid.app.testing.MetadataEndToEndTest -e catalogOrigin http://127.0.0.1:3300 com.omnidroid.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The injected test transport and loopback cleartext allowance are test/debug
facilities; the release service URL remains `https://omnidroid.site`. The static
server check is `pnpm exec tsc --noEmit -p .nuxt/tsconfig.server.json`.

### Validated locally on 2026-11-24

- Production Nuxt/Nitro build and server TypeScript check passed. Both the
  development server and the production build ran against a dedicated Postgres
  16 container; the production homepage and privacy page returned HTTP 200.
- Eleven live server checks and three restart/token checks passed. They used
  real IGDB responses and inspected the actual Postgres records/counters.
- The Android instrumentation cycle passed on an Android 10 / API 29 emulator.
  A cold run with the shared catalog and app artwork cache empty needed exactly
  one IGDB HTTP request for the missing-cover identity. Two duplicate ROMs shared
  that match; details and offline reuse required no additional service request.
- All 366 affected JVM tests passed with no skips. Free and Play Kotlin builds
  passed; the Free debug and instrumentation APKs were built and installed.

The emulator's normal DNS path was unavailable on this host. A temporary
loopback CONNECT tunnel restricted to the artwork hosts supplied host networking;
HTTPS remained end to end and no trust checks were disabled. The test used
synthetic cartridge files with known local metadata fixtures, rather than a
user's complete ROM collection. This validation does not deploy production or
claim coverage of every console, game, ROM variant or network condition.
