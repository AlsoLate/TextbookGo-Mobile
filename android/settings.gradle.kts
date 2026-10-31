pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TextbookGo-Mobile"

include(":core")
include(":testing")

// Android 模块仅在检测到 SDK 时纳入构建：
// - local.properties 的 sdk.dir，或
// - ANDROID_HOME / ANDROID_SDK_ROOT。
// 这样没有 SDK 的机器仍可运行 :core 的 JVM 测试。
val localProperties = java.util.Properties().apply {
    val file = file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val sdkDir = providers.gradleProperty("sdk.dir").orNull
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: localProperties.getProperty("sdk.dir")

if (!sdkDir.isNullOrBlank()) {
    include(":app")
}
