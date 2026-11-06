import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val adProps = Properties().apply {
    rootProject.file("config/ads.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun adValue(key: String, test: String): String =
    (System.getenv("ADMOB_" + key) ?: adProps.getProperty(key))?.trim().takeUnless { it.isNullOrEmpty() } ?: test
val testAppId = "ca-app-pub-3940256099942544~3347511713"

android {
    namespace = "com.bqsqwad.share"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.bqsqwad.share"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "2").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        manifestPlaceholders["admobAppId"] = testAppId
    }
    signingConfigs {
        create("release") {
            val envFile = System.getenv("KEYSTORE_FILE")
            val propFile = rootProject.file("keystore.properties")
            val props = Properties()
            if (propFile.exists()) propFile.inputStream().use { props.load(it) }
            val store = envFile ?: props.getProperty("storeFile")
            if (!store.isNullOrBlank() && file(store).exists()) {
                storeFile = file(store)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: props.getProperty("storePassword")
                keyAlias = System.getenv("KEY_ALIAS") ?: props.getProperty("keyAlias")
                keyPassword = System.getenv("KEY_PASSWORD") ?: props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["admobAppId"] = adValue("APP_ID", testAppId)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = testAppId
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        val real = adValue("APP_ID", testAppId)
        if (real == testAppId && !project.hasProperty("allowTestAds")) {
            throw GradleException("Release blocked: set a real AdMob App ID in config/ads.properties or pass -PallowTestAds=true for a trial build.")
        }
        val requiredAdUnits = listOf(
            "HOME_BANNER", "HOME_NATIVE", "HISTORY_NATIVE", "SETTINGS_BANNER",
            "TABLET_SIDE_BANNER", "HELP_NATIVE", "TRANSFER_COMPLETE_INTERSTITIAL",
            "APP_OPEN", "OPTIONAL_REWARDED"
        )
        if (!project.hasProperty("allowTestAds")) {
            val missing = requiredAdUnits.filter { adValue(it, "").isBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException("Release blocked: missing AdMob unit IDs in config/ads.properties: ${missing.joinToString()}")
            }
        }
        val signing = android.signingConfigs.getByName("release")
        if (signing.storeFile == null || !signing.storeFile!!.exists() ||
            signing.storePassword.isNullOrBlank() || signing.keyAlias.isNullOrBlank() || signing.keyPassword.isNullOrBlank()) {
            throw GradleException("Release blocked: configure a complete release keystore.")
        }
    }
}
dependencies {
    implementation(libs.lifecycle.runtime.compose)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":core:ads"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    ksp(libs.hilt.compiler)
    implementation(libs.datastore)
    implementation(libs.work)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.nearby)
    implementation(libs.splash)
    implementation(libs.coil)
    implementation(libs.zxing)
    implementation(libs.review)
    implementation(libs.app.update)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
