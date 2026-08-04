// sshclient/build.gradle.kts

plugins {
    id("com.android.library")  // ★ ★ ★ Android-Library statt java-library ★ ★ ★
}

android {
    namespace = "com.meinname.ssh"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 25
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.jcraft:jsch:0.1.55")
    implementation("androidx.annotation:annotation:1.7.0")
}