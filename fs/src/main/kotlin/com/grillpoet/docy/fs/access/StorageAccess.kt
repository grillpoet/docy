package com.grillpoet.docy.fs.access

import android.os.Build
import android.os.Environment
import java.io.File

object StorageAccess {
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true

    fun primaryRoots(): List<String> {
        val roots = mutableListOf<String>()
        File("/storage/emulated/0").takeIf { it.isDirectory }?.let { roots += it.absolutePath }
        System.getenv("SECONDARY_STORAGE")
            ?.split(":")?.filter { it.isNotBlank() }
            ?.forEach { p -> File(p).takeIf { it.isDirectory }?.let { roots += it.absolutePath } }
        return roots
    }
}