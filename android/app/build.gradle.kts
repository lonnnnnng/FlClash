import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.instrumentation.FramesComputationMode
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

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

// 作者 long：正式身份由本地密钥管理或CI注入，未配置时保持未签名产物，不借用上游或debug密钥。
val releaseStoreFile = providers.environmentVariable("KCLASH_RELEASE_KEYSTORE").orNull?.let(::file)
    ?: file("keystore.jks")
val releaseStorePassword = localProperties.getProperty("storePassword")
    ?: providers.environmentVariable("KCLASH_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = localProperties.getProperty("keyAlias")
    ?: providers.environmentVariable("KCLASH_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = localProperties.getProperty("keyPassword")
    ?: providers.environmentVariable("KCLASH_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = releaseStoreFile.exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

val defaultVersionCode = 1014
val defaultVersionName = "0.1.14"
// 作者 long：性能诊断显式启用独立包名的非Debug运行时；默认构建/正式Release不携带验收宿主。
val motionBenchmarkEnabled = providers.gradleProperty("kclashMotionBenchmark").orNull == "true"
val performanceBenchmarkEnabled = providers.gradleProperty("kclashPerformanceBenchmark").orNull == "true"
val menuTraceEnabled = providers.gradleProperty("kclashMenuTrace").orNull == "true"
val popupDisposeTraceEnabled = providers.gradleProperty("kclashPopupDisposeTrace").orNull == "true"
val requestedVersionCode = providers.gradleProperty("kclashVersionCode")
    .orNull
    ?.toIntOrNull()
    ?.also { require(it > 0) { "kclashVersionCode must be greater than zero" } }
    ?: defaultVersionCode
val requestedVersionName = providers.gradleProperty("kclashVersionName")
    .orNull
    ?.also {
        require(Regex("\\d+\\.\\d+\\.\\d+").matches(it)) {
            "kclashVersionName must use MAJOR.MINOR.PATCH format"
        }
    }
    ?: defaultVersionName

android {
    namespace = "com.lonnnnnng.flclash"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndkVersion.get()

    defaultConfig {
        applicationId = "com.lonnnnnng.flclash"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = requestedVersionCode
        versionName = requestedVersionName
        buildConfigField("boolean", "MENU_TRACE", "false")
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
        if (performanceBenchmarkEnabled) create("performanceBenchmark") {
            // 作者 long：真实页面性能使用完整R8和release库，独立包仅更换安装身份，避免Debug探针与测试框架污染内存。
            initWith(getByName("release"))
            applicationIdSuffix = ".performance"
            versionNameSuffix = "-performance"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            // 作者 long：定域追踪只进入显式诊断的独立R8包，正常性能分母及正式包不携带计数开销。
            buildConfigField("boolean", "MENU_TRACE", menuTraceEnabled.toString())
        }
        if (motionBenchmarkEnabled) create("motionBenchmark") {
            initWith(getByName("debug"))
            isDebuggable = false
            applicationIdSuffix = ".motion"
            versionNameSuffix = "-motion"
            matchingFallbacks += "debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    if (motionBenchmarkEnabled) testBuildType = "motionBenchmark"

    sourceSets {
        if (performanceBenchmarkEnabled) getByName("performanceBenchmark") {
            kotlin.srcDir("src/release/kotlin")
        }
        if (motionBenchmarkEnabled) getByName("motionBenchmark") {
            // 作者 long：同一个固定对象宿主用于Debug/非Debug对照，禁止另写简化动画冒充产品性能。
            kotlin.srcDir("src/debug/kotlin")
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
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

if (motionBenchmarkEnabled) configurations.named("motionBenchmarkImplementation") {
    extendsFrom(configurations.getByName("debugImplementation"))
}

// 作者 long：只给显式独立诊断变体插入同步计时，保留依赖方法和调用顺序；正式包及普通R8分母不改写Compose。
if (performanceBenchmarkEnabled && popupDisposeTraceEnabled) androidComponents {
    onVariants(selector().withBuildType("performanceBenchmark")) { variant ->
        variant.instrumentation.transformClassesWith(PopupDisposeTraceFactory::class.java, InstrumentationScope.ALL) {}
        variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
    }
}

abstract class PopupDisposeTraceFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData): Boolean = classData.className in setOf(
        "androidx.compose.ui.platform.AbstractComposeView", "androidx.compose.ui.platform.AndroidComposeView", "androidx.compose.ui.window.PopupLayout")
    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        object : ClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                val scope = when (classContext.currentClassData.className to name) {
                    "androidx.compose.ui.platform.AbstractComposeView" to "disposeComposition" -> "composition"
                    "androidx.compose.ui.platform.AndroidComposeView" to "removeConnectionToComposeViewContext" -> "input-connection"
                    "androidx.compose.ui.window.PopupLayout" to "dismiss" -> "window"
                    "androidx.compose.ui.window.PopupLayout" to "onDetachedFromWindow" -> "detach"
                    else -> return delegate
                }
                check(descriptor == "()V") { "Unexpected Compose disposal signature: $name$descriptor" }
                return object : AdviceAdapter(api, delegate, access, name, descriptor) {
                    private val start = Label()
                    override fun onMethodEnter() {
                        visitLdcInsn("kclash/dispose/$scope")
                        visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Trace", "beginSection", "(Ljava/lang/String;)V", false)
                        visitLabel(start)
                    }
                    override fun onMethodExit(opcode: Int) {
                        if (opcode != Opcodes.ATHROW) visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Trace", "endSection", "()V", false)
                    }
                    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
                        // 作者 long：异常也必须闭合trace再原样抛出，诊断不能吞异常或破坏后续线程切片栈。
                        val end = Label(); val handler = Label()
                        mv.visitLabel(end); mv.visitTryCatchBlock(start, end, handler, null); mv.visitLabel(handler)
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Trace", "endSection", "()V", false)
                        mv.visitInsn(Opcodes.ATHROW)
                        super.visitMaxs(maxStack, maxLocals)
                    }
                }
            }
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
    implementation("androidx.compose.material:material-icons-extended")
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
    implementation(libs.okhttp)
    // 作者 long：策略组图标复用同一图片管线，远程位图与 SVG 都保留加载失败的回退状态。
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-svg:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation(libs.zxing.core)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
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
