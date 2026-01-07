plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.superscreenshot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.superscreenshot"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // Windows 上经常会出现旧的 debug APK 被资源管理器/杀软占用导致 :app:packageDebug 无法删除输出目录。
        // 新增一个独立的 buildType，用于输出到 outputs/apk/debug02/，并固定文件名为 app-debug-0.2.apk。
        create("debug02") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Build debug02, then copy to a fixed name so it's easy to find/share.
// This avoids relying on AGP internal APIs for renaming APK outputs.
tasks.register<Copy>("copyDebug02Apk") {
    dependsOn("assembleDebug02")
    val srcDir = layout.buildDirectory.dir("outputs/apk/debug02")
    val dstDir = layout.buildDirectory.dir("outputs/apk/ss")
    from(srcDir)
    include("*.apk")
    exclude("app-debug-0.2.apk")
    into(dstDir)
    rename { "app-debug-0.2.apk" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // CameraX
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // OpenCV (Android AAR) for screen quad detection + homography
    implementation("com.quickbirdstudios:opencv:4.5.3.0")

    // ML Kit selfie segmentation (for residual background solid-color replacement)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // EXIF (fix camera JPEG rotation so it matches screen orientation)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}


