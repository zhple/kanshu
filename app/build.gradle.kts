plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kanshu.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kanshu.reader"
        minSdk = 24
        targetSdk = 35
        versionCode = 37
        versionName = "1.9.1"

        // 自动更新：指向 GitHub Releases latest API
        buildConfigField(
            "String",
            "UPDATE_API_URL",
            "\"https://api.github.com/repos/zhple/kanshu/releases/latest\""
        )
        // 仓库默认书：catalog + 文件目录（raw）
        buildConfigField(
            "String",
            "DEFAULT_BOOKS_CATALOG_URL",
            "\"https://raw.githubusercontent.com/zhple/kanshu/main/default-books/catalog.json\""
        )
        buildConfigField(
            "String",
            "DEFAULT_BOOKS_BASE_URL",
            "\"https://raw.githubusercontent.com/zhple/kanshu/main/default-books\""
        )
        // 共享歌单：playlist + 音频目录（raw）
        buildConfigField(
            "String",
            "DEFAULT_MUSIC_CATALOG_URL",
            "\"https://raw.githubusercontent.com/zhple/kanshu/main/default-music/playlist.json\""
        )
        buildConfigField(
            "String",
            "DEFAULT_MUSIC_BASE_URL",
            "\"https://raw.githubusercontent.com/zhple/kanshu/main/default-music\""
        )
        buildConfigField("String", "GITHUB_OWNER", "\"zhple\"")
        buildConfigField("String", "GITHUB_REPO", "\"kanshu\"")
        buildConfigField("String", "GITHUB_BRANCH", "\"main\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Notion 风格块拖拽排序：https://github.com/Calvin-LL/Reorderable
    implementation("sh.calvin.reorderable:reorderable:3.0.0")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-common:$media3")
}
