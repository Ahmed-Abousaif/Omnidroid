package com.omnidroid.app.shared.cast

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.Display
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen

class CastIdlePresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context)
        val owner = ownerActivity()
        if (owner is LifecycleOwner) {
            view.setViewTreeLifecycleOwner(owner)
        }
        if (owner is ViewModelStoreOwner) {
            view.setViewTreeViewModelStoreOwner(owner)
        }
        if (owner is SavedStateRegistryOwner) {
            view.setViewTreeSavedStateRegistryOwner(owner)
        }
        view.setContent {
            AppTheme {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(HomeChromeBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = LibraryNeonGreen,
                            modifier = Modifier.size(72.dp),
                        )
                        Text(
                            text = stringResource(R.string.cast_idle_title),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        Text(
                            text = stringResource(R.string.cast_idle_message),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                        )
                    }
                }
            }
        }
        setContentView(view)
    }

    private fun ownerActivity(): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
