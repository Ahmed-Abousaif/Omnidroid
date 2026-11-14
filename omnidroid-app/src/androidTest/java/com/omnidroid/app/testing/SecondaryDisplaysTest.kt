package com.omnidroid.app.testing

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.shared.displays.DisplayOutput
import com.omnidroid.app.shared.displays.DisplayProtocol
import com.omnidroid.app.shared.displays.DisplaySessionService
import com.omnidroid.app.shared.displays.DisplayStatus
import com.omnidroid.app.shared.displays.DisplaysState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class SecondaryDisplaysTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<OmnidroidApplication>()
    private val displays get() = app.displays

    private fun status(id: Int) = displays.state.value.entries.firstOrNull { it.facts.id == id }?.status

    @Suppress("DEPRECATION")
    private fun coordinatorIsRunning() =
        app.getSystemService(ActivityManager::class.java).getRunningServices(100).any {
            it.service.className == DisplaySessionService::class.java.name
        }

    private fun startGameHost(scenario: ActivityScenario<LibraryStressActivity>): Messenger {
        val game = java.util.concurrent.atomic.AtomicReference<Messenger>()
        val ready =
            Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    game.set(message.replyTo)
                    true
                },
            )
        scenario.onActivity {
            it.startActivity(Intent(it, DisplayProcessTestActivity::class.java).putExtra("ready", ready))
        }
        await("Game process is resumed") { game.get() != null }
        return game.get()
    }

    private fun await(
        reason: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 10000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(reason, condition())
    }

    private class Observer(private val context: Context) : AutoCloseable {
        @Volatile var state = DisplaysState()
        private var remote: Messenger? = null
        private val endpoint =
            Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    if (message.what == DisplayProtocol.STATE) state = DisplayProtocol.decode(message.data)
                    true
                },
            )
        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    binder: IBinder,
                ) {
                    remote = Messenger(binder)
                    remote!!.send(
                        Message.obtain(null, DisplayProtocol.UPDATE_HOST).also {
                            it.replyTo = endpoint
                            it.data = Bundle().apply { putBoolean(DisplayProtocol.FOREGROUND, false) }
                        },
                    )
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    state = DisplaysState()
                }
            }

        init {
            check(
                context.bindService(
                    Intent(context, DisplaySessionService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE,
                ),
            )
        }

        override fun close() {
            remote?.send(Message.obtain(null, DisplayProtocol.REMOVE_HOST).also { it.replyTo = endpoint })
            context.unbindService(connection)
        }
    }

    private class Screen(
        context: Context,
        name: String,
        width: Int,
        height: Int,
        public: Boolean = true,
        private val capture: Boolean = false,
        presentation: Boolean = true,
    ) : AutoCloseable {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        @Volatile var bitmap: Bitmap? = null
        val display: VirtualDisplay

        init {
            reader.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { image ->
                    if (!capture) return@use
                    val plane = image.planes[0]
                    // The final row need not include stride padding. Copy only real pixels.
                    val pixels = ByteBuffer.allocate(image.width * image.height * 4)
                    val row = ByteArray(image.width * 4)
                    val buffer = plane.buffer
                    for (y in 0 until image.height) {
                        buffer.position(y * plane.rowStride)
                        buffer.get(row)
                        pixels.put(row)
                    }
                    pixels.rewind()
                    bitmap =
                        Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
                            copyPixelsFromBuffer(pixels)
                        }
                }
            }, Handler(Looper.getMainLooper()))
            val flags =
                (if (presentation) DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION else 0) or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    (if (public) DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC else 0)
            display =
                context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                    name, width, height, 160, reader.surface, flags,
                )
        }

        val id get() = display.display.displayId

        override fun close() {
            // Closing from the test thread could invalidate a buffer being read by the main thread.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                display.release()
                reader.setOnImageAvailableListener(null, null)
                reader.close()
            }
        }
    }

    @Test fun multipleDisplaysHideSleepBackgroundReconnectAndRendering() {
        ActivityScenario.launch(LibraryStressActivity::class.java).use { scenario ->
            instrumentation.runOnMainSync { displays.setAutomatic(true) }
            Screen(app, "Handheld bottom", 800, 720, capture = true).use { handheld ->
                Screen(app, "Living room TV", 1280, 720).use { tv ->
                    Screen(app, "App-owned private display", 640, 480, public = false).use { owned ->
                        await("Both public displays show placeholders") {
                            listOf(handheld.id, tv.id).all { id ->
                                status(id) == DisplayStatus.SHOWING
                            }
                        }
                        await("An accessible app-owned private display can show output") {
                            status(owned.id) == DisplayStatus.SHOWING
                        }
                        scenario.onActivity { assertTrue("Primary window keeps focus", it.hasWindowFocus()) }
                        await("Placeholder produced pixels") { handheld.bitmap != null }
                        instrumentation.waitForIdleSync()
                        val screenshot = File(app.getExternalFilesDir(null), "secondary-display-test.png")
                        screenshot.outputStream().use {
                            assertTrue(handheld.bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        // Retain a QA artifact after Gradle uninstalls the instrumentation target.
                        ParcelFileDescriptor.AutoCloseInputStream(
                            instrumentation.uiAutomation.executeShellCommand(
                                "cp ${screenshot.absolutePath} /data/local/tmp/omnidroid-secondary-display-test.png",
                            ),
                        ).use { it.readBytes() }

                        instrumentation.runOnMainSync { displays.setHidden(handheld.id, true) }
                        await("Hide applies to one display") {
                            status(handheld.id) == DisplayStatus.HIDDEN
                        }
                        assertTrue(
                            status(tv.id) == DisplayStatus.SHOWING,
                        )
                        instrumentation.runOnMainSync { displays.setHidden(handheld.id, false) }
                        await("Show restores the placeholder") {
                            status(handheld.id) == DisplayStatus.SHOWING
                        }
                        handheld.display.setSurface(null)
                        await("Sleeping display stays detected") {
                            status(handheld.id) == DisplayStatus.SLEEPING
                        }
                        handheld.display.setSurface(handheld.reader.surface)
                        await("Waking display restores its placeholder") {
                            status(handheld.id) == DisplayStatus.SHOWING
                        }
                        instrumentation.runOnMainSync { displays.setAutomatic(false) }
                        await("Automatic off dismisses every output") { !displays.state.value.hasOutput }
                        instrumentation.runOnMainSync { displays.setAutomatic(true) }
                        await("Automatic on restores outputs") { displays.state.value.hasOutput }
                        scenario.recreate()
                        await("Activity recreation preserves output") { displays.state.value.hasOutput }
                        scenario.moveToState(Lifecycle.State.CREATED)
                        await("Leaving the app clears active state") { !displays.state.value.hasOutput }
                        scenario.moveToState(Lifecycle.State.RESUMED)
                        await("Returning restores output") { displays.state.value.hasOutput }
                    }
                }
            }
            await("Disconnected displays are removed") { displays.state.value.entries.isEmpty() }
            Screen(app, "Reconnected screen", 720, 800).use { screen ->
                await("A new connection is not hidden by an old selection") {
                    status(screen.id) == DisplayStatus.SHOWING
                }
            }
        }
        await("Closing the host clears output", { !displays.state.value.hasOutput })
    }

    @Test fun placeholderSurvivesTheGameProcessHandoff() {
        ActivityScenario.launch(LibraryStressActivity::class.java).use { scenario ->
            instrumentation.runOnMainSync { displays.setAutomatic(true) }
            Observer(app).use { observer ->
                Screen(app, "Process handoff screen", 800, 720).use { screen ->
                    await("Library shows placeholder") { displays.state.value.hasOutput }
                    val game = startGameHost(scenario)
                    // Once the main client unbinds, only the :game client can keep this output alive.
                    Thread.sleep(1200)
                    assertFalse("Library client has released its binding", displays.state.value.connected)
                    assertTrue("Game process keeps the placeholder visible", observer.state.hasOutput)
                    assertTrue(
                        "Correct display remains active",
                        observer.state.entries.any {
                            it.facts.id == screen.id && it.status == DisplayStatus.SHOWING
                        },
                    )
                    game.send(Message.obtain(null, 1))
                    await("Library reconnects after game exit") { displays.state.value.hasOutput }
                    scenario.onActivity { assertTrue(it.hasWindowFocus()) }
                }
            }
        }
    }

    @Test fun activityOnlyDisplayEitherShowsSafelyOrStaysUnavailable() {
        ActivityScenario.launch(LibraryStressActivity::class.java).use { scenario ->
            instrumentation.runOnMainSync { displays.setAutomatic(true) }
            Screen(app, "Activity-only screen", 800, 720, presentation = false).use { screen ->
                await("Activity output resolves without a retry loop") {
                    displays.state.value.entries.any {
                        it.facts.id == screen.id && it.status in setOf(DisplayStatus.SHOWING, DisplayStatus.UNAVAILABLE)
                    }
                }
                Thread.sleep(1200)
                val entry = displays.state.value.entries.single { it.facts.id == screen.id }
                assertFalse(entry.facts.presentation)
                assertTrue(entry.status in setOf(DisplayStatus.SHOWING, DisplayStatus.UNAVAILABLE))
                if (entry.status == DisplayStatus.SHOWING) assertEquals(DisplayOutput.ACTIVITY, entry.output)
                scenario.onActivity { assertTrue("Source retains controller focus", it.hasWindowFocus()) }
            }
        }
    }

    @Test fun singleScreenSkipsCoordinatorButHotplugDuringGameplayStillWorks() {
        ActivityScenario.launch(LibraryStressActivity::class.java).use { scenario ->
            await("Single-screen discovery is ready without a service") {
                displays.state.value.connected && !coordinatorIsRunning()
            }
            instrumentation.runOnMainSync {
                displays.setAutomatic(false)
                displays.refresh()
            }
            assertFalse("Settings and refresh must not start the coordinator", coordinatorIsRunning())
            assertFalse(displays.state.value.automatic)
            scenario.recreate()
            assertFalse(displays.state.value.automatic)
            assertFalse("Recreation stays on the single-screen path", coordinatorIsRunning())
            Screen(app, "Initially disabled screen", 800, 720).use { screen ->
                await("A later connection respects the saved preference") {
                    status(screen.id) == DisplayStatus.HIDDEN
                }
            }
            await("Last display disconnect releases the coordinator") { !coordinatorIsRunning() }
            instrumentation.runOnMainSync { displays.setAutomatic(true) }
            val game = startGameHost(scenario)
            await("Library watcher releases during gameplay") { !displays.state.value.connected }
            assertFalse("Single-screen gameplay must not bind the main-process service", coordinatorIsRunning())
            Screen(app, "TV connected during gameplay", 800, 720).use { screen ->
                Observer(app).use { observer ->
                    await("The game watcher activates output after hotplug") {
                        observer.state.entries.any {
                            it.facts.id == screen.id && it.status == DisplayStatus.SHOWING
                        }
                    }
                }
            }
            await("Disconnecting during gameplay releases the coordinator again") { !coordinatorIsRunning() }
            game.send(Message.obtain(null, 1))
            await("Library returns to the single-screen path") {
                displays.state.value.connected && !coordinatorIsRunning()
            }
        }
    }
}
