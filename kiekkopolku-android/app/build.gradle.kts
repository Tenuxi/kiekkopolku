import java.util.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val releaseKeysFile = rootProject.file(".signing/release.properties")
val releaseKeys = Properties().apply { if (releaseKeysFile.isFile) releaseKeysFile.inputStream().use(::load) }
android {
    namespace = "fi.kiekkopolku.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "fi.kiekkopolku.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseKeysFile.isFile) create("production") {
            storeFile = rootProject.file(releaseKeys.getProperty("storeFile"))
            storePassword = releaseKeys.getProperty("storePassword")
            keyAlias = releaseKeys.getProperty("keyAlias")
            keyPassword = releaseKeys.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { if (releaseKeysFile.isFile) signingConfig = signingConfigs.getByName("production") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "1536m"; it.forkEvery = 1 }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("test").resources.srcDir("schemas")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.icons)
    implementation(libs.activity)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.hilt.runtime)
    implementation(libs.coroutines)
    implementation(libs.maplibre)
    implementation(libs.okhttp)
    implementation("org.jsoup:jsoup:1.23.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation(libs.serialization.json)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.compose.test)
}

// Never silently publish an unsigned release when the local signing identity is missing.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst { check(releaseKeysFile.isFile) { "Release signing missing: restore .signing/release.properties and its keystore." } }
}
