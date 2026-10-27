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
            // 作者 long：没有独立发布身份时只生成未签名 Release，避免把 debug 信任链误当成正式发布包。
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        // 作者 long：Flutter 参考工程已有的 GeoIP/GeoSite 数据作为 Android 首次启动的离线核心资源，
        // 避免 geo-auto-update 在 VPN 尚未建立前依赖外网下载而阻断配置应用。
        getByName("main").assets.srcDir(rootProject.file("assets/data"))
        // 作者 long：AGP 9 的内置 Kotlin 只从 Kotlin 源集读取这些跨目录单元测试。
        getByName("test").kotlin.srcDirs("../tests/app")
        // 作者 long：Room DAO 只有在 Android SQLite 实现上才能验证，单独接入真机 instrumentation 源集。
        getByName("androidTest").kotlin.srcDirs("../tests/instrumented")
        // 作者 long：Compose 测试运行在独立 APK 中，必须把 Android 与测试协程的 ServiceLoader 条目一起打入该 APK。
        getByName("androidTest").resources.srcDirs("../tests/instrumented/resources")
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // 作者 long：Compose instrumentation 会同时加载 Android 协程实现和测试调度器，合并 ServiceLoader 条目才能让两套实现都可见。
        resources.merges += "/META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler"
        resources.merges += "/META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"
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
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // 作者 long：Compose instrumentation 的测试协程需要和 Debug 应用共享 ServiceLoader 平台 handler，Release 不携带测试工件。
    debugImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidxTestCoreLib)
    androidTestImplementation(libs.androidxTestRunnerLib)
    androidTestImplementation(libs.androidxTestExtJunitLib)
    // 作者 long：Activity 宿主测试需要在 Android 13+ 启动前授予通知权限，避免权限对话框遮挡 Compose 页面断言。
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation(libs.kotlinx.coroutines.android)
    // 作者 long：Android 16 已移除旧版 InputManager.getInstance()，Espresso 3.7.0 改用 Context.getSystemService()。
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
