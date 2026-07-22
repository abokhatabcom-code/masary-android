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

    val environmentUrls = mapOf(
        "development" to "https://dev.masary.app/",
        "staging" to "https://staging.masary.app/",
        "production" to "https://masary.app/",
    )
    val requestedEnvironment = providers.gradleProperty("masaryEnvironment").orNull
    if (requestedEnvironment != null) {
        require(requestedEnvironment in environmentUrls) {
            "masaryEnvironment must be one of: ${environmentUrls.keys.joinToString()}"
        }
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
            val environment = requestedEnvironment ?: "development"
            buildConfigField("String", "MASARY_ENVIRONMENT", "\"$environment\"")
            buildConfigField("String", "MASARY_API_BASE_URL", "\"${environmentUrls.getValue(environment)}\"")
            if (hasMasarySigning) {
                signingConfig = signingConfigs.getByName("masary")
            }
        }
        release {
            val environment = requestedEnvironment ?: "production"
            require(environment != "development") {
                "Release builds cannot target the development API"
            }
            buildConfigField("String", "MASARY_ENVIRONMENT", "\"$environment\"")
            buildConfigField("String", "MASARY_API_BASE_URL", "\"${environmentUrls.getValue(environment)}\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-models"))
    implementation(project(":core-ui"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-home"))
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
        ":feature-home:testDebugUnitTest",
        ":core-network:testDebugUnitTest",
        ":core-datastore:testDebugUnitTest",
        ":core-security:testDebugUnitTest",
    )
}
