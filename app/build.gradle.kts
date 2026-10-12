plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.emir.hizgostergesi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emir.hizgostergesi"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "3.0-beta2"
    }

    // Sabit anahtar: her derleme aynı imzayla çıkar, güncellemeler eskisinin üstüne kurulur
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Optimize sürüm: kullanılmayan kod ve kaynaklar atılır, kod sıkıştırılır
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        textReport = true
        htmlReport = false
        xmlReport = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Android'in org.json'u birim testlerinde boş taslaktır; gerçek uygulamayı kullan
    testImplementation("org.json:json:20240303")
}
