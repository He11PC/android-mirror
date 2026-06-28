@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "fr.hellpc.mirror"

    compileSdk = 36

    defaultConfig {
        applicationId = "fr.hellpc.mirror"
        minSdk = 26
        targetSdk = 36

        versionCode = 17
        versionName = "26.06.22"

        javaCompileOptions {
            annotationProcessorOptions { arguments += mapOf("room.incremental" to "true", "room.expandProjection" to "true") }
        }

        //testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("debug")
        androidResources.localeFilters += listOf("bn", "cs", "de", "el", "en", "es", "fr", "hi", "hu", "in", "it", "ja", "ko", "nl", "pl", "pt", "ro", "ru", "sv", "tr", "uk", "zh")
    }

    bundle {
        language { enableSplit = false }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/versions/{9,11,15}/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    val vRoom = "2.8.4"
    val vLifecycle = "2.11.0"
    val vCoroutines = "1.11.0"

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")

    // Kotlin
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$vCoroutines")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$vCoroutines")

    // Components
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // ROOM
    implementation("androidx.room:room-runtime:$vRoom")
    implementation("androidx.room:room-ktx:$vRoom")
    ksp("androidx.room:room-compiler:$vRoom")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-common-java8:$vLifecycle")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$vLifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$vLifecycle")
    implementation("androidx.lifecycle:lifecycle-service:$vLifecycle")

    // Security
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.guava:guava:33.6.0-android")

    // NFS
    implementation("com.emc.ecs:nfs-client:1.1.0")
    // SMB
    implementation("com.hierynomus:smbj:0.14.0")
    // FTP
    implementation("commons-net:commons-net:3.13.0")
    // SFTP
    implementation("com.github.mwiede:jsch:2.28.3")
    // WebDav
    implementation("com.github.thegrizzlylabs:sardine-android:0.9")
    implementation("io.github.rburgst:okhttp-digest:3.1.1")

    // Permissions
    implementation("com.github.judemanutd:autostarter:1.1.0")

    // SMB logs
    //implementation("org.slf4j:slf4j-simple:2.0.17")

    // Test
    //testImplementation("junit:junit:4.13.2")
    //androidTestImplementation("androidx.test.ext:junit:1.1.5")
    //androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}