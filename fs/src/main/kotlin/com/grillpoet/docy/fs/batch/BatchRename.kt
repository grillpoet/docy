package com.grillpoet.docy.fs.batch

import java.io.File

/**
 * 批量重命名。
 *  - withSuffix：自动加序号后缀
 *  - custom：每项提供新名字
 */
object BatchRename {

    fun withSuffix(
        targets: List<File>,
        suffix: String = "_",
        startIndex: Int = 1,
        digits: Int = 3
    ): List<Pair<File, File>> = targets.mapIndexed { i, src ->
        val ext = src.name.substringAfterLast('.', "")
        val base = if (ext.isEmpty()) src.name else src.name.removeSuffix(".$ext")
        val idx = (startIndex + i).toString().padStart(digits, '0')
        val newName = if (ext.isEmpty()) "$base$suffix$idx" else "$base$suffix$idx.$ext"
        src to File(src.parentFile, newName)
    }

    fun custom(plans: Map<File, String>): List<Pair<File, File>> =
        plans.map { (src, newName) -> src to File(src.parentFile, newName) }

    fun apply(plans: List<Pair<File, File>>): Pair<Int, List<String>> {
        var ok = 0
        val errors = mutableListOf<String>()
        plans.forEach { (src, dst) ->
            if (dst.exists()) {
                errors += "${src.name} → ${dst.name}：目标已存在"
                return@forEach
            }
            if (src.renameTo(dst)) ok++
            else errors += "${src.name}：重命名失败"
        }
        return ok to errors
    }
}