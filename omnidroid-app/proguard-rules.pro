## Options
# R8 optimization enabled via proguard-android-optimize.txt (do not add -dontoptimize).
# Verified: assemblePlayDynamicRelease with minifyPlayDynamicReleaseWithR8 succeeds.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable,Signature,JavascriptInterface,Exceptions
-verbose

## Fabric
-dontnote com.google.android.gms.**
-dontnote com.google.firebase.crash.FirebaseCrash

## Kotlin
-dontwarn kotlin.**
-dontnote kotlin.**
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.coroutines.flow.**
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

## Okio
-dontwarn okio.**

## OkHttp
-dontwarn okhttp3.**
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontnote com.android.org.conscrypt.SSLParametersImpl
-dontnote dalvik.system.CloseGuard
-dontnote sun.security.ssl.SSLContextImpl
-dontnote org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontnote org.conscrypt.ConscryptEngineSocket

## Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

## Moshi
-dontnote sun.misc.Unsafe

## Google API
-dontwarn com.google.api.client.**

## Guava
-dontnote com.google.appengine.api.ThreadManager
-dontnote com.google.apphosting.api.ApiProxy
-dontwarn java.lang.ClassValue
-dontwarn com.google.j2objc.annotations.Weak
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.apache.commons.codec.binary.Base64
-dontwarn org.apache.commons.codec.binary.StringUtils

## Misc
-dontwarn com.uber.javaxextras.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn junit.**
-dontwarn com.google.errorprone.**
-dontnote android.net.http.*

## Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.omnidroid.**$$serializer { *; }
-keepclassmembers class com.omnidroid.** {
    *** Companion;
}
-keepclasseswithmembers class com.omnidroid.** {
    kotlinx.serialization.KSerializer serializer(...);
}

## LibretroDroid JNI Interface (strictly scoped to C++ FindClass/GetMethodID/GetFieldID)
-keep class com.swordfish.libretrodroid.Variable { <fields>; }
-keep class com.swordfish.libretrodroid.Controller { <fields>; }
-keep class com.swordfish.libretrodroid.DetachedVirtualFile { *; }
-keep class com.swordfish.libretrodroid.GLRetroShader { <fields>; }
-keep class com.swordfish.libretrodroid.RetroException { *; }
-keep class com.swordfish.libretrodroid.GLRetroView {
    void refreshAspectRatio();
    void sendRumbleEvent(int, float, float);
}
-keep class com.swordfish.libretrodroid.ImmersiveMode* { *; }
-keepclasseswithmembernames class com.swordfish.libretrodroid.LibretroDroid {
    native <methods>;
}



