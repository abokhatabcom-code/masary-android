import java.net.URI

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
        versionCode = 1201
        versionName = "1.12.1"
    }

    fun injectedUrl(property: String, variable: String, fallback: String): String =
        providers.gradleProperty(property).orNull?.takeIf(String::isNotBlank)
            ?: providers.environmentVariable(variable).orNull?.takeIf(String::isNotBlank)
            ?: fallback

    val productionUrl = "https://masary.app/"
    val environmentUrls = mapOf(
        "development" to injectedUrl(
            "masaryDevelopmentBaseUrl",
            "MASARY_DEVELOPMENT_BASE_URL",
            "https://development.masary.invalid/",
        ),
        "staging" to injectedUrl(
            "masaryStagingBaseUrl",
            "MASARY_STAGING_BASE_URL",
            "https://staging.masary.invalid/",
        ),
        "production" to productionUrl,
    )
    environmentUrls.forEach { (environment, baseUrl) ->
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && baseUrl.endsWith("/")) {
            "$environment API Base URL must use HTTPS and end with /"
        }
    }
    val requestedEnvironment = providers.gradleProperty("masaryEnvironment").orNull?.takeIf(String::isNotBlank)
    if (requestedEnvironment != null) {
        require(requestedEnvironment in environmentUrls) {
            "masaryEnvironment must be one of: ${environmentUrls.keys.joinToString()}"
        }
    }
    fun firebaseValue(environment: String, key: String): String =
        providers.gradleProperty("masaryFirebase${environment.replaceFirstChar(Char::uppercase)}$key").orNull?.trim().orEmpty()
    val firebase = environmentUrls.keys.associateWith { environment ->
        listOf("ProjectId", "ApplicationId", "ApiKey", "GcmSenderId").associateWith { firebaseValue(environment, it) }
    }
    firebase.forEach { (environment, values) ->
        check(values.values.all(String::isBlank) || values.values.none(String::isBlank)) {
            "Firebase $environment configuration must be complete or absent"
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val environment = requestedEnvironment ?: "development"
            val environmentUrl = environmentUrls.getValue(environment)
            require(
                environment != "production" &&
                    URI(environmentUrl).host != URI(productionUrl).host
            ) {
                "Debug builds cannot target the production API"
            }
            buildConfigField("String", "MASARY_ENVIRONMENT", "\"$environment\"")
            buildConfigField("String", "MASARY_API_BASE_URL", "\"$environmentUrl\"")
            check(environment != "production") { "Debug cannot use production Firebase" }
            firebase.getValue(environment).forEach { (key, value) -> buildConfigField("String", "FIREBASE_${key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()}", "\"$value\"") }
            if (hasMasarySigning) {
                signingConfig = signingConfigs.getByName("masary")
            }
        }
        create("preview") {
            isDebuggable = true
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            buildConfigField("String", "MASARY_ENVIRONMENT", "\"production\"")
            buildConfigField("String", "MASARY_API_BASE_URL", "\"$productionUrl\"")
            firebase.getValue("production").forEach { (key, value) ->
                buildConfigField(
                    "String",
                    "FIREBASE_${key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()}",
                    "\"$value\"",
                )
            }
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            val environment = requestedEnvironment ?: "production"
            require(environment != "development") {
                "Release builds cannot target the development API"
            }
            buildConfigField("String", "MASARY_ENVIRONMENT", "\"$environment\"")
            buildConfigField("String", "MASARY_API_BASE_URL", "\"${environmentUrls.getValue(environment)}\"")
            firebase.getValue(environment).forEach { (key, value) -> buildConfigField("String", "FIREBASE_${key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()}", "\"$value\"") }
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
    implementation(project(":feature-subjects"))
    implementation(project(":feature-subject"))
    implementation(project(":feature-training-center"))
    implementation(project(":feature-activity-preparation"))
    implementation(project(":feature-notifications"))
    implementation(project(":core-datastore"))
    implementation(project(":core-security"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(
        ":feature-auth:testDebugUnitTest",
        ":feature-home:testDebugUnitTest",
        ":feature-subjects:testDebugUnitTest",
        ":feature-subject:testDebugUnitTest",
        ":feature-training-center:testDebugUnitTest",
        ":feature-activity-preparation:testDebugUnitTest",
        ":core-network:testDebugUnitTest",
        ":core-datastore:testDebugUnitTest",
        ":core-security:testDebugUnitTest",
    )
}
