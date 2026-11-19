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

# PlayerView is inflated by class name and reads these fields via getstatic.
# R8 was renaming PlayerView and stripping the API 34 surface helper, which
# crashes the process the moment a channel starts on Android 14+.
# Player classes are loaded by name. Keep the whole Media3 stack so a
# stripped extractor or renderer cannot kill the process when a channel starts.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class androidx.media3.ui.PlayerView { *; }
-keep class androidx.media3.ui.PlayerView$* { *; }
-keep class androidx.media3.ui.AspectRatioFrameLayout { *; }
-keep class androidx.media3.ui.AspectRatioFrameLayout$* { *; }
-keep class androidx.media3.ui.SubtitleView { *; }
-keep class androidx.media3.ui.SubtitleView$* { *; }
-keep class androidx.media3.ui.CanvasSubtitleOutput { *; }
-keep class androidx.media3.ui.PlayerControlView { *; }
-keep class androidx.media3.ui.PlayerControlView$* { *; }
-keep class androidx.media3.ui.DefaultTimeBar { *; }
-keepclassmembers class androidx.media3.ui.R$* { public static <fields>; }

-keep class androidx.media3.exoplayer.hls.HlsMediaSource { *; }
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.rtsp.RtspMediaSource { *; }
-keep class androidx.media3.exoplayer.rtsp.RtspMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.smoothstreaming.SsMediaSource { *; }
-keep class androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory { *; }
-keep class androidx.media3.datasource.okhttp.OkHttpDataSource { *; }
-keep class androidx.media3.datasource.okhttp.OkHttpDataSource$Factory { *; }



-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class androidx.media3.exoplayer.source.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.exoplayer.hls.** { *; }
