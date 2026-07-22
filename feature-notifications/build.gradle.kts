plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "app.masary.feature.notifications"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"\"")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}
dependencies {
    implementation(project(":core-network"))
    implementation(project(":core-security"))
    implementation(project(":core-models"))
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
