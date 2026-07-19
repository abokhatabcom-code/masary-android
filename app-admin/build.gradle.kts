plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.masary.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.masary.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    val masarySigningValues = listOf(
        System.getenv("MASARY_KEYSTORE_PATH"),
        System.getenv("MASARY_KEYSTORE_PASSWORD"),
        System.getenv("MASARY_KEY_ALIAS"),
        System.getenv("MASARY_KEY_PASSWORD"),
    )
    val hasMasarySigning = masarySigningValues.all { !it.isNullOrBlank() }
    check(hasMasarySigning || masarySigningValues.all { it.isNullOrBlank() }) {
        "Incomplete Masary signing configuration"
    }

    signingConfigs {
        if (hasMasarySigning) {
            create("masary") {
                storeFile = file(requireNotNull(masarySigningValues[0]))
                storePassword = masarySigningValues[1]
                keyAlias = masarySigningValues[2]
                keyPassword = masarySigningValues[3]
            }
        }
    }

    buildTypes {
        debug {
            if (hasMasarySigning) {
                signingConfig = signingConfigs.getByName("masary")
            }
        }
        release {
            if (hasMasarySigning) {
                signingConfig = signingConfigs.getByName("masary")
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
    implementation(project(":core-models"))
    implementation(project(":core-network"))
    implementation(project(":core-security"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
