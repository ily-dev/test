plugins {
    id("com.android.library") 
}

android {
    // Ein eindeutiger Namespace für Ihr Gateway-Modul
    namespace = "org.gateway.android"
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

    //SSHClient im apk einfuegen
    implementation(project(":sshclient"))
    
    // 1. Die Py4J Bibliothek für das Netzwerk-Gateway
    implementation("net.sf.py4j:py4j:0.10.9.7")
    
    // 2. Android Standard-Annotationen (hilfreich für Android-Entwicklung)
    implementation("androidx.annotation:annotation:1.7.0")
    
    // 3. WICHTIG: Damit 'PythonActivity' gefunden wird
    // Wenn p4a baut, stellt es die Kern-Klassen bereit. 
    // Für die lokale Kompilierung in Gradle binden wir es als 'compileOnly' ein.
    compileOnly(fileTree(mapOf("dir" to "../libs", "include" to listOf("*.jar", "*.aar"))))
}
