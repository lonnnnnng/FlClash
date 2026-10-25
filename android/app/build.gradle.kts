import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.android.legacy-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val releaseStoreFile = file("keystore.jks")
val releaseStorePassword = localProperties.getProperty("storePassword")
val releaseKeyAlias = localProperties.getProperty("keyAlias")
val releaseKeyPassword = localProperties.getProperty("keyPassword")
val hasReleaseSigning = releaseStoreFile.exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.lonnnnnng.flclash"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndkVersion.get()

    defaultConfig {
        applicationId = "com.lonnnnnng.flclash"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        // 作者 long：AGP 9 的内置 Kotlin 只从 Kotlin 源集读取这些跨目录单元测试。
        getByName("test").kotlin.srcDirs("../tests/app")
        // 作者 long：Room DAO 只有在 Android SQLite 实现上才能验证，单独接入真机 instrumentation 源集。
        getByName("androidTest").kotlin.srcDirs("../tests/instrumented")
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(project(":service"))
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(libs.androidx.core)
    implementation(libs.core.splashscreen)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidxTestCoreLib)
    androidTestImplementation(libs.androidxTestRunnerLib)
    androidTestImplementation(libs.androidxTestExtJunitLib)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
