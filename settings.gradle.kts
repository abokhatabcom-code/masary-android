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
    ":core-datastore",
    ":core-local",
    ":feature-auth",
    ":feature-home",
    ":feature-subjects",
    ":feature-subject",
    ":feature-training-center",
    ":feature-activity-preparation",
    ":feature-notifications",
)
