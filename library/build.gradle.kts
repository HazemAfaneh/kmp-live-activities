import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.hazemafaneh.liveactivities"
version = "0.1.1"

kotlin {
    explicitApi()

    compilerOptions {
        // The library API intentionally exposes `expect object LiveActivityManager`.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "io.github.hazemafaneh.liveactivities"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.startup.runtime)
            implementation(libs.androidx.datastore.preferences)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "live-activities", version.toString())

    pom {
        name = "KMP Live Activities"
        description = "A Kotlin Multiplatform library exposing iOS Live Activities and " +
            "Android Live Updates behind a single unified API."
        inceptionYear = "2026"
        url = "https://github.com/hazemafaneh/kmp-live-activities/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "hazemafaneh"
                name = "Hazem Afaneh"
                url = "https://github.com/hazemafaneh/"
            }
        }
        scm {
            url = "https://github.com/hazemafaneh/kmp-live-activities/"
            connection = "scm:git:git://github.com/hazemafaneh/kmp-live-activities.git"
            developerConnection = "scm:git:ssh://git@github.com/hazemafaneh/kmp-live-activities.git"
        }
    }
}
