plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kitsune.core.backend"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://kitsune-ai.com\"")
    }

    buildTypes {
        debug {
            // Was "http://91.99.182.95:8081" (raw port, no TLS) — closed off during the pentest
            // hardening pass (see KitsuneBackend BUGS.md); the dev backend is now only reachable
            // through nginx+TLS on this subdomain, same pattern as production.
            val devUrl = project.findProperty("kitsune.backendUrl") as String?
                ?: "https://dev.kitsune-ai.com"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$devUrl\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    api(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
}
