package com.grillpoet.docy.app.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val FOLDER = "folder/{path}"
    const val EDITOR = "editor/{path}"
    const val MARKDOWN = "md/{path}"
    const val ARCHIVE = "archive/{path}"
    const val SETTINGS = "settings"

    fun folder(path: String) = "folder/${Uri.encode(path)}"
    fun editor(path: String) = "editor/${Uri.encode(path)}"
    fun markdown(path: String) = "md/${Uri.encode(path)}"
    fun archive(path: String) = "archive/${Uri.encode(path)}"
}
