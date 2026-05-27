import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.jlleitschuh.ktlint)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(keystorePropertiesFile.inputStream())
        }
    }
val devBuildSha = rootProject.findProperty("DEV_BUILD_SHA")?.toString()?.take(7)

android {
    namespace = "com.jotty.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jotty.android"
        minSdk = 26
        targetSdk = 36
        versionCode = (rootProject.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: 1)
        versionName = rootProject.findProperty("VERSION_NAME")?.toString() ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    val devVersionSuffix = if (!devBuildSha.isNullOrBlank()) "-dev+$devBuildSha" else null

    buildTypes {
        getByName("debug") {
            devVersionSuffix?.let { versionNameSuffix = it }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            devVersionSuffix?.let { versionNameSuffix = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val copyChangelogToAssets =
    tasks.register<Copy>("copyChangelogToAssets") {
        from(rootProject.file("CHANGELOG.md"))
        into(layout.projectDirectory.dir("src/main/assets"))
    }

tasks.named("preBuild") {
    dependsOn(copyChangelogToAssets)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ktlint {
    android.set(true)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // Retrofit for REST API
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-gson:2.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Secure storage for API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room for offline storage
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Markdown rendering for notes (images via Coil)
    implementation("com.github.jeziellago:compose-markdown:0.5.8")
    implementation("io.coil-kt:coil:2.6.0")

    // Encryption: Argon2 + XChaCha20 (for Jotty encrypted notes)
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    // Biometric authentication (note passphrase protection)
    implementation("androidx.biometric:biometric:1.1.0")
    // FragmentActivity is required by BiometricPrompt; declared explicitly to avoid relying on transitive resolution.
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.room:room-testing:2.7.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
