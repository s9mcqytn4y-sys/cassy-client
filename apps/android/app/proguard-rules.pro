# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.azuredevid.cassy.**$$serializer { *; }
-keepclassmembers class com.azuredevid.cassy.** {
    *** Companion;
}
-keepclasseswithmembers class com.azuredevid.cassy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# AndroidX Lifecycle SavedStateHandle & ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class androidx.lifecycle.SavedStateHandle { *; }

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# SplashScreen
-keep class androidx.core.splashscreen.** { *; }

# Ktor, Coroutines & OkHttp
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**

# Google Tink, CameraX, MLKit & ZXing
-dontwarn com.google.crypto.tink.**
-dontwarn androidx.camera.**
-dontwarn com.google.mlkit.**
-dontwarn com.google.zxing.**
