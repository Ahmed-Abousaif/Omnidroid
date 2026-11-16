package gg.padkit

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import gg.padkit.haptics.HapticEffect
import gg.padkit.haptics.createAndroidHapticFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 26, 28, 35])
class AndroidHapticFeedbackTest {
    private val context = RuntimeEnvironment.getApplication()
    private val vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    private val shadow = shadowOf(vibrator)

    @Test
    fun silentAndInvalidStrengthNeverVibrates() {
        shadow.setHasVibrator(true)
        val feedback = createAndroidHapticFeedback(context)
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { feedback.generate(HapticEffect.PRESS, it) }
        assertFalse(shadow.isVibrating)
    }

    @Test
    fun pressAndReleaseHaveDistinctDurationsWithLegacyAndCapabilityFallbacks() {
        shadow.setHasVibrator(true)
        shadow.setHasAmplitudeControl(false)
        val feedback = createAndroidHapticFeedback(context)
        feedback.generate(HapticEffect.PRESS, 0.5f)
        assertTrue(shadow.isVibrating)
        assertEquals(30L, shadow.milliseconds)
        feedback.generate(HapticEffect.RELEASE, 0.5f)
        assertEquals(15L, shadow.milliseconds)
    }

    @Test
    fun missingHardwareIsSilent() {
        shadow.setHasVibrator(false)
        createAndroidHapticFeedback(context).generate(HapticEffect.PRESS, 1f)
        assertFalse(shadow.isVibrating)
    }

    @Test
    @Config(sdk = [35])
    fun amplitudeTracksLiveIntensityOnCapableHardware() {
        shadow.setHasVibrator(true)
        shadow.setHasAmplitudeControl(true)
        val feedback = createAndroidHapticFeedback(context)
        feedback.generate(HapticEffect.PRESS, 0.5f)
        assertEquals(128f / 255f, amplitude(), 0.001f)
        feedback.generate(HapticEffect.RELEASE, 0.25f)
        assertEquals(45f / 255f, amplitude(), 0.001f)
        feedback.generate(HapticEffect.PRESS, 0.1f)
        assertEquals(26f / 255f, amplitude(), 0.001f)
    }

    private fun amplitude(): Float {
        val segments =
            ReflectionHelpers.getStaticField<List<Any>>(
                ShadowVibrator::class.java,
                "vibrationEffectSegments",
            )
        return ReflectionHelpers.callInstanceMethod(segments.single(), "getAmplitude")
    }
}
