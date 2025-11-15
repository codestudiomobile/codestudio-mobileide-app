// Ensure all these are present, along with any existing imports
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.cs.ide"
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
        applicationId = "com.cs.ide"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndkVersion = "29.0.14033849"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"apt\"")
    }

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
    ndkVersion = "29.0.14033849"
}

configurations.all {
    resolutionStrategy {
        force("org.commonmark:commonmark:0.21.0")
        exclude("com.atlassian.commonmark", "commonmark")
        force("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
        force("org.commonmark:commonmark:0.21.0")
        exclude("com.atlassian.commonmark", "commonmark-ext-gfm-strikethrough")
        exclude("com.atlassian.commonmark", "commonmark")
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.play:feature-delivery:2.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.preference:preference:1.2.1")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:recycler:4.6.2")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("androidx.window:window:1.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
