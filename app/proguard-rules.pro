-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep @Keep annotated classes and members (Firestore / Moshi models)
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# App data models serialized via Moshi / Firestore / Room
-keep class com.example.data.model.** { *; }
-keep class com.example.calls.**Dto { *; }
-keep class com.example.calls.**Request* { *; }
-keep class com.example.calls.**Response* { *; }

# Kotlinx Coroutines & Serialization / Reflection
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Retrofit / OkHttp / Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# LiveKit & WebRTC native bindings
-keep class org.webrtc.** { *; }
-keep class io.livekit.android.** { *; }
-dontwarn org.webrtc.**
-dontwarn livekit.**

# Google Play Billing (when enabled)
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
