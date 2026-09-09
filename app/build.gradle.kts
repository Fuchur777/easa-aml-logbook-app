plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "nl.part66l.logbook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "nl.part66l.logbook"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        // bcprov/bcpkix/bcutil are three separately-published jars from the same
        // BouncyCastle release, each carrying its own copy of these META-INF text
        // files at an identical path — not a real conflict, just three copies of
        // the same license/notice text. None of it needs to end up in the APK.
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

ksp {
    // Room schemas are the migration history for legal records — committed, never gitignored.
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    // PdfBox-Android transitively pulls the entire older jdk15to18-naming-scheme
    // BouncyCastle family (bcprov, bcpkix, bcutil) alongside the jdk18on ones
    // declared below — same org.bouncycastle.* classes and resource paths under
    // different artifact IDs, so Gradle doesn't dedupe them and
    // checkDebugDuplicateClasses/mergeDebugJavaResource fails on the clash.
    // Excluding the older family keeps a single, consistent BouncyCastle version
    // on the classpath. (bcprov alone wasn't enough — bcpkix and bcutil conflict too.)
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.pdfbox.android)

    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)

    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}