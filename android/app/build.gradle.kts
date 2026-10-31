import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 正式版签名：keystore.properties 只存在于本机，不提交到 git。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.textbookgo.mobile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.textbookgo.mobile"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // 1.1.0 加入应用内阅读器；versionCode 递增，保证能覆盖 1.0.0（build 3）。
        versionCode = 4
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 默认关闭：只有 debug/verify 显式打开，正式包不出现测试入口。
        buildConfigField("boolean", "ALLOW_TEST_ENTRY", "false")

        // 用 Gitee contents API 而不是 raw：raw 有 CDN 缓存，发版后几分钟内可能读到旧清单。
        val updateManifestUrl = (project.findProperty("textbookgo.updateManifestUrl") as? String)
            ?: "https://gitee.com/api/v5/repos/yizhixixi/TextbookGo-Mobile/contents/update/latest.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")

        // Gitee 不可用时回退到 GitHub 的同一份清单（github.com raw 会跳转到 raw.githubusercontent.com）。
        val updateManifestFallbackUrl =
            (project.findProperty("textbookgo.updateManifestFallbackUrl") as? String)
                ?: "https://github.com/AlsoLate/TextbookGo-Mobile/raw/main/update/latest.json"
        buildConfigField("String", "UPDATE_MANIFEST_FALLBACK_URL", "\"$updateManifestFallbackUrl\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_TEST_ENTRY", "true")
        }
        release {
            isMinifyEnabled = false
            // 正式包不提供手动 token / 示例模式。
            buildConfigField("boolean", "ALLOW_TEST_ENTRY", "false")
            // 没有 keystore.properties 时保持未签名，方便在没有密钥的机器上只做编译。
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        /**
         * 真机验收包：使用真实平台数据源，但用调试签名，便于直接安装。
         * 不属于发布渠道，不进入 Play/应用商店。
         */
        create("verify") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "ALLOW_TEST_ENTRY", "true")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".verify"
            versionNameSuffix = "-verify"
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

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
        unitTests.all {
            // 真实网络探针默认跳过：-Dnetwork.probe=true 时才对线上目录做契约校验。
            it.systemProperty("network.probe", System.getProperty("network.probe") ?: "false")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    debugImplementation(project(":testing"))

    androidTestImplementation(project(":testing"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
