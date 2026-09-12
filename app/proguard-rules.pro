# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx-serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.studypath.app.**$$serializer { *; }
-keepclassmembers class com.studypath.app.** { *** Companion; }
-keepclasseswithmembers class com.studypath.app.** { kotlinx.serialization.KSerializer serializer(...); }
