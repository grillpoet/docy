# ============================================================
# docy ProGuard / R8 规则
#
# 构建配置：
#   minSdk        26
#   targetSdk     35
#   AGP           8.7.3
#   Kotlin        2.0.21
#   Compose BOM   2024.12.01
#   R8            full mode（AGP 8.x 默认）
#
# 设计原则：
#   绝大多数第三方库自带 consumer rules，本文件不重复造轮子。
#   只处理「consumer rules 覆盖不到」的应用自身逻辑：
#     1) kotlinx.serialization 数据类的 JSON key 稳定性
#     2) 通过 valueOf 反射查找的枚举常量名
#     3) AndroidManifest 里声明的组件
#   其余全部走 -dontwarn 兜底，让 R8 自由收缩。
# ============================================================


# ============================================================
# 一、基础属性
# ============================================================

# 崩溃堆栈可读性：保留源文件名和行号
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 注解、泛型签名、内部类元信息
#  - *Annotation*  覆盖 RuntimeVisible / RuntimeInvisible / AnnotationDefault
#  - Signature     泛型信息，kotlinx.serialization 和 Compose 都可能依赖
#  - InnerClasses  内部类信息，Kotlin 的 Companion / Lambda 用到
#  - EnclosingMethod  匿名类/局部类归属，Kotlin 编译器产物需要
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod


# ============================================================
# 二、枚举通用
# ============================================================
#
# Java 编译器和 Kotlin 编译器都会为 enum 生成 values() / valueOf()，
# 这两个方法通过反射调用。R8 默认会保留它们，但显式声明更保险，
# 也防止某个 enum 因为没有显式引用 valueOf 而被 R8 优化掉。

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ============================================================
# 三、应用数据类 —— JSON key 稳定性
# ============================================================
#
# 【为什么要 keep】
#
# kotlinx.serialization 编译期为每个 @Serializable 类生成一个
# $$serializer 内部类。这个 serializer 的序列化/反序列化逻辑里，
# 直接把字段名的字符串字面量写死了，例如：
#
#     descriptor = PluginGeneratedSerialDescriptor(
#         serialName = "LanguagePlugin",
#         elementsCount = 8,
#     )
#     descriptor.addElement(name = "name", isOptional = false)
#     descriptor.addElement(name = "extensions", isOptional = true)
#     ...
#
# 反序列化时，从外部 JSON 里读 key，然后按名字映射到字段。
#
# 如果 R8 把字段 name 混淆成 a，同时把 descriptor 里的字符串
# 一起改名（R8 会做字符串到字段名的联动混淆），那么：
#   - 应用内部的序列化往返是自洽的（写和读用同一个名字）
#   - 但读取外部提供的 JSON 会失败（外部 JSON 里 key 还是 "name"）
#
# docy 的 languages/*.json 和 libraries/*.json 都是从 assets 读的
# 外部资源，不是应用生成的。所以字段名绝对不能被混淆。
#
# 【为什么只 keep 这两个包】
#
# 如果写成 -keep class com.grillpoet.docy.** { *; }，会保留整个应用
# 的所有类和成员，几乎抵消所有混淆和收缩效果。
#
# 实际用了 @Serializable 的类只有：
#   com.grillpoet.docy.core.plugin.LanguagePlugin
#   com.grillpoet.docy.core.plugin.LibraryPlugin
#   com.grillpoet.docy.core.plugin.Rule
#   com.grillpoet.docy.core.plugin.Completion
#   com.grillpoet.docy.core.plugin.RunSpec
#   com.grillpoet.docy.core.archive.ArchiveFormat（enum，被通用 enum 规则覆盖）
#   com.grillpoet.docy.core.archive.ArchiveEntry（虽然是 data class，
#       但当前未标 @Serializable。保留以防未来加注解。）
#
# 保留范围限定在这两个包即可。

-keep class com.grillpoet.docy.core.plugin.** { *; }
-keep class com.grillpoet.docy.core.archive.** { *; }

# kotlinx.serialization 的伴生对象和生成 serializer 的兜底 keep
# （官方 consumer rules 已覆盖大部分，这里只处理自定义包下漏网的）
-keepclassmembers class com.grillpoet.docy.** {
    *** Companion;
}
-keepclasseswithmembers class com.grillpoet.docy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.grillpoet.docy.**$$serializer { *; }

-dontwarn kotlinx.serialization.**


# ============================================================
# 四、持久化枚举 —— DataStore key 稳定性
# ============================================================
#
# 【为什么要 keep】
#
# DocyPreferences 里通过字符串反射查找枚举常量：
#
#     SortMode.valueOf(it[Keys.sortMode] ?: "NAME")
#     SortOrder.valueOf(it[Keys.sortOrder] ?: "ASC")
#     ViewMode.valueOf(it[Keys.viewMode] ?: "LIST")
#
# 这些字符串已经写进了用户的 DataStore 里（"NAME" / "ASC" / "LIST"）。
# R8 默认会把 enum 常量名混淆成 a / b / c 之类，
# 下次启动时 valueOf("NAME") 就会抛 IllegalArgumentException。
#
# 【为什么不直接用 @SerialName】
#
# @SerialName 是 kotlinx.serialization 的注解，
# 这里用的是 AndroidX DataStore，两个体系不共享。
# 最简单的办法就是让 R8 不要碰这个包下的枚举。

-keep enum com.grillpoet.docy.core.sort.** { *; }


# ============================================================
# 五、Android 组件
# ============================================================
#
# AndroidManifest.xml 里声明的组件由系统通过反射实例化，
# 类名和入口方法都不能被混淆。
#
# 当前 manifest 里的组件：
#   - DocyApp         (Application)
#   - MainActivity    (Activity)
#   - FileProvider    (ContentProvider，AndroidX 提供)
#
# Application 和 Activity 是直接继承关系，下面的规则覆盖。
# FileProvider 由 AndroidX 的 consumer rules 保留，不用管。

-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# View 的 XML 构造（Compose 底层封装了自定义 View，通用保护）
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}


# ============================================================
# 六、ViewModel
# ============================================================
#
# 当前应用所有 ViewModel 都用显式工厂创建：
#
#     viewModel(
#         key = "...",
#         factory = viewModelFactory {
#             initializer { FolderViewModel(path, app.prefs) }
#         }
#     )
#
# 理论上不需要无参构造。但保留规则只是两行，防止未来重构
# 时不小心改成默认 viewModel() 调用导致 Release 包崩溃。

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}


# ============================================================
# 七、归档库
# ============================================================
#
# ArchiveManager 直接调用具体类：
#   org.apache.commons.compress.archivers.zip.ZipFile
#   org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
#   org.apache.commons.compress.archivers.sevenz.SevenZFile
#   org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
#   com.github.junrar.Archive
#
# 这些类被直接 new 出来，R8 会自动保留。
# 不使用 ArchiveStreamFactory / CompressorStreamFactory 这类 SPI 工厂，
# 所以不用保留 ServiceLoader 相关配置。
#
# 只需压制三方库内部的 optional 依赖警告。

-dontwarn org.apache.commons.compress.**
-dontwarn com.github.junrar.**
-dontwarn org.tukaani.xz.**
-dontwarn org.slf4j.**


# ============================================================
# 八、Markdown
# ============================================================
#
# commonmark 的扩展在代码里显式注册：
#   TablesExtension.create()
#   StrikethroughExtension.create()
#   AutolinkExtension.create()
#   TaskListItemsExtension.create()
#
# 各种 Node 子类（Heading / Paragraph / Link / Code 等）通过
# Kotlin 的 when(node) { is Heading -> ... } 做类型检查，
# R8 会保留这些类的类型信息。
#
# 官方没有 consumer rules，兜底 -dontwarn 即可。

-dontwarn org.commonmark.**


# ============================================================
# 九、Coil3
# ============================================================
#
# DocyApp 通过 SingletonImageLoader.Factory 自定义 ImageLoader，
# 组件在代码里显式 add：
#   add(AnimatedImageDecoder.Factory())
#   add(GifDecoder.Factory())
#   add(VideoFrameDecoder.Factory())
#
# 这些类被显式引用，R8 会保留。
# Coil3 自带 consumer rules，覆盖了 ImageLoader / ImageRequest /
# 各种 Decoder 的反射入口。

-dontwarn coil3.**
-dontwarn okio.**
-dontwarn okhttp3.**


# ============================================================
# 十、Compose
# ============================================================
#
# Compose Compiler 生成的代码结构固定，
# AndroidX Compose 系列 AAR 都带完整 consumer rules。
# 这里只兜底，防止某些边缘版本漏声明。

-dontwarn androidx.compose.**


# ============================================================
# 十一、Kotlin / 协程
# ============================================================

-dontwarn kotlin.**
-dontwarn kotlin.reflect.**
-dontwarn kotlinx.coroutines.**


# ============================================================
# 十二、收尾
# ============================================================

-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.**