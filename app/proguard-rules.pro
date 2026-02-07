# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Bouncy Castle (encryption)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Jotty API models (Gson serialization)
-keep class com.jotty.android.data.api.** { *; }

# Jotty encryption models (Gson / reflection)
-keep class com.jotty.android.data.encryption.** { *; }

# Jotty preferences models
-keep class com.jotty.android.data.preferences.JottyInstance { *; }

# GitHub release (update check) DTOs for Gson
-keep class com.jotty.android.data.updates.GitHubReleaseResponse { *; }
-keep class com.jotty.android.data.updates.GitHubAsset { *; }
