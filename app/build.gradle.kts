plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "tao.test.flipaccounting"
    // 修改点 1：升级编译版本到 34
    compileSdk = 34

    defaultConfig {
        applicationId = "tao.test.flipaccounting"
        minSdk = 24
        // 修改点 2：目标版本也建议设为 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        // 修改点：由 VERSION_1_8 改为 VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // 修改点：由 "1.8" 改为 "17"
        jvmTarget = "17"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
// 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

// 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
// Material Design (用于底部导航栏)
    implementation("com.google.android.material:material:1.11.0")
    // Shizuku 核心库 (Kotlin DSL 标准写法)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
// 确保有这一行
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Retrofit 及其 Gson 转换器
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Glide 图片加载库
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Gson JSON 解析库
    implementation("com.google.code.gson:gson:2.10.1")
}