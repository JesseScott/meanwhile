# R8 rules for the release build. Most libraries (Compose, OkHttp, Firebase, DataStore) ship their own.

# Ktor's logging and debug detection mention classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.netty.**

# kotlinx.serialization: the generated serializers are found by name at run time.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class tt.co.jesses.meanwhile.core.** {
    *** Companion;
}
-keepclasseswithmembers class tt.co.jesses.meanwhile.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Crash reports: keep file names and line numbers so stack traces can be read after mapping.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
