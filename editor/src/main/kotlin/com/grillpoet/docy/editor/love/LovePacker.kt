package com.grillpoet.docy.editor.love

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * 打包目录为 .love（zip，main.lua 在根，去顶层目录）。
 * 输出到项目同级 .docy_cache/，跳过 . 开头的文件。
 */
class LovePacker {

    fun pack(dir: File): File {
        require(LoveProject.isProject(dir)) { "不是 love2d 项目" }
        val cacheDir = File(dir.parentFile, ".docy_cache").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val output = File(cacheDir, "${dir.name}.love")
        ZipArchiveOutputStream(output).use { zip -> addDir(dir, "", zip) }
        return output
    }

    private fun addDir(dir: File, prefix: String, zip: ZipArchiveOutputStream) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.name.startsWith(".")) return@forEach
            val entryName = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
            if (f.isDirectory) {
                zip.putArchiveEntry(ZipArchiveEntry("$entryName/"))
                zip.closeArchiveEntry()
                addDir(f, entryName, zip)
            } else {
                zip.putArchiveEntry(ZipArchiveEntry(entryName).apply {
                    size = f.length()
                    time = f.lastModified()
                })
                FileInputStream(f).use { it.copyTo(zip) }
                zip.closeArchiveEntry()
            }
        }
    }
}