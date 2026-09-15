package com.omnidroid.app.tv.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.omnidroid.app.tv.shared.TVBaseSettingsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TVSettingsActivity : TVBaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment = TVSettingsFragmentWrapper()
            supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commit()
        }
    }

    @AndroidEntryPoint
    class TVSettingsFragmentWrapper : BaseSettingsFragmentWrapper() {
        override fun createFragment(): Fragment {
            return TVSettingsFragment()
        }
    }
}
