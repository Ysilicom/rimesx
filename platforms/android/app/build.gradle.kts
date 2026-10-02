plugins { id("com.android.application") }
android {
    namespace = "org.scholay.rimes.android"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "org.scholay.rimes.android"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "org.scholay.rimes.android.EngineInstrumentation"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        versionCode = 4
        versionName = "0.1.0-dev.4"
    }
    ndkVersion = "29.0.14206865"
    sourceSets.getByName("main") {
        assets.srcDir("build/generated/rime/assets")
        jniLibs.srcDir("build/generated/rime/jniLibs")
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":core")) }

// Generate with scripts/build-engine.py before Gradle; missing resources must fail closed.
tasks.register("verifyEngineResources", Exec::class) {
    commandLine("python3", "../scripts/verify-engine.py")
}
tasks.named("preBuild") { dependsOn("verifyEngineResources") }
