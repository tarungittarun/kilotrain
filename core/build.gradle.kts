plugins {
    alias(libs.plugins.kotlin.jvm)
}

// core = the v2 pure-JVM layers (KICKOFF 6.1-6.5, 9.2): NO android.* imports, NO HTTP, NO DB.
// The Android module enforces that at compile time by depending on this module only.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
