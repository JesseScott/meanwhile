import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    id("com.google.android.gms.oss-licenses-plugin")
}

// Firebase (analytics and crash reports) needs app/google-services.json from your own Firebase project. It is not
// committed. Without it the build still works and the app simply collects nothing.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// The upload key for Google Play. keystore.properties (gitignored, in the repo root) says where it is:
//   storeFile=../../secrets/meanwhile-upload.jks
//   storePassword=...
//   keyAlias=upload
//   keyPassword=...
// Without it, release builds are produced unsigned (fine for checking that they build; Play needs them signed).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The version comes from version.properties, so the AAB's file name always matches what is inside it
// (app/build/outputs/bundle/release/meanwhile-0.1.0-1-release.aab). Bump versionCode (in version.properties) for every Play upload.
val versionProperties = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val appVersionName: String = versionProperties.getProperty("versionName")
val appVersionCode: Int = versionProperties.getProperty("versionCode").toInt()
base.archivesName.set("meanwhile-$appVersionName-$appVersionCode")

android {
    namespace = "tt.co.jesses.meanwhile"
    compileSdk = 36

    defaultConfig {
        applicationId = "tt.co.jesses.meanwhile"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlin.test)

    // Ktor's OkHttp engine pulls OkHttp 5.5, which needs compileSdk 37 (and so AGP 9). Hold it
    // back until the project moves to AGP 9.
    constraints {
        implementation("com.squareup.okhttp3:okhttp") {
            version { strictly("5.1.0") }
        }
    }

    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(libs.play.services.location)
    implementation(libs.play.services.oss.licenses)
    implementation(libs.datastore.preferences)
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
