package com.grillpoet.docy.fs.ops

import com.grillpoet.docy.core.model.FileItem
import java.io.File

enum class ConflictResolution { SKIP, OVERWRITE, RENAME }

object FileOps {

    fun list(dir: File): List<FileItem> = dir.listFiles()?.map {
        FileItem(
            name = it.name,
            path = it.absolutePath,
            isDirectory = it.isDirectory,
            size = if (it.isFile) it.length() else 0L,
            lastModified = it.lastModified()
        )
    } ?: emptyList()

    fun delete(targets: List<File>): Pair<Int, List<File>> {
        var ok = 0
        val failed = mutableListOf<File>()
        targets.forEach { if (it.deleteRecursively()) ok++ else failed += it }
        return ok to failed
    }

    fun rename(src: File, newName: String): File? {
        val dst = File(src.parentFile, newName)
        return if (src.renameTo(dst)) dst else null
    }

    fun copy(src: File, dstDir: File, newName: String? = null): File? {
        return runCatching {
            val name = newName ?: src.name
            val dst = File(dstDir, name)
            if (src.isDirectory) src.copyRecursively(dst, overwrite = false)
            else src.copyTo(dst, overwrite = false)
            dst
        }.getOrNull()
    }

    fun move(src: File, dstDir: File, newName: String? = null): File? {
        val name = newName ?: src.name
        val dst = File(dstDir, name)
        if (src.renameTo(dst)) return dst
        return runCatching {
            if (src.isDirectory) src.copyRecursively(dst, overwrite = false)
            else src.copyTo(dst, overwrite = false)
            src.deleteRecursively()
            dst
        }.getOrNull()
    }

    fun applyAndTransfer(
        src: File,
        dstDir: File,
        resolution: ConflictResolution,
        isMove: Boolean
    ): File? {
        val dst = File(dstDir, src.name)
        return when {
            !dst.exists() -> if (isMove) move(src, dstDir) else copy(src, dstDir)

            resolution == ConflictResolution.SKIP -> null

            resolution == ConflictResolution.OVERWRITE -> {
                dst.deleteRecursively()
                if (isMove) move(src, dstDir) else copy(src, dstDir)
            }

            resolution == ConflictResolution.RENAME -> {
                val newName = uniqueName(dstDir, src.name)
                if (isMove) move(src, dstDir, newName) else copy(src, dstDir, newName)
            }

            else -> null
        }
    }

    fun mkdir(parent: File, name: String): File? =
        File(parent, name).takeIf { !it.exists() && it.mkdirs() }

    fun newFile(parent: File, name: String): File? =
        File(parent, name).takeIf { !it.exists() && it.createNewFile() }

    fun uniqueName(dir: File, base: String): String {
        val ext = base.substringAfterLast('.', "")
        val stem = if (ext.isEmpty()) base else base.removeSuffix(".$ext")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext"
            if (!File(dir, candidate).exists()) return candidate
            n++
        }
    }
}
