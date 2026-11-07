package com.bqsqwad.share

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.bqsqwad.share.ads.AdManager
import com.bqsqwad.share.ui.ShareAppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var ads: AdManager
    private var backgroundAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        ads.startConsent(this) {}
        setContent {
            val vm: ShareViewModel = hiltViewModel()
            val settings by vm.settings.collectAsState(initial = null)
            ShareAppRoot(activity = this, vm = vm, settings = settings, ads = ads)
        }
    }

    override fun onPause() {
        backgroundAt = System.currentTimeMillis()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (backgroundAt > 0L) ads.maybeShowAppOpen(this, System.currentTimeMillis() - backgroundAt)
    }
}
