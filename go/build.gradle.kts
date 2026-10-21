plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "io.github.denberg28.telerc.go"
 compileSdk = 35
 buildFeatures { buildConfig = true }
 defaultConfig { applicationId = "io.github.denberg28.telerc.go"; minSdk = 26; targetSdk = 35; versionCode = if (providers.gradleProperty("goUpdateFixture").orNull == "true") 5 else 4; versionName = if (providers.gradleProperty("goUpdateFixture").orNull == "true") "1.0.4" else "1.0.3"; testInstrumentationRunner = "io.github.denberg28.telerc.GoInstrumentation" }
 sourceSets.getByName("main").res.srcDir("../app/src/main/res")
 signingConfigs { create("releaseKey") {
  System.getenv("TELERC_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let {
   storeFile=file(it);storePassword=System.getenv("TELERC_KEYSTORE_PASSWORD")
   keyAlias=System.getenv("TELERC_KEY_ALIAS");keyPassword=System.getenv("TELERC_KEY_PASSWORD")
  }
 } }
 buildTypes { getByName("release") { signingConfig=signingConfigs.getByName("releaseKey") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
