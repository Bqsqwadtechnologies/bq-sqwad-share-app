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

android {
    namespace = "com.bqsqwad.share.ads"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "AD_BANNER", "\""+adValue("BANNER", "ca-app-pub-3940256099942544/9214589741")+"\"")
        buildConfigField("String", "AD_NATIVE", "\""+adValue("NATIVE", "ca-app-pub-3940256099942544/2247696110")+"\"")
        buildConfigField("String", "AD_INTERSTITIAL", "\""+adValue("INTERSTITIAL", "ca-app-pub-3940256099942544/1033173712")+"\"")
        buildConfigField("String", "AD_REWARDED", "\""+adValue("REWARDED", "ca-app-pub-3940256099942544/5224354917")+"\"")
        buildConfigField("String", "AD_APP_OPEN", "\""+adValue("APP_OPEN", "ca-app-pub-3940256099942544/9257395921")+"\"")
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "AD_BANNER", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "AD_NATIVE", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "AD_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_APP_OPEN", "\"ca-app-pub-3940256099942544/9257395921\"")
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
