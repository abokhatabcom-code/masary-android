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

rootProject.name = "MasaryEducational"
include(
    ":app-student",
    ":app-admin",
    ":app-pos",
    ":core-ui",
    ":core-models",
    ":core-network",
    ":core-security",
)
