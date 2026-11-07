package com.bqsqwad.share.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AdManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val _canRequestAdsState = MutableStateFlow(false)
    val canRequestAdsState: StateFlow<Boolean> = _canRequestAdsState.asStateFlow()

    private var mobileAdsInitialized = false

    var consentResolved: Boolean = false
        private set
    var canRequestAds: Boolean = false
        private set
    var appOpenLoaded: Boolean = false
        private set
    var interstitialLoaded: Boolean = false
        private set
    var rewardedLoaded: Boolean = false
        private set

    private var interstitial: InterstitialAd? = null
    private var adFreeRewarded: RewardedAd? = null
    private var premiumRewarded: RewardedAd? = null
    private var appOpen: AppOpenAd? = null
    private var lastInterstitialAt = 0L
    private var appOpenShownThisSession = false

    fun startConsent(activity: Activity, onDone: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        val consent = UserMessagingPlatform.getConsentInformation(context)
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                consentResolved = true
                canRequestAds = consent.canRequestAds()
                _canRequestAdsState.value = canRequestAds
                if (canRequestAds) initializeAdsIfAllowed()
                onDone()
            }
        }, {
            consentResolved = true
            canRequestAds = consent.canRequestAds()
            _canRequestAdsState.value = canRequestAds
            if (canRequestAds) initializeAdsIfAllowed()
            onDone()
        })
    }

    fun privacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            val consent = UserMessagingPlatform.getConsentInformation(context)
            canRequestAds = consent.canRequestAds()
            _canRequestAdsState.value = canRequestAds
            if (canRequestAds) {
                initializeAdsIfAllowed()
            } else {
                interstitial = null
                adFreeRewarded = null
                premiumRewarded = null
                appOpen = null
                interstitialLoaded = false
                rewardedLoaded = false
                appOpenLoaded = false
            }
        }
    }

    @Synchronized
    private fun initializeAdsIfAllowed() {
        if (!canRequestAds) return
        if (!mobileAdsInitialized) {
            mobileAdsInitialized = true
            MobileAds.initialize(context) {}
        }
        preload()
    }

    fun preload() {
        if (!canRequestAds) return
        InterstitialAd.load(context, AdUnits.TRANSFER_INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; interstitialLoaded = true }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitialLoaded = false }
        })
        RewardedAd.load(context, AdUnits.AD_FREE_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { adFreeRewarded = ad; rewardedLoaded = true }
            override fun onAdFailedToLoad(error: LoadAdError) { rewardedLoaded = false }
        })
        RewardedAd.load(context, AdUnits.PREMIUM_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { premiumRewarded = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { }
        })
        AppOpenAd.load(context, AdUnits.APP_OPEN, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) { appOpen = ad; appOpenLoaded = true }
            override fun onAdFailedToLoad(error: LoadAdError) { appOpenLoaded = false }
        })
    }

    fun showAdFreeRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = adFreeRewarded
        if (!canRequestAds || ad == null) return
        adFreeRewarded = null
        rewardedLoaded = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload() }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) { preload() }
        }
        ad.show(activity) { onReward() }
    }

    fun showPremiumRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = premiumRewarded
        if (!canRequestAds || ad == null) return
        premiumRewarded = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload() }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) { preload() }
        }
        ad.show(activity) { onReward() }
    }

    fun maybeShowAppOpen(activity: Activity, awayMs: Long) {
        if (!canRequestAds || appOpenShownThisSession || awayMs < 4L * 60L * 60L * 1000L) return
        val ad = appOpen ?: return
        appOpenShownThisSession = true
        appOpen = null
        appOpenLoaded = false
        ad.show(activity)
    }
}
