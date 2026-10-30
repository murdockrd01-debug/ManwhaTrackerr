plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.manhwatracker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.manhwatracker.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 30
        versionName = "3.9"
    }

    // Clave fija: así cada APK nuevo se instala encima del anterior sin desinstalar.
    signingConfigs {
        create("shared") {
            // En GitHub la llave llega por secretos (variables de entorno). Sin ellas se usa el archivo del repositorio.
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "manhwa.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "manhwa"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Base de datos local
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Portadas
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Leer el backup de Komikku/Mihon (.tachibk = gzip + protobuf)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.1")

    // Leer texto de imágenes (en el teléfono, sin internet)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
