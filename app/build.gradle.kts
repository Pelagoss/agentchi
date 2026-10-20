import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signature de publication (Play Store) : keystore/release.properties, non versionné,
// ou variables d'environnement RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD.
val releaseProps = Properties().apply {
    rootProject.file("keystore/release.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun releaseProp(name: String): String? =
    releaseProps.getProperty(name) ?: System.getenv("RELEASE_" + name.replace(Regex("([A-Z])"), "_$1").uppercase())

android {
    namespace = "com.claudecompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.claudecompanion"
        // Wear OS 3 (Galaxy Watch4 et plus récentes)
        minSdk = 30
        targetSdk = 34
        // Montre et téléphone partagent la fiche Play Store : codes de version distincts.
        versionCode = 2_000_001
        versionName = "0.1.0"

        // Serveur Claude Companion proposé par défaut (modifiable sur la montre) :
        // ./gradlew -PcompanionServer=https://cc.example.fr … ou variable COMPANION_SERVER.
        val companionServer = (findProperty("companionServer") as String?) ?: System.getenv("COMPANION_SERVER") ?: ""
        buildConfigField("String", "COMPANION_SERVER", "\"${companionServer.trimEnd('/')}\"")
    }

    signingConfigs {
        // Clé partagée avec l'appli téléphone : obligatoire pour la Wearable Data Layer.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseProp("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(releaseProp("storeFile")!!)
                storePassword = releaseProp("storePassword")
                keyAlias = releaseProp("keyAlias")
                keyPassword = releaseProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.wear.remote.interactions)
    implementation(libs.listenablefuture)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
