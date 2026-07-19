plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.masary.student"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.masary.student"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    val releaseSigningValues = listOf(
        System.getenv("STUDENT_KEYSTORE_PATH"),
        System.getenv("STUDENT_KEYSTORE_PASSWORD"),
        System.getenv("STUDENT_KEY_ALIAS"),
        System.getenv("STUDENT_KEY_PASSWORD"),
    )
    val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
    check(hasReleaseSigning || releaseSigningValues.all { it.isNullOrBlank() }) {
        "Incomplete student release signing configuration"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues[0]))
                storePassword = releaseSigningValues[1]
                keyAlias = releaseSigningValues[2]
                keyPassword = releaseSigningValues[3]
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":feature-auth"))
    implementation(project(":core-datastore"))
    implementation(project(":core-security"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(
        ":feature-auth:testDebugUnitTest",
        ":core-network:testDebugUnitTest",
        ":core-datastore:testDebugUnitTest",
        ":core-security:testDebugUnitTest",
    )
}
