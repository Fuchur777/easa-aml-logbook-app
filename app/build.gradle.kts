import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // The Kotlin package the code lives in. Deliberately not the same as applicationId:
    // renaming it would touch every file's package declaration for no functional gain, and
    // Android treats the two as independent on purpose.
    namespace = "nl.part66l.logbook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The app's permanent identity on Google Play — it can never change once published,
        // and it is half of what identifies the app to Google's OAuth (the other half being
        // the signing certificate SHA-1). Not the same as `namespace` above.
        applicationId = "nl.schellenberg.amlog"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /*
     * Release signing.
     *
     * The keystore and its passwords never enter the repository — they live in
     * keystore.properties, which .gitignore covers. Without that file the release build
     * still assembles (unsigned), so a fresh checkout is never broken; it just can't
     * produce something uploadable. See docs/release.md.
     */
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use(::load)
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off for now. PdfBox-Android and BouncyCastle both reach for classes
            // reflectively, and a keep rule missed there fails at runtime when a certificate
            // is generated, not at build time. Size is irrelevant on a test track, so this
            // trades bytes for not shipping a build that can't issue a CRS. Turn it on once
            // there's a tester who can exercise generate-and-sign on a minified build.
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.findByName("release")
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

    // Auto-sync (§10) — WorkManager, plus Hilt's WorkManager integration so DriveSyncWorker can
    // be @Inject-constructed like everything else instead of manually wiring its dependencies.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

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
    implementation(libs.androidx.exifinterface)

    implementation(libs.play.services.auth)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
