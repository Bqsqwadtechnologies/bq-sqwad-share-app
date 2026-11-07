package com.bqsqwad.share.ads

import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

object AdUnits {
    const val APP_ID = "ca-app-pub-2748789567113929~9250342978"
    const val HOME_BANNER = "ca-app-pub-2748789567113929/3184852834"
    const val HOME_NATIVE = "ca-app-pub-2748789567113929/4105208956"
    const val HISTORY_BANNER = "ca-app-pub-2748789567113929/2610137763"
    const val APPS_NATIVE = "ca-app-pub-2748789567113929/7661310589"
    const val HISTORY_NATIVE = "ca-app-pub-2748789567113929/8792402730"
    const val TRANSFER_INTERSTITIAL = "ca-app-pub-2748789567113929/9483714054"
    const val AD_FREE_REWARDED = "ca-app-pub-2748789567113929/4525854668"
    const val PREMIUM_REWARDED = "ca-app-pub-2748789567113929/5498181672"
    const val APP_OPEN = "ca-app-pub-2748789567113929/1843851002"
    const val PHOTOS_NATIVE = "ca-app-pub-2748789567113929/2764207122"
    const val VIDEOS_NATIVE = "ca-app-pub-2748789567113929/4334282970"
    const val MUSIC_NATIVE = "ca-app-pub-2748789567113929/1586856824"
    const val FILES_NATIVE = "ca-app-pub-2748789567113929/5399952630"
    const val FAVORITES_NATIVE = "ca-app-pub-2748789567113929/1460707621"
    const val MORE_BANNER = "ca-app-pub-2748789567113929/8081956290"
    const val STORAGE_BANNER = "ca-app-pub-2748789567113929/5786965443"
    const val TEAM_NATIVE = "ca-app-pub-2748789567113929/2726734014"
    const val FOLDER_COMPLETE_INTERSTITIAL = "ca-app-pub-2748789567113929/6647611818"
}

@Composable
fun BannerSlot(enabled: Boolean, adUnitId: String, modifier: Modifier = Modifier) {
    if (!enabled) return
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth().height(60.dp),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { view ->
            if (view.adUnitId != adUnitId) {
                view.adUnitId = adUnitId
                view.loadAd(AdRequest.Builder().build())
            }
        },
    )
}

@Composable
fun NativeSlot(enabled: Boolean, adUnitId: String, modifier: Modifier = Modifier) {
    if (!enabled) return
    val context = LocalContext.current
    var nativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }
    LaunchedEffect(adUnitId) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { loaded -> nativeAd = loaded }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }
    DisposableEffect(nativeAd) {
        onDispose { nativeAd?.destroy() }
    }
    nativeAd?.let { ad ->
        AndroidView(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            factory = { ctx ->
                NativeAdView(ctx).apply {
                    val column = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 12, 16, 12)
                    }
                    val disclosure = TextView(ctx).apply {
                        text = "Ad"
                        textSize = 11f
                    }
                    val headline = TextView(ctx).apply {
                        textSize = 16f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    val media = MediaView(ctx)
                    val body = TextView(ctx).apply { textSize = 13f }
                    val advertiser = TextView(ctx).apply { textSize = 11f }
                    val actionRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                    }
                    val icon = ImageView(ctx)
                    val cta = Button(ctx)
                    column.addView(disclosure)
                    column.addView(headline)
                    column.addView(media, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 150,
                    ))
                    column.addView(body)
                    column.addView(advertiser)
                    actionRow.addView(icon, LinearLayout.LayoutParams(48, 48))
                    actionRow.addView(cta)
                    column.addView(actionRow)
                    addView(column, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ))
                    headlineView = headline
                    mediaView = media
                    bodyView = body
                    advertiserView = advertiser
                    iconView = icon
                    callToActionView = cta
                    setNativeAd(ad)
                }
            },
            update = { view ->
                view.headlineView?.let { (it as TextView).text = ad.headline }
                view.bodyView?.let { (it as TextView).text = ad.body.orEmpty() }
                view.advertiserView?.let { (it as TextView).text = ad.advertiser.orEmpty() }
                view.callToActionView?.let {
                    (it as Button).text = ad.callToAction.orEmpty()
                    it.visibility = if (ad.callToAction.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
                }
                view.iconView?.let {
                    val drawable = ad.icon?.drawable
                    (it as ImageView).setImageDrawable(drawable)
                    it.visibility = if (drawable == null) android.view.View.GONE else android.view.View.VISIBLE
                }
                view.mediaView?.setMediaContent(ad.mediaContent)
                view.setNativeAd(ad)
            },
        )
    }
}
