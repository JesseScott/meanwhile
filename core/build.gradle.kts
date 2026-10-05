plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.ktor.client.core)
    api(libs.datastore.preferences.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    // Only for LiveFeedsCheck, which runs the real RSS source over the real network with the engine the app uses.
    testImplementation(libs.ktor.client.okhttp)
}

// Pass -DliveFeeds=<csv> to run LiveFeedsCheck; without it that test does nothing.
tasks.withType<Test>().configureEach {
    System.getProperty("liveFeeds")?.let { systemProperty("liveFeeds", it) }
}
