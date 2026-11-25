plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.azuredevid.cassy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.azuredevid.cassy"
        minSdk = 26
        targetSdk = 36
        // CI release builds pass these (android-release.yml); local builds keep 1 / 1.0.0.
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_API_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "PUBLIC_WEB_URL", "\"http://10.0.2.2:8088\"")
            buildConfigField("Boolean", "IS_DEMO_ENVIRONMENT", "true")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "BASE_API_URL", "\"https://api.abbdullaziz.online\"")
            buildConfigField("String", "PUBLIC_WEB_URL", "\"https://pos.abbdullaziz.online\"")
            buildConfigField("Boolean", "IS_DEMO_ENVIRONMENT", "true")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_API_URL", "\"https://api.abbdullaziz.online\"")
            buildConfigField("String", "PUBLIC_WEB_URL", "\"https://pos.abbdullaziz.online\"")
            buildConfigField("Boolean", "IS_DEMO_ENVIRONMENT", "false")
        }
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("ANDROID_KEYSTORE_PATH")
            val targetFile = when {
                !envPath.isNullOrBlank() -> file(envPath)
                project.rootProject.file("upload-keystore.jks").exists() -> project.rootProject.file("upload-keystore.jks")
                project.file("upload-keystore.jks").exists() -> project.file("upload-keystore.jks")
                else -> null
            }
            if (targetFile != null && targetFile.exists()) {
                storeFile = targetFile
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")?.trim()
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")?.trim()?.ifEmpty { "cassy-upload" } ?: "cassy-upload"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")?.trim()?.ifEmpty {
                    System.getenv("ANDROID_KEYSTORE_PASSWORD")?.trim()
                } ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")?.trim()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile != null && releaseConfig.storeFile!!.exists()) {
                signingConfig = releaseConfig
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.biometric:biometric:1.4.0-rc01")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Ktor 3 Client & Network Socket HAL (Port 9100)
    val ktorVersion = "3.0.1"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-network:$ktorVersion")

    // Koin Dependency Injection
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")

    // Security & Persistence: Google Tink AEAD (Android Keystore) + Jetpack DataStore
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.crypto.tink:tink-android:1.24.0")

    // Image Loading for WebP Menu Catalog from Cloudflare R2 CDN
    implementation("io.coil-kt:coil-compose:2.7.0")

    // CameraX & Barcode Scanner for Store Pairing QR
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // QR Code Generator (ZXing) for Dynamic Midtrans QRIS Bitmaps
    implementation("com.google.zxing:core:3.5.4")

    // Room Local Cache & Offline Cash Queue
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("io.insert-koin:koin-test:4.0.0")
    testImplementation("io.insert-koin:koin-test-junit4:4.0.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

