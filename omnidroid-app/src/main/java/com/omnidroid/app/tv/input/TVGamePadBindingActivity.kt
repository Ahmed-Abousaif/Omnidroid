package com.omnidroid.app.tv.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.leanback.app.GuidedStepSupportFragment
import com.omnidroid.app.shared.input.InputBindingUpdater
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.tv.shared.BaseTVActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TVGamePadBindingActivity : BaseTVActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var inputBindingUpdater: InputBindingUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputBindingUpdater = InputBindingUpdater(inputDeviceManager, intent)

        if (null == savedInstanceState) {
            val fragment =
                TVGamePadBindingFragment.create(
                    inputBindingUpdater.getTitle(applicationContext),
                    inputBindingUpdater.getMessage(applicationContext),
                )
            GuidedStepSupportFragment.addAsRoot(this, fragment, android.R.id.content)
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        return inputBindingUpdater.handleKeyEvent(event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val result = inputBindingUpdater.handleKeyEvent(event)

        if (result) {
            finish()
        }

        return result
    }
}
