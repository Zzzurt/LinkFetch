plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// 构建输出目录：默认使用系统临时目录，避免在仓库中写死本机路径；
// 需要固定输出位置时用 -Plinkfetch.buildDir=<绝对路径> 覆盖
val linkfetchBuildDir: String = (project.findProperty("linkfetch.buildDir") as String?)
    ?: (System.getProperty("java.io.tmpdir") + "/LinkFetchBuild/app")
buildDir = file(linkfetchBuildDir)

android {
    namespace = "com.linkfetch.app"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.linkfetch.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 19
        versionName = "1.6.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 发布版签名（与 debug 同 key，保证可安装；如有正式 keystore 可替换）
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // 本机离线环境：跳过 release 的 lint 校验（需要联网下载 lint 依赖）
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Debug 签名：使用工作区内的 keystore（默认 ~/.android/debug.keystore 在该环境被文件监视器锁定）
    signingConfigs {
        getByName("debug") {
            storeFile = file("../../.keystore/debug.keystore")
        }
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

    implementation("androidx.media3:media3-exoplayer:1.0.2")
    implementation("androidx.media3:media3-ui:1.0.2")

    implementation("io.coil-kt:coil-compose:2.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}
