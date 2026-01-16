plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    kotlin("plugin.serialization") version "1.9.22"
    jacoco
}

android {
    namespace = "com.healthtracker"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.healthtracker"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        unitTests.all {
            it.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*\$ViewInjector*.*",
        "**/*\$ViewBinder*.*",
        "**/databinding/*",
        "**/android/databinding/*",
        "**/androidx/databinding/*",
        "**/di/module/*",
        "**/*MapperImpl*.*",
        "**/*\$Lambda$*.*",
        "**/*Companion*.*",
        "**/*Module*.*",
        "**/*Dagger*.*",
        "**/*Hilt*.*",
        "**/*MembersInjector*.*",
        "**/*_Factory*.*",
        "**/*_Provide*Factory*.*",
        "**/*Extensions*.*"
    )
    
    val debugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    
    val mainSrc = "${project.projectDir}/src/main/kotlin"
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.buildDir) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

tasks.register("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    
    doLast {
        val reportFile = file("${project.buildDir}/reports/jacoco/jacocoTestReport/html/index.html")
        if (reportFile.exists()) {
            println("Coverage report: ${reportFile.absolutePath}")
        }
    }
}

dependencies {
    // Core Library Desugaring for Java 8+ APIs on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Project modules
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ml"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    // Health Connect
    implementation(libs.health.connect)

    // TensorFlow Lite
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)

    // ML Kit
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.face.detection)

    // CameraX
    implementation(libs.bundles.camerax)

    // Networking
    implementation(libs.bundles.networking)

    // Image Loading
    implementation(libs.coil.compose)

    // Animation
    implementation(libs.lottie.compose)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // Security
    implementation(libs.security.crypto)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Utilities
    implementation(libs.timber)
    implementation(libs.gson)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
}
