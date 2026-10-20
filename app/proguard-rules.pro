# ---- ExamCamp release (R8) rules -------------------------------------------------------------
# The libraries below ship their own consumer rules (OkHttp, Coil, Firebase, Credential Manager,
# Compose, coroutines). These are the extra ones this app needs.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep the generated serializers of our @Serializable data classes.
# Without this, an obfuscated build fails at runtime with SerializationException while the
# debug build works fine.
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Our own data models (also serialised through reflection-free serializers, but kept whole so
# field names written to disk / sent to Supabase never change between releases).
-keep class com.examcamp.app.data.** { *; }

# Google Sign-In through Credential Manager
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# OkHttp optional TLS providers (not present on Android)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
