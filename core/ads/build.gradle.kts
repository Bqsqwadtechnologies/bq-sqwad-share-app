plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.bqsqwad.share.ads"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
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
