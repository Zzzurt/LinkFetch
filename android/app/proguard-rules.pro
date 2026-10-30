# ===== kotlinx.serialization =====
# 保留生成的 *$$serializer 类与 Companion 上的 serializer() 方法，
# 否则 JSON 编解码（解析响应、历史记录 mediaJson）在运行时会失败。
-keepattributes *Annotation*, InnerClasses

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `Companion` object field of serializable classes.
-keepclassmembers class com.linkfetch.app.** {
    *** Companion;
}
# Keep `serializer()` on companion objects of serializable classes.
-keepclasseswithmembers class com.linkfetch.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep generated `*$$serializer` classes.
-keep,includedescriptorclasses class com.linkfetch.app.**$$serializer { *; }

# ===== WorkManager =====
# Worker 由 WorkManager 按类名反射实例化，R8 不能删掉它的构造函数。
-keep class com.linkfetch.app.data.download.DownloadWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
