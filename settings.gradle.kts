pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BQSqwadShare"
include(
    ":app",
    ":core:common",
    ":core:domain",
    ":core:data",
    ":core:design",
    ":core:ads",
)
