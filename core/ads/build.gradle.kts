import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val adProps = Properties().apply {
    rootProject.file("config/ads.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun adValue(key: String, test: String): String =
    (System.getenv("ADMOB_" + key) ?: adProps.getProperty(key))?.trim().takeUnless { it.isNullOrEmpty() } ?: test

val testBanner = "ca-app-pub-3940256099942544/9214589741"
val testNative = "ca-app-pub-3940256099942544/2247696110"
val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
val testRewarded = "ca-app-pub-3940256099942544/5224354917"
val testAppOpen = "ca-app-pub-3940256099942544/9257395921"

android {
    namespace = "com.bqsqwad.share.ads"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "AD_BANNER", "\""+adValue("BANNER", testBanner)+"\"")
        buildConfigField("String", "AD_NATIVE", "\""+adValue("NATIVE", testNative)+"\"")
        buildConfigField("String", "AD_INTERSTITIAL", "\""+adValue("INTERSTITIAL", testInterstitial)+"\"")
        buildConfigField("String", "AD_REWARDED", "\""+adValue("REWARDED", testRewarded)+"\"")
        buildConfigField("String", "AD_APP_OPEN", "\""+adValue("APP_OPEN", testAppOpen)+"\"")
        buildConfigField("String", "AD_HOME_BANNER", "\""+adValue("HOME_BANNER", testBanner)+"\"")
        buildConfigField("String", "AD_HOME_NATIVE", "\""+adValue("HOME_NATIVE", testNative)+"\"")
        buildConfigField("String", "AD_HISTORY_NATIVE", "\""+adValue("HISTORY_NATIVE", testNative)+"\"")
        buildConfigField("String", "AD_SETTINGS_BANNER", "\""+adValue("SETTINGS_BANNER", testBanner)+"\"")
        buildConfigField("String", "AD_TABLET_SIDE_BANNER", "\""+adValue("TABLET_SIDE_BANNER", testBanner)+"\"")
        buildConfigField("String", "AD_HELP_NATIVE", "\""+adValue("HELP_NATIVE", testNative)+"\"")
        buildConfigField("String", "AD_TRANSFER_COMPLETE_INTERSTITIAL", "\""+adValue("TRANSFER_COMPLETE_INTERSTITIAL", testInterstitial)+"\"")
        buildConfigField("String", "AD_OPTIONAL_REWARDED", "\""+adValue("OPTIONAL_REWARDED", testRewarded)+"\"")
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "AD_BANNER", "\""+testBanner+"\"")
            buildConfigField("String", "AD_NATIVE", "\""+testNative+"\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\""+testInterstitial+"\"")
            buildConfigField("String", "AD_REWARDED", "\""+testRewarded+"\"")
            buildConfigField("String", "AD_APP_OPEN", "\""+testAppOpen+"\"")
            buildConfigField("String", "AD_HOME_BANNER", "\""+testBanner+"\"")
            buildConfigField("String", "AD_HOME_NATIVE", "\""+testNative+"\"")
            buildConfigField("String", "AD_HISTORY_NATIVE", "\""+testNative+"\"")
            buildConfigField("String", "AD_SETTINGS_BANNER", "\""+testBanner+"\"")
            buildConfigField("String", "AD_TABLET_SIDE_BANNER", "\""+testBanner+"\"")
            buildConfigField("String", "AD_HELP_NATIVE", "\""+testNative+"\"")
            buildConfigField("String", "AD_TRANSFER_COMPLETE_INTERSTITIAL", "\""+testInterstitial+"\"")
            buildConfigField("String", "AD_OPTIONAL_REWARDED", "\""+testRewarded+"\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core:design"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines)
    implementation(libs.datastore)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.admob)
    implementation(libs.ump)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
