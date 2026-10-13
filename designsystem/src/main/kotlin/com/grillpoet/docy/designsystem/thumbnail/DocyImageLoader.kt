package com.grillpoet.docy.designsystem.thumbnail

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.video.VideoFrameDecoder
import okio.Path.Companion.toOkioPath

/**
 * 磁盘缓存上限。50MB。
 *
 * 文件管理器里缩略图很小（100x100 JPEG 约 5KB），
 * 50MB 大约能存 1 万张。用户浏览大量目录不会一下撑满，
 * 也避免无上限膨胀。
 */
private const val DISK_CACHE_BYTES = 50L * 1024 * 1024

/** 磁盘缓存目录名。放在 [Context.getCacheDir] 下。 */
private const val DISK_CACHE_DIR = "image_cache"

/**
 * 构造 docy 的 Coil [ImageLoader]。
 *
 * 由 [com.grillpoet.docy.app.DocyApp] 在启动时调用一次。
 * 集中管理组件注册和缓存策略，方便后续调整。
 *
 * 已注册组件：
 *  - [VideoFrameDecoder]：视频文件抽第一帧作为缩略图
 *  - [AnimatedImageDecoder] / [GifDecoder]：GIF 和动图支持
 *
 * 缓存策略：
 *  - 内存：Coil 默认（屏幕内存占用的一部分）
 *  - 磁盘：[DISK_CACHE_BYTES] 上限，超出后按 LRU 淘汰
 *
 * @param context 用于创建缓存目录和作为 ImageLoader 的上下文。
 * @return 配置好的 [ImageLoader] 单例。
 */
fun buildDocyImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+：系统 ImageDecoder，支持 GIF / 动画 WebP / HEIF
                add(AnimatedImageDecoder.Factory())
            } else {
                // API 26-27：退回旧 GifDecoder，仅支持 GIF
                add(GifDecoder.Factory())
            }
            // 视频抽帧
            add(VideoFrameDecoder.Factory())
        }
        .diskCache {
            DiskCache.Builder()
                .directory(
                    context.cacheDir.resolve(DISK_CACHE_DIR).toOkioPath()
                )
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .build()
}
