package com.omnidroid.app.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnidroid.app.shared.ps3.Armsx3Bridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Ps3NativeSmokeTest {
    @Test
    fun curatedGraphicsOptionsMatchPinnedEngine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Armsx3Bridge.withDataAccess(context) {
            Armsx3Bridge.initialize(context)
            for (option in com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.all) {
                val metadata = org.json.JSONObject(Armsx3Bridge.settings(option.path))
                if (option.kind == com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.ENUM) {
                    val variants = metadata.getJSONArray("variants")
                    val supported = (0 until variants.length()).map { variants.getString(it) }
                    org.junit.Assert.assertTrue(
                        "${option.key}: unsupported choices",
                        option.choices.all {
                            it.key in
                                supported
                        },
                    )
                }
                val json =
                    when (option.kind) {
                        com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.ENUM ->
                            org.json.JSONObject.quote(
                                option.default,
                            )
                        com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.BOOL ->
                            (option.default == "enabled")
                                .toString()
                        else -> option.default
                    }
                Armsx3Bridge.setting(option.path, json)
                val expected =
                    if (option.kind ==
                        com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.BOOL
                    ) {
                        (option.default == "enabled").toString()
                    } else {
                        option.default
                    }
                assertEquals(
                    option.key,
                    expected,
                    org.json
                        .JSONObject(Armsx3Bridge.settings(option.path))
                        .get("value")
                        .toString(),
                )
            }
            org.junit.Assert.assertTrue(
                org.json.JSONObject(Armsx3Bridge.settings("Savestate@@Compatible Savestate Mode")).getBoolean("value"),
            )
        }
    }

    @Test
    fun indexesSelectedIsoAndStartsGameScreen() {
        val path = InstrumentationRegistry.getArguments().getString("ps3Game")
        org.junit.Assume.assumeNotNull(path)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = java.io.File(path!!)
        val info =
            com.omnidroid.lib.library.scan.Ps3Content
                .read(context, android.net.Uri.fromFile(source))!!
        val directory =
            java.io
                .File(
                    com.omnidroid.lib.storage
                        .DirectoriesManager(context)
                        .getInternalRomsDirectory(),
                    "PS3",
                ).apply {
                    mkdirs()
                }
        val destination = java.io.File(directory, "${info.titleId}.iso")
        if (!destination.exists()) source.copyTo(destination)
        val dependencies =
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context,
                Ps3PhoneTestDependencies::class.java,
            )
        kotlinx.coroutines.runBlocking { dependencies.library().indexLibrary(forceDeep = true) }
        val game =
            dependencies.database().gameDao().selectByFileUri(
                android.net.Uri
                    .fromFile(destination)
                    .toString(),
            )
        org.junit.Assert.assertNotNull("The PS3 ISO was not indexed", game)
        assertEquals("ps3", game!!.systemId)
        assertEquals(info.title, game.title)
        com.omnidroid.app.mobile.feature.library
            .RegisteredSystemsStore(
                context,
            ).register(com.omnidroid.lib.library.MetaSystemID.PS3)
        com.omnidroid.lib.core
            .ConsoleInstallPreferences(
                context,
            ).setUninstalled(com.omnidroid.lib.library.MetaSystemID.PS3, false)
        val config =
            com.omnidroid.lib.library.GameSystem
                .findById("ps3")
                .systemCoreConfigs
                .first()
        instrumentation.runOnMainSync {
            context.startActivity(
                android.content.Intent(context, com.omnidroid.app.mobile.feature.game.GameActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(com.omnidroid.app.shared.game.BaseGameActivity.EXTRA_GAME, game)
                    putExtra(com.omnidroid.app.shared.game.BaseGameActivity.EXTRA_SYSTEM_CORE_CONFIG, config)
                },
            )
        }
        Thread.sleep(20_000)
        val screen = instrumentation.uiAutomation.takeScreenshot()
        org.junit.Assert.assertNotNull(screen)
        java.io.File(context.filesDir, "ps3-test/app-game-screen.png").outputStream().use {
            screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        if (InstrumentationRegistry.getArguments().getString("ps3HostMenu") == "true") {
            instrumentation.uiAutomation.serviceInfo =
                instrumentation.uiAutomation.serviceInfo.apply {
                    flags =
                        flags or
                        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                }

            fun find(
                root: android.view.accessibility.AccessibilityNodeInfo?,
                text: String,
            ): android.view.accessibility.AccessibilityNodeInfo? {
                if (root == null) return null
                if (root.isVisibleToUser &&
                    (root.text?.toString() == text || root.contentDescription?.toString() == text)
                ) {
                    return root
                }
                for (index in 0 until root.childCount) find(root.getChild(index), text)?.let { return it }
                return null
            }

            fun node(text: String): android.view.accessibility.AccessibilityNodeInfo {
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    find(instrumentation.uiAutomation.rootInActiveWindow, text)?.let { return it }
                    Thread.sleep(100)
                }
                error("Missing Omnidroid menu item: $text")
            }

            fun touch(
                target: android.view.accessibility.AccessibilityNodeInfo,
                hold: Long = 80,
            ) {
                val rect = android.graphics.Rect().apply { target.getBoundsInScreen(this) }
                val down = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    if (action == android.view.MotionEvent.ACTION_UP) Thread.sleep(hold)
                    val event =
                        android.view.MotionEvent.obtain(
                            down,
                            android.os.SystemClock.uptimeMillis(),
                            action,
                            rect.exactCenterX(),
                            rect.exactCenterY(),
                            0,
                        )
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        org.junit.Assert.assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                    } finally {
                        event.recycle()
                    }
                }
            }

            fun capture(name: String) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(context.filesDir, "ps3-test/$name.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }

            fun back() {
                val now = android.os.SystemClock.uptimeMillis()
                instrumentation.uiAutomation.injectInputEvent(
                    android.view.KeyEvent(
                        now,
                        now,
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_BACK,
                        0,
                    ),
                    true,
                )
                instrumentation.uiAutomation.injectInputEvent(
                    android.view.KeyEvent(
                        now,
                        now,
                        android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_BACK,
                        0,
                    ),
                    true,
                )
                Thread.sleep(500)
            }
            touch(node(context.getString(com.omnidroid.touchinput.R.string.touch_control_game_menu)), 800)
            node(context.getString(com.omnidroid.R.string.game_menu_save))
            node(context.getString(com.omnidroid.R.string.game_menu_load))
            capture("omnidroid-ps3-menu")
            touch(node(context.getString(com.omnidroid.R.string.game_menu_load)))
            node(context.getString(com.omnidroid.R.string.game_menu_state, "4"))
            org.junit.Assert.assertNull(
                find(
                    instrumentation.uiAutomation.rootInActiveWindow,
                    context.getString(com.omnidroid.R.string.game_menu_autosave),
                ),
            )
            capture("omnidroid-ps3-slots")
            back()
            touch(node(context.getString(com.omnidroid.R.string.game_menu_settings)))
            node(context.getString(com.omnidroid.lib.R.string.setting_ps3_frame_limit))
            capture("omnidroid-ps3-graphics")
            // Close the settings page, then the host menu, before normal game quit below.
            repeat(2) { back() }
        }
        // Keep the actual app process alive beyond the boot logo, then exercise normal quit.
        val observationMs =
            InstrumentationRegistry
                .getArguments()
                .getString("ps3ObservationMs")
                ?.toLongOrNull()
                ?.coerceIn(30_000L, 600_000L) ?: 60_000L
        Thread.sleep(observationMs)
        instrumentation.uiAutomation.takeScreenshot()?.let { later ->
            java.io.File(context.filesDir, "ps3-test/app-game-later.png").outputStream().use {
                later.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val now = android.os.SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(
            android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0),
            true,
        )
        instrumentation.uiAutomation.injectInputEvent(
            android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0),
            true,
        )
        Thread.sleep(2_000)
        // The game process must release its native data lock before another launch/install.
        Armsx3Bridge.withDataAccess(context) { }
    }

    @Test
    fun readsSelectedDiscWithNativeProbe() {
        val path = InstrumentationRegistry.getArguments().getString("ps3Game")
        org.junit.Assume.assumeNotNull(path)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = android.net.Uri.fromFile(java.io.File(path!!))
        val expected =
            com.omnidroid.lib.library.scan.Ps3Content
                .read(context, uri)!!
        Armsx3Bridge.withDataAccess(context) {
            Armsx3Bridge.initialize(context)
            val actual = Armsx3Bridge.probe(context, uri)
            assertEquals(expected.titleId, actual.titleId)
            assertEquals(expected.title, actual.title)
        }
    }

    @Test
    fun rendersSelectedGameAndSurvivesPauseResume() {
        val path = InstrumentationRegistry.getArguments().getString("ps3Game")
        org.junit.Assume.assumeNotNull(path)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assert.assertTrue(
            com.omnidroid.lib.backend.ps3.Ps3BackendDescriptor
                .firmwareInstalled(context),
        )
        val info =
            com.omnidroid.lib.library.scan.Ps3Content.read(
                context,
                android.net.Uri.fromFile(java.io.File(path!!)),
            )!!
        val game =
            com.omnidroid.lib.library.db.entity.Game(
                fileName = java.io.File(path).name,
                fileUri =
                    java.io
                        .File(path)
                        .toURI()
                        .toString(),
                title = info.title,
                systemId = "ps3",
                developer = null,
                coverFrontUrl = null,
                lastIndexedAt = System.currentTimeMillis(),
            )
        val config =
            com.omnidroid.lib.library.GameSystem
                .findById("ps3")
                .systemCoreConfigs
                .first()
        val runtime =
            com.omnidroid.app.shared.game.backend.ps3
                .Ps3BackendRuntime()
        val exited =
            java.util.concurrent.atomic
                .AtomicReference<Int?>(null)
        androidx.test.core.app.ActivityScenario.launch(LibraryStressActivity::class.java).use { scenario ->
            val host =
                java.util.concurrent.atomic
                    .AtomicReference<android.app.Activity>()
            scenario.onActivity { host.set(it) }
            kotlinx.coroutines.runBlocking { runtime.prepare(host.get(), game, config).getOrThrow() }
            lateinit var session: com.omnidroid.app.shared.game.backend.ps3.Ps3BackendSession
            scenario.onActivity { activity ->
                session =
                    runtime.createSession(
                        activity,
                        game,
                        config,
                        android.widget.FrameLayout(activity),
                    ) { exited.set(it) }
                for (variable in session.getVariables()) {
                    val option =
                        com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.all
                            .first { it.key == variable.key }
                    org.junit.Assert.assertTrue(
                        "Invalid selected value for ${option.key}",
                        option.accepts(requireNotNull(variable.value)),
                    )
                    org.junit.Assert.assertTrue(requireNotNull(variable.description).contains(';'))
                }
                activity.setContentView(session.view)
                session.viewport = android.graphics.RectF(0f, 0f, 1f, 1f)
                session.start()
            }

            fun waitForState(
                expected: Int,
                timeout: Long = 120_000,
            ) {
                val deadline = android.os.SystemClock.elapsedRealtime() + timeout
                var nextLog = 0L
                while (Armsx3Bridge.state() != expected &&
                    exited.get() == null &&
                    android.os.SystemClock.elapsedRealtime() < deadline
                ) {
                    if (android.os.SystemClock.elapsedRealtime() > nextLog) {
                        android.util.Log.i("PS3-test", "Waiting for state $expected, actual=${Armsx3Bridge.state()}")
                        nextLog = android.os.SystemClock.elapsedRealtime() + 5000
                    }
                    Thread.sleep(200)
                }
                assertEquals("PS3 session exited=${exited.get()}", expected, Armsx3Bridge.state())
            }
            try {
                waitForState(3, 600_000)
                Thread.sleep(15_000)
                assertEquals(null, exited.get())
                val frame =
                    java.util.concurrent.atomic
                        .AtomicReference<android.graphics.Bitmap>()
                val copied = java.util.concurrent.CountDownLatch(1)
                val copyResult =
                    java.util.concurrent.atomic
                        .AtomicInteger(-1)
                scenario.onActivity {
                    val surface = (session.view as android.view.ViewGroup).getChildAt(0) as android.view.SurfaceView
                    val bitmap =
                        android.graphics.Bitmap.createBitmap(
                            surface.width,
                            surface.height,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                    frame.set(bitmap)
                    android.view.PixelCopy.request(surface, bitmap, { result ->
                        copyResult.set(result)
                        copied.countDown()
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                }
                org.junit.Assert.assertTrue(copied.await(10, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(android.view.PixelCopy.SUCCESS, copyResult.get())
                val bitmap = frame.get()
                java.io.File(context.filesDir, "ps3-test/frame.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                var lit = 0
                for (y in 0 until bitmap.height step 8) {
                    for (x in 0 until bitmap.width step 8) {
                        if (bitmap.getPixel(x, y) and 0x00ffffff != 0) lit++
                    }
                }
                org.junit.Assert.assertTrue("PS3 rendered only black pixels", lit > 100)
                val profile = InstrumentationRegistry.getArguments().getString("ps3Profile") == "true"
                if (profile || InstrumentationRegistry.getArguments().getString("ps3GameplayWarmup") == "true") {
                    // Reach the game using the PlayStation cross input, then stop injecting
                    // controls before sampling. Device captures establish the tested scene.
                    repeat(10) {
                        session.sendKeyEvent(
                            android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_BUTTON_B,
                            0,
                        )
                        Thread.sleep(100)
                        session.sendKeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_B, 0)
                        Thread.sleep(4900)
                    }
                    instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                        java.io.File(context.filesDir, "ps3-test/performance-scene.png").outputStream().use {
                            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        image.recycle()
                    }
                }
                if (profile) {
                    val samples = org.json.JSONArray()
                    try {
                        // ABBA order reduces a simple warmup/temperature ordering bias.
                        for (mode in listOf(0, 3, 3, 0)) {
                            Armsx3Bridge.performanceOptionsForTesting(mode)
                            Thread.sleep(3000)
                            val before = Armsx3Bridge.performanceStats()
                            val startedAt = android.os.SystemClock.elapsedRealtime()
                            Thread.sleep(20_000)
                            val after = Armsx3Bridge.performanceStats()
                            val frames = after.frames - before.frames
                            val period = after.periodNs - before.periodNs
                            org.junit.Assert.assertTrue("No valid frame timing samples", frames > 0 && period > 0)
                            if (mode == 3) {
                                org.junit.Assert.assertTrue(
                                    "Android did not receive performance reports",
                                    after.submitted > before.submitted,
                                )
                                assertEquals(after.rsxTid, after.hintTid)
                                assertEquals(before.errors, after.errors)
                            } else {
                                assertEquals(before.submitted, after.submitted)
                            }
                            val sample =
                                org.json
                                    .JSONObject()
                                    .put("options", mode)
                                    .put("frames", frames)
                                    .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - startedAt)
                                    .put("validFlipRate", frames * 1e9 / period)
                                    .put("periodMs", period / 1e6 / frames)
                                    .put("workMs", (after.workNs - before.workNs) / 1e6 / frames)
                                    .put("reports", after.submitted - before.submitted)
                                    .put(
                                        "hintErrors",
                                        after.errors - before.errors,
                                    ).put(
                                        "rsxTid",
                                        after.rsxTid,
                                    ).put("hintTid", after.hintTid)
                                    .put("displayHz", after.displayHz)
                                    .put(
                                        "thermalStatus",
                                        context
                                            .getSystemService(
                                                android.os.PowerManager::class.java,
                                            ).currentThermalStatus,
                                    ).put(
                                        "batteryTempC",
                                        context
                                            .registerReceiver(
                                                null,
                                                android.content.IntentFilter(
                                                    android.content.Intent.ACTION_BATTERY_CHANGED,
                                                ),
                                            )?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)
                                            ?.div(10.0),
                                    )
                            samples.put(sample)
                            java.io
                                .File(
                                    context.filesDir,
                                    "ps3-test/performance-results.json",
                                ).writeText(samples.toString(2))
                            android.util.Log.i("PS3-performance", sample.toString())
                        }
                    } finally {
                        Armsx3Bridge.performanceOptionsForTesting(3)
                    }
                    Thread.sleep(2000)
                    assertEquals(3L, Armsx3Bridge.performanceStats().options)
                    // It is a compile-time SPU policy, never hot-switched for capture.
                    org.junit.Assert.assertTrue(
                        runCatching {
                            Armsx3Bridge.setting("Savestate@@Compatible Savestate Mode", "false")
                        }.isFailure,
                    )
                    org.junit.Assert.assertTrue(
                        org.json
                            .JSONObject(
                                Armsx3Bridge.settings("Savestate@@Compatible Savestate Mode"),
                            ).getBoolean("value"),
                    )
                }
                scenario.onActivity { session.pause() }
                waitForState(4, 30_000)
                assertEquals(0L, Armsx3Bridge.performanceStats().hintTid)
                org.junit.Assert.assertFalse(Armsx3Bridge.performanceStats().active)
                scenario.onActivity { session.resume() }
                waitForState(3, 30_000)
                if (InstrumentationRegistry.getArguments().getString("ps3States") == "true") {
                    val testSlot =
                        com.omnidroid.lib.saves.Ps3StateSlots
                            .forTitle(context, info.titleId)
                            .nextIndex()
                    org.junit.Assert.assertTrue(kotlinx.coroutines.runBlocking { session.saveNativeState(testSlot) })
                    waitForState(3, 300_000)
                    org.junit.Assert.assertTrue(
                        com.omnidroid.lib.saves.Ps3StateSlots
                            .forTitle(context, info.titleId)
                            .info(testSlot)
                            .exists,
                    )
                    assertEquals(null, exited.get())
                    org.junit.Assert.assertTrue(kotlinx.coroutines.runBlocking { session.loadNativeState(testSlot) })
                    waitForState(3, 300_000)
                    assertEquals(null, exited.get())
                }
                scenario.onActivity { session.reset() }
                // Wait for the queued shutdown/reboot before inspecting the new VM.
                Armsx3Bridge.executor.submit {}.get(90, java.util.concurrent.TimeUnit.SECONDS)
                instrumentation.waitForIdleSync()
                Armsx3Bridge.executor.submit {}.get(300, java.util.concurrent.TimeUnit.SECONDS)
                waitForState(3, 300_000)
                Thread.sleep(3000)
                val stats = Armsx3Bridge.performanceStats()
                org.junit.Assert.assertTrue("Hints did not resume after restart", stats.submitted > 0)
                assertEquals(stats.rsxTid, stats.hintTid)
                org.junit.Assert.assertTrue(stats.sessionsCreated > 1)
            } finally {
                kotlinx.coroutines.runBlocking { session.persistNativeSaves(stop = true) }
                waitForState(0, 30_000)
            }
        }
    }

    @Test
    fun installSelectedFirmware() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = InstrumentationRegistry.getArguments().getString("ps3Firmware")
        org.junit.Assume.assumeNotNull(path)
        val context = instrumentation.targetContext
        var lastLog = 0L
        val result =
            Armsx3Bridge.install(context, android.net.Uri.fromFile(java.io.File(path!!)), "firmware") { message ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastLog > 1000) {
                    android.util.Log.i("PS3-test", message)
                    lastLog = now
                }
            }
        android.util.Log.i("PS3-test", result)
        org.junit.Assert.assertTrue(
            com.omnidroid.lib.backend.ps3.Ps3BackendDescriptor
                .firmwareInstalled(context),
        )
    }

    @Test
    fun initializesPinnedEngineAndReportsUnreadableGame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Armsx3Bridge.initialize(context)
        assertEquals(0, Armsx3Bridge.state())
        assertEquals(3L, Armsx3Bridge.performanceStats().options)
        assertEquals(0L, Armsx3Bridge.performanceStats().hintTid)
        Armsx3Bridge.displayRate(120f)
        assertEquals(120.0, Armsx3Bridge.performanceStats().displayHz, 0.01)
        Armsx3Bridge.displayRate(Float.NaN)
        assertEquals(120.0, Armsx3Bridge.performanceStats().displayHz, 0.01)
        Armsx3Bridge.displayRate(60f)
        val result = Armsx3Bridge.boot(java.io.File(context.filesDir, "missing-ps3-game/EBOOT.BIN").absolutePath)
        assertNotEquals(0, result)
        Armsx3Bridge.stop()
        assertEquals(0, Armsx3Bridge.state())
    }
}
