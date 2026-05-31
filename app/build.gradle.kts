// Ensure all these are present, along with any existing imports
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.csmide"
    compileSdk = 36
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.csmide"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        externalNativeBuild {
            ndkBuild {
                arguments += "APP_STL=c++_static"
                arguments += "APP_LDFLAGS+=-Wl,-z,max-page-size=16384"
            }
        }
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"apt-android-7\"")
    }
    ndkVersion = "27.0.12077973"

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

configurations.all {
    resolutionStrategy {
        force("org.commonmark:commonmark:0.21.0")
        exclude("com.atlassian.commonmark", "commonmark")
        force("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
        exclude("com.atlassian.commonmark", "commonmark-ext-gfm-strikethrough")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugarJdkLibs)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.android.play:feature-delivery:2.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.preference:preference:1.2.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:recycler:4.6.2")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.luben:zstd-jni:1.5.5-11")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("androidx.window:window:1.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Updated sora-editor with 16KB page support (Note: group ID changed in 0.24.x)
    implementation("io.github.rosemoe:editor:0.24.5")
    implementation("io.github.rosemoe:language-treesitter:0.24.5")
    implementation("io.github.rosemoe:language-textmate:0.24.5")
}
