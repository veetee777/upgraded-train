plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.owasp.dependencycheck")
}

// Pin resolved direct and transitive versions once the first cloud resolution is run.
dependencyLocking {
    lockAllConfigurations()
}

// Resolves every resolvable app configuration so --write-locks can capture the
// complete dependency graph, including transitive dependencies.
tasks.register("resolveAllDependencies") {
    group = "verification"
    description = "Resolves all app dependency configurations for lockfile and audit generation."
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            configuration.incoming.resolutionResult.allComponents.forEach { component ->
                component.id.displayName // Force full graph resolution.
            }
        }
    }
}

dependencyCheck {
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 7.0f
    nvd {
        // Supply NVD_API_KEY as an environment variable in the build environment.
        apiKey = System.getenv("NVD_API_KEY")
    }
}

android {
    namespace = "com.glassforge.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.glassforge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
