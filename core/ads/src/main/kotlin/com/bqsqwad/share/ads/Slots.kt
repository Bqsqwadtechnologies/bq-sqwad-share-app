package com.bqsqwad.share.ads

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext

object AdUnits {
    const val BANNER = BuildConfig.AD_BANNER
    const val NATIVE = BuildConfig.AD_NATIVE
    const val INTERSTITIAL = BuildConfig.AD_INTERSTITIAL
    const val REWARDED = BuildConfig.AD_REWARDED
    const val APP_OPEN = BuildConfig.AD_APP_OPEN
}

@Composable
fun BannerSlot(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth().height(60.dp),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdUnits.BANNER
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

@Composable
fun NativeSlot(enabled: Boolean) {
    if (!enabled) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = "Ad", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            factory = { ctx -> NativeAdView(ctx).apply { contentDescription = "Ad" } },
        )
    }
}

@Suppress("unused")
fun bindNative(view: NativeAdView, ad: NativeAd) { view.setNativeAd(ad) }
