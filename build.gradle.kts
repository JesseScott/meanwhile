buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Generates the open source license list shown by OssLicensesMenuActivity.
        classpath(libs.google.oss.licenses.plugin)
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}
