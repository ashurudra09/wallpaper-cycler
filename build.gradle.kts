buildscript {
    dependencies {
        // AGP 9's built-in Kotlin support defaults to its own minimum Kotlin Gradle Plugin
        // version; pin it explicitly so it matches the Compose/serialization plugins below.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
