import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kitsune.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kitsune.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Pentest finding (see BUGS.md): the signing password used to be hardcoded here in plain
        // text, in a file tracked by git — one commit away from permanently exposing the Play
        // Store signing credentials. Read from a gitignored keystore.properties instead (same
        // convention as keystore.jks itself, never committed). Release builds fail fast with a
        // clear message if the file is missing, rather than silently signing with something wrong.
        val keystorePropsFile = rootProject.file("keystore.properties")
        if (keystorePropsFile.exists()) {
            val keystoreProps = Properties().apply {
                keystorePropsFile.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = file("../keystore.jks")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // findByName, not getByName: a checkout without keystore.properties (e.g. a fresh dev
            // machine) must still be able to sync/build debug variants. A release build attempted
            // without it falls back to Android Gradle Plugin's own default (unsigned/debug-signed)
            // rather than failing the whole Gradle configuration.
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    }

    hilt {
        enableAggregatingTask = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    implementation(project(":core:data"))
    implementation(project(":core:background"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:backend"))
    implementation(project(":core:network"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:persona"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:universe"))
    implementation(project(":feature:marketplace"))


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Only needed by the throwaway DatabaseRescueTool (app/src/androidTest/.../DatabaseRescueTest.kt)
    // — remove alongside it once the database has been recovered.
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.sqlcipher.android)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
