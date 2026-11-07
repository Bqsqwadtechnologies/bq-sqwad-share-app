plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register<Exec>("generateBranding") {
    group = "branding"
    description = "Regenerate launcher, splash, notification and Play icons from branding/logo/logo.png"
    commandLine("python3", "$rootDir/scripts/generate_branding.py")
}
