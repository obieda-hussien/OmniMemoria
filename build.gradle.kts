plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://maven.objectbox.io")
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:4.2.0")
    }
}
