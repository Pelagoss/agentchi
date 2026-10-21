plugins {
    alias(libs.plugins.android.application)
}

// Cadran Watch Face Format : un APK de ressources seules, sans code, à installer à côté de l'appli.
android {
    namespace = "fr.codingpacer.agentchi.watchface"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.codingpacer.agentchi.watchface"
        // Watch Face Format : Wear OS 4 et plus récent.
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Le Play Store refuse le code dans un cadran : R8 retire la classe R générée, seule présente.
        release {
            isMinifyEnabled = true
        }
    }
}
