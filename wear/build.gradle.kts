import java.util.Properties

// The Wear OS app: the native engine and its Android player on the watch (no WebView there), with
// the account's session and the list of playlists asked to the phone's app over the Wearable Data
// Layer. Same application id and signature as the phone's app: the Data Layer only links those.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Match :app's signing inputs and precedence. Resolve a relative CI path from base/app, as :app does.
val envStore = providers.environmentVariable("MYSPOTI_KEYSTORE").orNull
val localSigning = rootProject.layout.projectDirectory.file("keystore/keystore.properties")
val localSigningBytes = providers.fileContents(localSigning).asBytes.orNull
val debugKeystoreFile = rootProject.layout.projectDirectory.file("keystore/debug.keystore")
val hasReleaseKey = envStore != null || localSigningBytes != null
val automixLab = providers.gradleProperty("automixLab").isPresent
val overDev = providers.gradleProperty("overDev").isPresent

android {
    namespace = "app.myspoti.wear"
    compileSdk = 37

    val version = (findProperty("appVersionName") as String?) ?: "1.0.4"
    defaultConfig {
        applicationId = if (automixLab) "app.revera.automixlab" else "app.revera.dev"
        minSdk = 30
        targetSdk = 36
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 204
        versionName = version
    }
    signingConfigs {
        if (debugKeystoreFile.asFile.exists()) {
            getByName("debug") {
                storeFile = debugKeystoreFile.asFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        create("release") {
            if (envStore != null) {
                storeFile = rootProject.file("app").resolve(envStore)
                storePassword = providers.environmentVariable("MYSPOTI_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("MYSPOTI_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("MYSPOTI_KEY_PASSWORD").orNull
            } else if (localSigningBytes != null) {
                val properties = Properties().apply { localSigningBytes.inputStream().use { load(it) } }
                storeFile = rootProject.file("keystore/${properties.getProperty("storeFile")}")
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":engine-android"))
    // The listen mirror on the watch: the sync's rules on the native engine (EngineSyncPage).
    implementation(project(":api-spotify"))
    // The phone's extensions run on the watch too, and the Wi-Fi link to the phone.
    implementation(project(":extensions"))
    implementation(project(":pass"))
    // A list played from the extensions: a plain player (DASH for the manifests some give).
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    // Wear's Material 3 (scaffold, pager, lists, time text) and Google's Horologist media UI on it
    // (Apache-2.0): the player's controls, progress ring, artwork background and volume.
    implementation("androidx.wear.compose:compose-material3:1.7.1")
    implementation("com.google.android.horologist:horologist-media-ui-material3:0.8.4-alpha")
    implementation("com.google.android.horologist:horologist-audio-ui-material3:0.8.4-alpha")
    implementation("com.google.android.horologist:horologist-compose-layout:0.8.4-alpha")
    implementation("com.google.android.horologist:horologist-images-coil:0.8.4-alpha")
    // Refresh only; the two other icons are drawn in WatchIcons (the extended set is ~60 MB).
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    // Screenshots of the watch screen on the computer (WatchScreenshotTest): Robolectric draws them, Roborazzi saves them.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
