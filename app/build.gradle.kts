import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing material lives outside version control in keystore.properties (see .gitignore).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val keystoreEntries = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val missingKeystoreEntries = keystoreEntries.filter {
    keystoreProperties.getProperty(it).isNullOrBlank()
}
val hasReleaseKeystore = keystorePropertiesFile.exists() && missingKeystoreEntries.isEmpty()

android {
    namespace = "dk.ftb.imageduplicationchecker"
    compileSdk = 36

    defaultConfig {
        applicationId = "dk.ftb.imageduplicationchecker"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Release packaging fails loudly when the keystore is absent or incomplete, rather than quietly
// emitting an unsigned APK. Debug builds never need it, so they don't pay for this check.
gradle.taskGraph.whenReady {
    if (hasReleaseKeystore) return@whenReady
    val packagingRelease = allTasks.any {
        it.name.startsWith("package") && it.name.contains("Release")
    }
    if (!packagingRelease) return@whenReady
    val reason = if (!keystorePropertiesFile.exists()) {
        "keystore.properties not found at ${keystorePropertiesFile.path}"
    } else {
        "keystore.properties is missing ${missingKeystoreEntries.joinToString(", ")}"
    }
    throw GradleException("Cannot build a signed release: $reason")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.preference.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
