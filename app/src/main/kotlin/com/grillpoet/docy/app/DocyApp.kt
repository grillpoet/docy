package com.grillpoet.docy.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.grillpoet.docy.app.di.DocyPreferences
import com.grillpoet.docy.designsystem.thumbnail.buildDocyImageLoader
import com.grillpoet.docy.editor.lint.BuiltinLinters

/**
 * Application 入口。
 *
 * 实现 [SingletonImageLoader.Factory] 定制 Coil 的 ImageLoader，
 * 让所有 Coil 调用共享同一个配置好的实例（含缩略图缓存和视频抽帧）。
 */
class DocyApp : Application(), SingletonImageLoader.Factory {

    val prefs: DocyPreferences by lazy { DocyPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        BuiltinLinters.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        buildDocyImageLoader(context)
}
