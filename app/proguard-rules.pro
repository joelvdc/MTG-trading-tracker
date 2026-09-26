# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mtgtrader.**$$serializer { *; }
-keepclassmembers class com.mtgtrader.** { *** Companion; }
-keepclasseswithmembers class com.mtgtrader.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp optional platform integrations
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
