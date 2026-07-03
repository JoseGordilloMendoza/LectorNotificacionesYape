import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
}

android {
    namespace = "com.example.kajaapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kajaapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 16
        versionName = "2.6"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 1. Cargar el archivo local.properties (que está en la raíz del proyecto)
        val properties = Properties()
        properties.load(FileInputStream(project.rootProject.file("local.properties")))

        // 2. Inyectar esos valores como si fueran constantes de Android
        // Esto hace que "BuildConfig.SUPABASE_URL" exista en todo el proyecto
        buildConfigField("String", "SUPABASE_URL", "\"${properties.getProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${properties.getProperty("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "BACKEND_URL", "\"${properties.getProperty("BACKEND_URL", "http://10.0.2.2:8000/")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }
    
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Google Sign-In (Completo para Firebase Auth)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    
    // Firebase Auth
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    
    // Firebase Firestore (con versión explícita)
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.0")
    
    // Firebase Storage (para descargar APKs de actualización)
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    
    // Coil: carga de imágenes (foto de perfil Google)
    implementation("io.coil-kt:coil:2.6.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val supabaseVersion = "2.2.3"
    val ktorVersion = "2.3.9"
    
    // Módulo de Autenticación de Supabase (GoTrue)
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseVersion")
    
    // Módulo de Base de Datos de Supabase (Postgrest)
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
    
    // Motor HTTP para Supabase
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    
    // Serialización JSON requerida por Supabase
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Retrofit y Gson para backend Django
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
}
