import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// ---- 签名配置 ----
// 正式发布密钥信息从 android/keystore.properties 读取（该文件含口令，已被 .gitignore 排除）。
// 克隆仓库者拿不到该文件，此时 release 产物不签名但仍可构建，便于只做编译校验。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun keystoreProp(key: String): String? = keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

// 本地 debug 密钥：仓库内 .keystore/ 已被 gitignore，克隆者不存在该文件。
// 仅在文件存在时才覆盖默认配置，否则交给 AGP 回退到 SDK 自动生成的 ~/.android/debug.keystore，
// 避免 "Keystore file not found for signing config 'debug'" 导致克隆后无法构建。
val localDebugKeystore = rootProject.file("../.keystore/debug.keystore")

// 构建输出目录：默认使用系统临时目录，避免在仓库中写死本机路径；
// 但 KSP 无法处理与工程跨盘符的输出目录（IllegalArgumentException: this and base files have different roots），
// 跨盘符时回退为模块内默认 build/（已在 .gitignore 忽略）。
// 需要固定输出位置时用 -Plinkfetch.buildDir=<绝对路径> 覆盖
val linkfetchBuildDir: String = (project.findProperty("linkfetch.buildDir") as String?)
    ?: run {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "LinkFetchBuild/app")
        val sameRoot = tmpDir.canonicalFile.toPath().root == project.projectDir.canonicalFile.toPath().root
        if (sameRoot) tmpDir.absolutePath else "build"
    }
buildDir = file(linkfetchBuildDir)

android {
    namespace = "com.linkfetch.app"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.linkfetch.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 28
        versionName = "1.7.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 注意：signingConfigs 必须声明在 buildTypes 之前——buildTypes.release 的配置块是
    // 立即执行的，若在其后才 create("release")，release 会拿不到签名配置。
    signingConfigs {
        // 正式发布密钥：仅当 keystore.properties 提供 storeFile 时创建
        keystoreProp("storeFile")?.let { path ->
            create("release") {
                storeFile = rootProject.file(path)
                storePassword = keystoreProp("storePassword")
                keyAlias = keystoreProp("keyAlias")
                keyPassword = keystoreProp("keyPassword")
                // minSdk >= 24 无需 v1（JAR）签名；显式开启 v3，
                // 为将来更换密钥时的 signing lineage 平滑迁移留出余地。
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        // Debug 签名：仓库内 keystore 存在时优先使用（保证本机各版本可互相覆盖安装）
        if (localDebugKeystore.exists()) {
            getByName("debug") {
                storeFile = localDebugKeystore
            }
        }
    }

    buildTypes {
        release {
            // 开启 R8 代码压缩 + 资源收缩，显著减小 APK 体积
            isMinifyEnabled = true
            isShrinkResources = true
            // 使用正式发布密钥（android/keystore.properties）
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            } else {
                logger.warn(
                    "未找到 android/keystore.properties，release 产物将不签名。" +
                        "如需生成可发布的 APK，请先配置发布密钥。",
                )
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // lint 不再全局关闭：它能发现明文流量、权限缺失、资源泄漏、PendingIntent 标志位误用等问题。
    // 存量问题用 baseline 一次性收口，之后新增问题会让 lint 任务失败。
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = false
        // 不在 assembleRelease 中跑 lint：AGP 7.4.2 在 isShrinkResources=true 时，
        // lint 的 release 变体会因找不到 merged-not-compiled-resources 下的资源而抛
        // "Unable to locate resourceFile ... in source-sets"（已知 bug，清理缓存无效）。
        // 因此 lint 作为独立门禁执行：./gradlew :app:lintDebug（CI 中应纳入）。
        checkReleaseBuilds = false
        // 依赖版本更新提示噪音较大，不纳入门禁
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.5.3")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    implementation("androidx.room:room-runtime:2.5.1")
    implementation("androidx.room:room-ktx:2.5.1")
    ksp("androidx.room:room-compiler:2.5.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Exif 解析：平台自带的 android.media.ExifInterface 读不出 WebP / HEIF 的方向信息，
    // 而 Live 图封面在合成 Motion Photo 前需要按 EXIF 校正方向。
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.media3:media3-exoplayer:1.0.2")
    implementation("androidx.media3:media3-ui:1.0.2")

    implementation("io.coil-kt:coil-compose:2.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}
