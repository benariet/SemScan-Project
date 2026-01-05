import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application") version "8.13.1"
    id("com.google.gms.google-services") version "4.4.4"
}

android {
    namespace = "org.example.semscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.example.semscan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 16 KB page size compatibility
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
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
        isCoreLibraryDesugaringEnabled = true
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf(
            "MissingTranslation",      // App is English-only, no need for translations
            "OldTargetApi",            // Target SDK is intentional
            "GradleDependency",        // We control dependency versions
            "NewerVersionAvailable",   // We control dependency versions
            "AndroidGradlePluginVersion", // We control plugin version
            "LocaleFolder",            // English-only app
            "DefaultLocale",           // App uses US locale consistently
            "MissingSuperCall",        // onBackPressed doesn't require super in our use cases
            "UnsafeOptInUsageError",   // CameraX experimental API usage is intentional
            "PermissionImpliesUnsupportedChromeOsHardware", // App requires camera, Chrome OS not supported
            "LockedOrientationActivity", // Portrait lock is intentional
            "DiscouragedApi",          // Some deprecated APIs are still needed for compatibility
            "QueryPermissionsNeeded",  // Package visibility handled properly
            "VectorRaster"             // Vector to raster conversion is acceptable
        )
    }
    
    // 16 KB page size compatibility - ensure native libraries are properly aligned
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.navigation:navigation-fragment:2.7.6")
    implementation("androidx.navigation:navigation-ui:2.7.6")
    
    // QR Code scanning and generation
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")
    
    // CameraX for modern camera functionality (updated for 16 KB compatibility)
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    
    // ML Kit for barcode scanning (updated for 16 KB page size compatibility)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    
    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Preferences
    implementation("androidx.preference:preference:1.2.1")

    // Security - EncryptedSharedPreferences for secure credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Firebase for push notifications
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    
    // CSV export
    implementation("com.opencsv:opencsv:5.9")
    
    // Desugaring for Android 8+ compatibility with Java 17 features
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.register("installDebugAllDevices") {
    group = "install"
    description = "Assemble debug once, then install+launch on all connected devices."
    
    dependsOn("assembleDebug")
    
    doLast {
        // מצא APK אחרון של debug
        val apkDir = file("build/outputs/apk/debug")
        if (!apkDir.exists()) {
            throw GradleException("Debug APK directory not found. Run 'assembleDebug' first.")
        }
        
        val apks = apkDir.listFiles { _, name -> name.endsWith(".apk") }
            ?: throw GradleException("No debug APK found in ${apkDir.absolutePath}")
        
        if (apks.isEmpty()) {
            throw GradleException("No debug APK found")
        }
        
        val apk = apks.maxByOrNull { it.lastModified() }
            ?: throw GradleException("Could not find latest APK")
        
        println("Found APK: ${apk.name}")
        
        // שלוף מכשירים במצב device
        val output = ByteArrayOutputStream()
        project.exec {
            commandLine("adb", "devices")
            standardOutput = output
        }
        
        val lines = output.toString().split("\n").drop(1)
        val serials: List<String> = lines
            .filter { line -> line.contains("\tdevice") }
            .map { line -> line.split("\t")[0] }
            .filter { line -> line.isNotBlank() }
        
        if (serials.isEmpty()) {
            throw GradleException("No connected devices found. Connect a device and enable USB debugging.")
        }
        
        println("Found ${serials.size} device(s): ${serials.joinToString(", ")}")
        
        val pkg = "org.example.semscan"
        val activity = "org.example.semscan.ui.auth.LoginActivity"
        
        serials.forEach { serial: String ->
            println("---- Device: $serial ----")
            
            // Setup port forwarding
            try {
                project.exec {
                    commandLine("adb", "-s", serial, "reverse", "tcp:8080", "tcp:8080")
                    isIgnoreExitValue = true
                }
                println("  [OK] Port forwarding active")
            } catch (e: Exception) {
                println("  [WARN] Port forwarding failed (may already be active)")
            }
            
            // Stop app
            project.exec {
                commandLine("adb", "-s", serial, "shell", "am", "force-stop", pkg)
                isIgnoreExitValue = true
            }
            
            // Install APK
            println("  Installing APK...")
            project.exec {
                commandLine("adb", "-s", serial, "install", "-r", "-d", apk.absolutePath)
            }
            
            // Launch app with explicit intent flags
            println("  Launching app...")
            val launchResult = project.exec {
                commandLine("adb", "-s", serial, "shell", "am", "start", 
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER",
                    "-n", "$pkg/$activity")
                isIgnoreExitValue = true
            }
            
            if (launchResult.exitValue == 0) {
                println("  ✓ Installed and launched on $serial")
            } else {
                println("  ⚠ Installed on $serial but launch may have failed")
            }
        }
        
        println("\n✅ Done! Installed on ${serials.size} device(s).")
    }
}