import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/**
 * 签名凭据，优先取 keystore.properties，其次取环境变量（供 CI 使用）。
 * 四项缺任意一项就返回 null —— 半套配置会在构建后期以晦涩的错误失败，
 * 不如干脆退回未签名构建。
 */
fun loadKeystoreProperties(propertiesFile: File): Map<String, String>? {
    val fromFile = if (propertiesFile.exists()) {
        Properties().apply { propertiesFile.inputStream().use { load(it) } }
            .entries.associate { (key, value) -> key.toString() to value.toString() }
    } else {
        emptyMap()
    }

    val resolved = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .mapNotNull { key ->
            val value = fromFile[key]
                ?: System.getenv("HERTZ_" + key.replace(Regex("([A-Z])"), "_$1").uppercase())
            value?.takeIf { it.isNotBlank() }?.let { key to it }
        }
        .toMap()

    return resolved.takeIf { it.size == 4 }
}

android {
    namespace = "xyz.sakulik.hertz"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.sakulik.hertz"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // 密钥材料从 keystore.properties 或环境变量读取，绝不入库。
        // 配置缺失时不注册该 signingConfig，release 仍可构建为未签名包。
        val keystoreProperties = loadKeystoreProperties(rootProject.file("keystore.properties"))
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getValue("storeFile"))
                storePassword = keystoreProperties.getValue("storePassword")
                keyAlias = keystoreProperties.getValue("keyAlias")
                keyPassword = keystoreProperties.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 独立 applicationId，使开发版与已安装的正式版共存：
            // 两者签名不同，同包名安装会以 INSTALL_FAILED_UPDATE_INCOMPATIBLE 失败，
            // 而卸载正式版会连带删掉用户已记录的音域
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 刻意不改 app_name：两者在启动器里同名，只以包名区分
        }

        release {
            // R8 开启后必须在真机上重新验证音高检测：混淆最可能打断的
            // 正是那个跨包名的手写音频桥
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // Lifecycle Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // TarsosDSP and slf4j
    implementation(libs.tarsos.dsp.core)
    implementation(libs.slf4j.nop)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
