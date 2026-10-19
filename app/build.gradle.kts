plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    // 1. ضفنا مكوّن فحص ثغرات المكتبات هنا
    id("org.owasp.dependencycheck") version "12.1.0"
}

// Resolve the same compiler/runtime used by the project instead of relying on
// a runner-specific Gradle installation or cache layout.
listOf("verifyTrashRepository" to "scripts/test_trash_repository.py",
    "verifyPhotoIndexWorker" to "scripts/test_photo_index_worker.py").forEach { (taskName, script) ->
    tasks.register<Exec>(taskName) {
        group = "verification"
        description = "Run production repository/worker regressions with JVM boundary doubles"
        workingDir(rootDir)
        doFirst {
            val runtime = configurations.getByName("debugUnitTestRuntimeClasspath").files
            val stdlib = runtime.single { it.name.matches(Regex("kotlin-stdlib-[0-9].*\\.jar")) }
            val coroutines = runtime.single { it.name.startsWith("kotlinx-coroutines-core-jvm-") && it.extension == "jar" }
            commandLine("python3", script,
                "--compiler-classpath", configurations.getByName("kotlinCompilerClasspath").asPath,
                "--stdlib", stdlib.absolutePath, "--coroutines", coroutines.absolutePath)
        }
    }
}

apply(plugin = "io.objectbox")

android {
    namespace = "com.omnimemoria"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omnimemoria"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        resValue("string", "build_revision", System.getenv("GITHUB_SHA")?.take(12) ?: "local")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    lint {
        abortOnError = false
        baseline = file("lint-baseline.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Installable optimized build for device testing without a production key.
    buildTypes.create("preview") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        versionNameSuffix = "-preview"
        matchingFallbacks += listOf("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        compose = true
    }

    // Native OCR and image codecs are large. Ship a complete APK per supported
    // CPU instead of making each device download four copies of native code.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.matching { it.name.contains("debug", ignoreCase = true) }.all {
    exclude(group = "io.objectbox", module = "objectbox-android")
}

// 2. إعدادات اختيارية لتوليد التقرير بصيغة SARIF المتوافقة مع الـ Pipeline عندك
dependencyCheck {
    format = "SARIF"
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.4.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.41")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    debugImplementation(libs.objectbox.objectbrowser)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.accompanist.permissions)
    implementation(libs.telephoto.zoomable.image.coil3)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.google.dagger:hilt-android-testing:2.57")
    kspTest(libs.hilt.compiler)
    kspTest(libs.androidx.hilt.compiler)
    testImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
