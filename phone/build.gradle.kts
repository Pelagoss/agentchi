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
    namespace = "com.claudecompanion.phone"
    compileSdk = 35

    defaultConfig {
        // Même identifiant que l'appli montre : requis par la Wearable Data Layer.
        applicationId = "com.claudecompanion"
        minSdk = 26
        targetSdk = 35
        // Montre et téléphone partagent la fiche Play Store : codes de version distincts.
        versionCode = 1_000_001
        versionName = "0.1.0"
    }

    signingConfigs {
        // Même clé que l'appli montre : requis par la Wearable Data Layer.
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
