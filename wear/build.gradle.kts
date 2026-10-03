plugins {
    id("com.android.application")
}

android {
    namespace = "com.togg.companion.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.togg.companion"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.0")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:guava:31.1-android")
}
