import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    id("org.jetbrains.kotlin.kapt")
}

// 读取local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use(localProperties::load)
}

// The build script can supply release credentials through temporary environment
// variables so they never need to be written into the repository. local.properties
// remains the fallback for Android Studio builds.
fun getProperty(key: String, defaultValue: String = ""): String {
    val environmentKey = when (key) {
        "signing.storeFile" -> "CHATMATE_SIGNING_STORE_FILE"
        "signing.storePassword" -> "CHATMATE_SIGNING_STORE_PASSWORD"
        "signing.keyAlias" -> "CHATMATE_SIGNING_KEY_ALIAS"
        "signing.keyPassword" -> "CHATMATE_SIGNING_KEY_PASSWORD"
        else -> null
    }
    return environmentKey?.let(System::getenv)
        ?: localProperties.getProperty(key)
        ?: defaultValue
}

android {
    namespace = "com.hwb.aianswerer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hwb.aianswerer"
        minSdk = 29
        targetSdk = 34
        versionCode = 19
        versionName = "1.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Allow Android framework calls in JUnit tests (e.g., android.util.Log)
        testOptions {
            unitTests {
                isReturnDefaultValues = true
                isIncludeAndroidResources = true
                // Robolectric 测试 JVM：显式堆大小防止 OOM（GC 风暴吃满 CPU），
                // 限制并行 fork 数避免多 JVM 同时初始化打满 CPU
                all { test ->
                    test.maxHeapSize = "2g"
                    test.maxParallelForks = 2
                }
            }
        }

        ndk {
            //noinspection ChromeOsAbiSupport
            // 支持arm64-v8a(真机)和x86_64(模拟器)
            abiFilters += setOf("arm64-v8a", "x86_64")
        }

        // BuildConfig字段 - 从local.properties读取
        val apiUrl = getProperty("api.url", "https://api.openai.com/v1/chat/completions")
        val apiKey = getProperty("api.key", "")
        val apiModel = getProperty("api.model", "gpt-4")
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
        buildConfigField("String", "API_MODEL", "\"$apiModel\"")
    }

    // Release签名配置
    signingConfigs {
        create("release") {
            val storeFile = getProperty("signing.storeFile")
            val storePassword = getProperty("signing.storePassword")
            val keyAlias = getProperty("signing.keyAlias")
            val keyPassword = getProperty("signing.keyPassword")

            if (storeFile.isNotEmpty() && storePassword.isNotEmpty() && keyAlias.isNotEmpty() && keyPassword.isNotEmpty()) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                println("Release signing configuration loaded")
            }
        }
    }

    // APK命名规则
    applicationVariants.all {
        val versionNameValue = versionName
        outputs.all {
            // 使用安全的方式重命名APK，避免依赖AGP内部API
            try {
                val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val date = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
                outputImpl.outputFileName =
                    "${date}_ChatMateAI_v${versionNameValue}.apk"
            } catch (e: Exception) {
                println("Warning: Could not rename APK output: ${e.message}")
            }
        }
    }

    buildTypes {
        debug {
            // Optional isolated install for device QA; regular debug builds keep their existing ID.
            applicationIdSuffix = providers.gradleProperty("chatmateDebugSuffix").orNull ?: ""
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true  // 启用R8代码混淆和优化
            isShrinkResources = true  // 启用资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release签名：签名配置不完整时自动降级到debug签名
            val releaseSigningConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseSigningConfig.storeFile != null) {
                releaseSigningConfig
            } else {
                signingConfigs.getByName("debug")
            }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockwebserver)

    // ML Kit for text recognition (Chinese recognizer supports Latin text)
    implementation(libs.mlkit.text.recognition.chinese)

    // OkHttp for HTTP requests
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Gson for JSON parsing
    implementation(libs.gson)

    // Kotlin Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)

    // Lifecycle components
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Jetpack Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    // implementation("androidx.compose.material:material-icons-extended") // 移除：使用本地图标定义，减少13.1 MB
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.test.manifest)

    implementation(libs.mmkv)

    // Security - EncryptedSharedPreferences for API Key storage
    implementation(libs.security.crypto)

    // Chat companion persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
}

kapt {
    correctErrorTypes = true
}

// Kover code coverage configuration
kover {
    reports {
        filters {
            excludes {
                classes("com.hwb.aianswerer.ui.theme.*")
                classes("com.hwb.aianswerer.BuildConfig")
            }
        }
    }
}
