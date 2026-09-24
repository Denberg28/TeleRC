plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "io.github.denberg28.telerc"
 compileSdk = 35
 buildFeatures { buildConfig = true }
 defaultConfig { applicationId = "io.github.denberg28.telerc"; minSdk = 26; targetSdk = 35; versionCode = 17; versionName = "0.7.6"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 signingConfigs {
   create("teleRcRelease") {
     val keystorePath = System.getenv("TELERC_KEYSTORE_FILE")
     if (!keystorePath.isNullOrBlank()) {
       storeFile = file(keystorePath)
       storePassword = System.getenv("TELERC_KEYSTORE_PASSWORD")
       keyAlias = System.getenv("TELERC_KEY_ALIAS")
       keyPassword = System.getenv("TELERC_KEY_PASSWORD")
     }
   }
 }
 buildTypes {
   getByName("release") { signingConfig = signingConfigs.getByName("teleRcRelease") }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
