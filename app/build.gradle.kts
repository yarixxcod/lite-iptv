plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "player.vvvion.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "player.vvvion.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("ciRelease") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (!System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfigs.getByName("ciRelease")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,LICENSE-notice.md}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = false
        disable += setOf(
            "ObsoleteLintCustomCheck",
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
            "MonochromeLauncherIcon",
            "IconLauncherShape",
            "ConvertToWebp",
            "UseTomlInstead",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

base.archivesName.set("VvvionIPTV")

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.media3.common.util.UnstableApi")
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.window.size)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.dash)
    implementation(libs.androidx.media3.rtsp)
    implementation(libs.androidx.media3.okhttp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.work)
    implementation(libs.androidx.datastore)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coil)
    implementation(libs.zxing)
    implementation(libs.xz)
    implementation(libs.coroutines)

    testImplementation(libs.junit)
}
