# Omnidroid Dependency Upgrade — minSdk 23 Preserved

This plan upgrades every stale dependency in the project across 6 sequenced phases. Each phase is designed to be independently compilable and testable before moving to the next. The constraint — **minSdk stays at 23** — is validated at every bump.

## User Review Required

> [!IMPORTANT]
> **Retrofit 2.9.0 → 3.0.0**: Retrofit 3.0 was rewritten in Kotlin and depends on OkHttp 4.12+. It maintains binary compatibility with 2.x converters, but you're currently using a **custom `Converter.Factory`** in [`OmnidroidApplicationModule.kt`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/swordfish/omnidroid/app/OmnidroidApplicationModule.kt#L208-L229). This converter is simple (`ZipInputStream` / `InputStream` passthrough) and should work fine, but I want to confirm you're comfortable jumping to 3.x rather than doing a more conservative 2.11 bump.

> [!IMPORTANT]
> **OkHttp 4.9.1 → 5.x**: OkHttp 5.x is stable (5.5.0). The jump from 4.x to 5.x is a major version bump. The OkHttp 5 API surface is backward-compatible for normal usage (builder patterns, interceptors), but internal changes around connection pooling and TLS are significant. If you'd rather stay on the 4.x line (latest 4.12.x), the networking improvements are still meaningful. Please confirm preference: **OkHttp 5.5.0** or **OkHttp 4.12.x**?

> [!WARNING]
> **Jetifier**: Your `gradle.properties` has `android.enableJetifier=true`. After removing the pre-AndroidX `android.arch.lifecycle:reactivestreams` dependency and bumping all libraries to AndroidX-native versions, the Jetifier becomes unnecessary and can be disabled. However, if any **transitive** dependency from JitPack or `LibretroDroid` still pulls in a support-library artifact, disabling Jetifier could cause a build break. I'll disable it at the end and verify. If it fails, I'll re-enable it.

> [!IMPORTANT]
> **Navigation 2.5.2 → 2.10.1**: This is a significant jump. Navigation 2.7+ introduced type-safe routes and `@Serializable` support, and 2.8+ changed how `NavHost` handles back stack. Your app currently uses Safe Args plugin (`androidx.navigation.safeargs.kotlin`). The Safe Args plugin **is still supported** in 2.10, so existing usage won't break — but you should be aware this is a big behavioral leap. I'll keep Safe Args in place. Confirm you're OK with this jump.

## Open Questions

> [!IMPORTANT]
> **Accompanist deprecations**: Several Accompanist libraries you use are now deprecated:
> - `accompanist-systemuicontroller` → replaced by `enableEdgeToEdge()` in Activity 1.8+
> - `accompanist-navigation-material` → replaced by `material3:ModalBottomSheet` in Compose Material3
> 
> Should I **remove these Accompanist dependencies and migrate** to the platform equivalents in this pass? Or leave them pinned at a compatible version for now and handle the migration separately?

---

## Phase 1 — GMS Pin + Play Services Auth Bump (Critical)

**Why first**: The global GMS pin to `17.0.0` is the highest-risk item. It forces *all* `com.google.android.gms` artifacts to a 2019 version, creating runtime conflicts with Feature Delivery 2.1 and modern auth APIs. This is a potential crash source for real users on the Play dynamic-feature path.

### Changes

#### [MODIFY] [`deps.kt`](file:///d:/Work/Omnidroid/buildSrc/src/main/java/deps.kt)
| Dependency | Current | Target | minSdk Impact |
|---|---|---|---|
| `versions.gms` | `17.0.0` | `21.6.0` | None (API 21+) |
| `libs.play.playServices` | `17.0.0` (hardcoded) | `21.6.0` (use `${versions.gms}`) | None |
| `libs.play.review` | `2.0.0` | `2.0.2` | None |
| `libs.play.reviewKtx` | `2.0.0` | `2.0.2` | None |
| `libs.play.coroutine` | `1.6.4` | `1.10.2` | None |

#### [MODIFY] [`build.gradle.kts`](file:///d:/Work/Omnidroid/build.gradle.kts) (root)
- The `resolutionStrategy.eachDependency` block forces all `com.google.android.gms` to `deps.versions.gms`. After bumping the version, this still works correctly — it just forces to `21.6.0` instead of `17.0.0`.

### Verification
- Build the `playDynamic` variant
- Verify dynamic feature delivery still works (install + download a core)
- Verify Google sign-in flow in the Play variant

---

## Phase 2 — Remove Pre-AndroidX Lifecycle + Lifecycle Bump

**Why second**: The `android.arch.lifecycle:reactivestreams` dependency is pre-AndroidX. It's being pulled in but **never imported** in any source file — it's dead weight that risks double-observer bugs. Removing it is safe and unblocks the Lifecycle version bump.

### Changes

#### [MODIFY] [`deps.kt`](file:///d:/Work/Omnidroid/buildSrc/src/main/java/deps.kt)
| Dependency | Current | Target | minSdk Impact |
|---|---|---|---|
| `versions.lifecycle` | `2.6.1` | `2.9.0` | None (2.9.x still minSdk 21; 2.10+ requires 23 which is fine too, but 2.9.0 is the safe conservative pick) |
| `libs.lifecycle.reactiveStreams` | `android.arch.lifecycle:reactivestreams:1.1.1` | **DELETE** | N/A |
| `libs.lifecycle.viewModelCompose` | `2.5.1` (hardcoded) | Use `${versions.lifecycle}` | None |
| `libs.lifecycle.processor` | `${versions.lifecycle}` (kapt) | Keep at `${versions.lifecycle}` (will be removed in Phase 4 KSP migration) | None |

#### [MODIFY] [`omnidroid-app/build.gradle.kts`](file:///d:/Work/Omnidroid/omnidroid-app/build.gradle.kts)
- Remove `implementation(deps.libs.androidx.lifecycle.reactiveStreams)` (line 156)

#### [MODIFY] [`proguard-rules.pro`](file:///d:/Work/Omnidroid/omnidroid-app/proguard-rules.pro)
- Remove/update rules referencing `android.arch.lifecycle` (lines 9, 20)

### Verification
- Full build (all variants)
- Grep for any remaining `android.arch` references

---

## Phase 3 — OkHttp / Okio / Retrofit Bump

**Why third**: These are the networking stack underpinning cloud sync and core downloads. Bumping them is independent of DI changes and benefits from the GMS fix in Phase 1 (they talk to the same Google endpoints).

### Changes

#### [MODIFY] [`deps.kt`](file:///d:/Work/Omnidroid/buildSrc/src/main/java/deps.kt)
| Dependency | Current | Target | minSdk Impact |
|---|---|---|---|
| `versions.okHttp` | `4.9.1` | `4.12.0` (or `5.5.0` — awaiting your preference) | None (API 21+) |
| `libs.okio` | `2.10.0` | `3.18.2` | None |
| `versions.retrofit` | `2.9.0` | `3.0.0` (or `2.11.0` — awaiting your preference) | None (API 21+) |

#### [MODIFY] [`proguard-rules.pro`](file:///d:/Work/Omnidroid/omnidroid-app/proguard-rules.pro)
- Update OkHttp/Retrofit proguard rules for the new versions
- OkHttp 5.x ships its own consumer proguard rules; many existing suppressions become unnecessary

### Verification
- Build all variants
- Test core download flow
- Test cloud save sync (if using Google Drive/Dropbox)

---

## Phase 4 — Dagger → Hilt Migration + kapt → KSP

This is the largest single phase. The current app uses Dagger Android 2.19 with a hand-rolled `@Component`, `DaggerApplication`, `AndroidInjection.inject(this)`, and custom Worker injection. Hilt replaces all of this with `@HiltAndroidApp`, `@AndroidEntryPoint`, and `@HiltWorker`.

### 4a — Add KSP plugin + Hilt plugin to the project

#### [MODIFY] [`build.gradle.kts`](file:///d:/Work/Omnidroid/build.gradle.kts) (root)
- Add KSP plugin: `id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false`
- Add Hilt plugin: `id("com.google.dagger.hilt.android") version "2.60.1" apply false`

#### [MODIFY] [`deps.kt`](file:///d:/Work/Omnidroid/buildSrc/src/main/java/deps.kt)
| Dependency | Current | Target |
|---|---|---|
| `versions.dagger` | `2.19` | `2.60.1` |
| `libs.dagger.*` | Dagger + dagger-android artifacts | Hilt artifacts (`hilt-android`, `hilt-compiler`) |

### 4b — Migrate each module from kapt to KSP

Modules using `kapt` and their processors:

| Module | kapt processors | Action |
|---|---|---|
| `omnidroid-app` | `dagger-compiler`, `dagger-android-processor`, `lifecycle-compiler` | Switch to `ksp(hilt-compiler)`, remove lifecycle-compiler (use `DefaultLifecycleObserver` instead of `@OnLifecycleEvent`) |
| `retrograde-app-shared` | `room-compiler` | Switch to `ksp(room-compiler)` |
| `omnidroid-metadata-libretro-db` | `room-compiler`, `dagger-compiler` | Switch to `ksp(room-compiler)`, `ksp(hilt-compiler)` |
| `omnidroid-touchinput` | `lifecycle-compiler` | Remove kapt entirely (migrate to `DefaultLifecycleObserver`) |
| `omnidroid-app-ext-play` | `lifecycle-compiler` | Remove kapt entirely (migrate to `DefaultLifecycleObserver`) |
| `omnidroid-app-ext-free` | No kapt usages | Remove `kotlin-kapt` plugin |
| `retrograde-util` | No kapt usages | Remove `kotlin-kapt` plugin |
| `omnidroid-cores/*` (19 modules) | No kapt usages | Remove `kotlin-kapt` plugin |

### 4c — Migrate DI patterns

**Files to change** (~32 files touched by Dagger imports):

1. **Application class** — [`OmnidroidApplication.kt`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/swordfish/omnidroid/app/OmnidroidApplication.kt)
   - `DaggerApplication` → plain `Application` with `@HiltAndroidApp`
   - Remove `applicationInjector()`, `workerInjector()`
   - Remove `HasWorkerInjector` implementation

2. **Component** — [`OmnidroidApplicationComponent.kt`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/swordfish/omnidroid/app/OmnidroidApplicationComponent.kt)
   - **DELETE** this file entirely. Hilt generates the component.

3. **Application Module** — [`OmnidroidApplicationModule.kt`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/swordfish/omnidroid/app/OmnidroidApplicationModule.kt)
   - Remove all `@ContributesAndroidInjector` methods (Activities get `@AndroidEntryPoint` instead)
   - Convert `@PerApp` scoped `@Provides` to `@Singleton` scoped `@Provides` inside `@Module @InstallIn(SingletonComponent::class)`
   - Remove `@JvmStatic` annotations (Hilt modules can be `object` instead of `companion object`)
   - Split `@Binds` methods into a separate abstract module if needed

4. **TV Module** — [`OmnidroidTVApplicationModule.kt`](file:///d:/Work/Omnidroid/omnidroid-app/src/main/java/com/swordfish/omnidroid/app/tv/OmnidroidTVApplicationModule.kt)
   - Remove `@ContributesAndroidInjector`; Activities get `@AndroidEntryPoint`

5. **Base Activities** (3 files in `retrograde-app-shared`):
   - [`RetrogradeActivity.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/android/RetrogradeActivity.kt) — Remove `AndroidInjection.inject(this)`, remove `HasFragmentInjector`/`HasSupportFragmentInjector`, remove injector fields
   - [`RetrogradeAppCompatActivity.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/android/RetrogradeAppCompatActivity.kt) — Same treatment
   - [`RetrogradeComponentActivity.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/android/RetrogradeComponentActivity.kt) — Same treatment

6. **Worker injection** (custom `AndroidWorkerInjection` system → `@HiltWorker`):
   - **DELETE** [`AndroidWorkerInjection.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/AndroidWorkerInjection.kt)
   - **DELETE** [`AndroidWorkerInjectionModule.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/AndroidWorkerInjectionModule.kt)
   - **DELETE** [`HasWorkerInjector.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/HasWorkerInjector.kt)
   - **DELETE** [`WorkerKey.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/WorkerKey.kt)
   - Convert all Workers (`LibraryIndexWork`, `SaveSyncWork`, `SaveBackupWork`, `ChannelUpdateWork`, `CoreUpdateWork`, `CacheCleanerWork`) to use `@HiltWorker` + `@AssistedInject`

7. **Custom scopes** — **DELETE** or repurpose:
   - [`PerApp.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/PerApp.kt) → replaced by `@Singleton`
   - [`PerActivity.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/PerActivity.kt) → replaced by `@ActivityScoped`
   - [`PerFragment.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/PerFragment.kt) → replaced by `@FragmentScoped`
   - [`PerChildFragment.kt`](file:///d:/Work/Omnidroid/retrograde-app-shared/src/main/java/com/swordfish/omnidroid/lib/injection/PerChildFragment.kt) → delete (unused or replaceable)

8. **All Activities/Fragments** that have `@Inject` fields (~20 files):
   - Add `@AndroidEntryPoint` annotation
   - Remove manual `AndroidInjection.inject(this)` calls (Hilt does this automatically)
   - Keep `@Inject lateinit var` field injection patterns (they work identically in Hilt)

### Verification
- Full clean build (all variants)
- Run the app — verify DI graph is complete
- Test Worker execution (library index, save sync, cache clean)
- Test dynamic feature delivery flow

---

## Phase 5 — Compose BOM / Navigation / AndroidX Bumps

### Changes

#### [MODIFY] [`deps.kt`](file:///d:/Work/Omnidroid/buildSrc/src/main/java/deps.kt)
| Dependency | Current | Target | minSdk Impact |
|---|---|---|---|
| `versions.composeBom` | `2024.02.02` | `2026.08.00` | None |
| `versions.navigation` | `2.5.2` | `2.8.8` | None (staying on 2.x line; 2.10.1 is also fine) |
| `versions.kotlinExtension` | `1.4.6` | **DELETE** (Compose Compiler is now managed by `org.jetbrains.kotlin.plugin.compose` plugin since Kotlin 2.0 — you already have this plugin applied!) |
| `versions.accompanist` | `0.34.0` | `0.36.0` (latest compatible with 2026 BOM) or **remove** deprecated modules |
| `versions.paging` | `3.2.1` | `3.3.6` | None |
| `versions.room` | `2.6.1` | `2.8.4` | None |
| `versions.work` | `2.9.0` | `2.10.0` | None |
| `versions.fragment` | `1.5.1` | `1.8.6` | None |
| `versions.activity` | `1.7.2` | `1.10.1` | None |
| `versions.serialization` | `1.2.2` | `1.8.1` | None |
| `libs.kotlinxCoroutinesAndroid` | `1.6.4` | `1.10.2` | None |
| `libs.appcompat.appcompat` | `1.4.2` | `1.7.0` | None |
| `libs.appcompat.recyclerView` | `1.2.1` | `1.4.0` | None |
| `libs.appcompat.constraintLayout` | `2.1.4` | `2.2.1` | None |
| `libs.ktx.core` | `1.8.0` | `1.16.0` | None |
| `libs.material` | `1.6.1` | `1.12.0` | None |
| `libs.preferences.preferencesKtx` | `1.1.1` | `1.2.1` | None |
| `libs.documentfile` | `1.0.1` | `1.1.0` | None |
| `libs.startup` | `1.1.1` | `1.2.0` | None |
| `libs.profileInstaller` | `1.3.1` | `1.4.1` | None |
| `libs.coil` | `2.6.0` | `2.7.0` (staying on Coil 2.x) | None |
| `libs.guava` | `30.1.1-android` | `33.4.0-android` | None |

#### [MODIFY] All modules with `composeOptions { kotlinCompilerExtensionVersion = ... }`
- **Remove** the `composeOptions` block from [`omnidroid-app/build.gradle.kts`](file:///d:/Work/Omnidroid/omnidroid-app/build.gradle.kts#L117-L119), [`retrograde-util/build.gradle.kts`](file:///d:/Work/Omnidroid/retrograde-util/build.gradle.kts#L18-L20), and [`omnidroid-touchinput/build.gradle.kts`](file:///d:/Work/Omnidroid/omnidroid-touchinput/build.gradle.kts#L24-L26)
- The Kotlin 2.0 Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) handles this automatically — the manual `kotlinCompilerExtensionVersion` override is both unnecessary and potentially conflicting

### Verification
- Full clean build
- Verify Compose UI renders correctly (main screen, settings, game menu)
- Test navigation flows (deep links, back stack)

---

## Phase 6 — Cleanup & Hardening

### Changes

#### [MODIFY] [`gradle.properties`](file:///d:/Work/Omnidroid/gradle.properties)
- Remove `android.enableJetifier=true` (all dependencies are now AndroidX-native)
- Remove `android.enableD8.desugaring=true` (D8 is the default desugarer since AGP 3.1; this flag is deprecated)

#### [MODIFY] [`proguard-rules.pro`](file:///d:/Work/Omnidroid/omnidroid-app/proguard-rules.pro)
- Clean up obsolete rules (`android.arch.lifecycle`, `Platform$Java8`, old OkHttp/Okio suppressions)
- Add Hilt proguard rules (Hilt ships consumer rules, but custom rules may be needed for the `model` keep patterns)

#### [MODIFY] [`build.gradle.kts`](file:///d:/Work/Omnidroid/build.gradle.kts) (root)
- Clean up `resolutionStrategy` — the `kotlin-stdlib-jre → jdk` rename is no longer needed (Kotlin 2.0 doesn't ship `jre` variants)
- Update `com.android.application` plugin version alignment: `8.4.0` → confirm compatibility with all new dependencies

### Verification
- Full clean build (all variants: `freeBundle`, `freeDynamic`, `playBundle`, `playDynamic`)
- Release build with ProGuard (`./gradlew assemblePlayDynamicRelease`)
- Verify no Jetifier warnings in build output

---

## Version Summary Table

| Library | Current | Target | minSdk Safe? |
|---|---|---|---|
| **GMS (global pin)** | 17.0.0 | 21.6.0 | ✅ API 21+ |
| **play-services-auth** | 17.0.0 | 21.6.0 | ✅ API 21+ |
| **Dagger** | 2.19 (dagger-android) | 2.60.1 (Hilt) | ✅ API 23 |
| **kapt** | kapt | KSP 2.0.21-1.0.27 | ✅ N/A |
| **OkHttp** | 4.9.1 | 4.12.0 or 5.5.0 | ✅ API 21+ |
| **Okio** | 2.10.0 | 3.18.2 | ✅ N/A |
| **Retrofit** | 2.9.0 | 3.0.0 | ✅ API 21+ |
| **Compose BOM** | 2024.02.02 | 2026.08.00 | ✅ API 21+ |
| **Navigation** | 2.5.2 | 2.8.8 | ✅ API 21+ |
| **Lifecycle** | 2.6.1 | 2.9.0 | ✅ API 21+ |
| **Room** | 2.6.1 | 2.8.4 | ✅ API 21+ |
| **Work** | 2.9.0 | 2.10.0 | ✅ API 21+ |
| **Paging** | 3.2.1 | 3.3.6 | ✅ API 21+ |
| **Coroutines** | 1.6.4 | 1.10.2 | ✅ N/A |
| **Serialization** | 1.2.2 | 1.8.1 | ✅ N/A |
| **arch.lifecycle:reactivestreams** | 1.1.1 | **REMOVED** | ✅ |
| **minSdk** | **23** | **23** | ✅ Unchanged |

---

## Verification Plan

### Automated Tests
```bash
# Full debug build — all variants
./gradlew assembleDebug

# Release build with ProGuard (most likely to catch missing keep rules)
./gradlew assemblePlayDynamicRelease

# Lint check
./gradlew lintPlayDynamicDebug
```

### Manual Verification
- Launch the app on an API 23 emulator — confirm startup, game list, settings
- Test dynamic feature delivery flow (download a core on Play variant)
- Test Google Sign-in / Drive sync (Play variant)
- Test Worker execution: trigger library re-index, save sync, cache cleanup
- Verify Compose UI on both mobile and TV form factors
- Check that ProGuard doesn't strip Hilt-generated components
