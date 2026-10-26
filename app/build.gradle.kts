plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.catalog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.catalog"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        animationsDisabled = true
        // Orchestrator включается флагом -Porchestrator=true, по умолчанию тесты идут в одном процессе.
        if (providers.gradleProperty("orchestrator").orNull == "true") {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
        managedDevices {
            localDevices {
                // Тот же образ, что у эмуляторов pixel35*: Android 35, Google APIs, arm64.
                create("pixel35gmd") {
                    device = "Pixel"
                    apiLevel = 35
                    systemImageSource = "google"
                }
                // Облегчённый образ для тестов (ATD), для сравнения отдельной строкой.
                create("pixel35atd") {
                    device = "Pixel"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockwebserver3)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.test.parameter.injector)
    // Для Marathon: передаёт аннотации тестов его парсеру на устройстве (отбор по @Smoke и т.п.).
    androidTestImplementation(libs.adam.test.annotation.producer)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
