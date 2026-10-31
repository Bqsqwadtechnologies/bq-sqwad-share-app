package com.bqsqwad.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.bqsqwad.share.ads.AdManager
import com.bqsqwad.share.ui.ShareAppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var ads: AdManager
    private var backgroundAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
