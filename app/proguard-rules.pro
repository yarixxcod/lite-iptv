-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature

-dontwarn org.tukaani.xz.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
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

-keepclassmembers class player.vvvion.app.data.net.** {
    <fields>;
}

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Release startup: R8 was dropping the Android main dispatcher and Room's generated
# database. Keep the ServiceLoader entry points and the generated DB.
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.HandlerContext { *; }
-keep class kotlinx.coroutines.android.HandlerDispatcherKt { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }

-keep class * extends androidx.room.RoomDatabase { *; }
-keep class player.vvvion.app.data.db.** { *; }

-keep class org.koin.** { *; }
-dontwarn org.koin.**

-keep class com.google.common.** { *; }
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class player.vvvion.app.** {
    *** Companion;
}

