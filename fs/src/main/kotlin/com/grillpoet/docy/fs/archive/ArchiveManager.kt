package com.grillpoet.docy.fs.archive

import com.grillpoet.docy.core.archive.ArchiveEntry
import com.grillpoet.docy.core.archive.ArchiveFormat
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 压缩 / 解压统一入口。
 *  - ZIP：commons-compress
 *  - 7Z：commons-compress + xz
 *  - RAR：junrar（只读）
 *
 * 压缩 / 解压提供进度回调。回调在 IO 线程触发。
 * 进度按「已写文件数 / 总文件数」上报，目录不计入 total。
 */
class ArchiveManager {

    fun list(archive: File): List<ArchiveEntry> = when (archive.extension.lowercase()) {
        "zip" -> listZip(archive)
        "7z"  -> listSevenZ(archive)
        "rar" -> listRar(archive)
        else -> emptyList()
    }

    fun extract(
        archive: File,
        destDir: File,
        entries: List<String>? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ) {
        when (archive.extension.lowercase()) {
            "zip" -> extractZip(archive, destDir, entries, onProgress)
            "7z"  -> extractSevenZ(archive, destDir, entries, onProgress)
            "rar" -> extractRar(archive, destDir, entries, onProgress)
            else -> error("不支持的压缩格式：${archive.extension}")
        }
    }

    fun compress(
        sources: List<File>,
        dest: File,
        format: ArchiveFormat,
        onProgress: ((Int, Int) -> Unit)? = null
    ) {
        when (format) {
            ArchiveFormat.ZIP -> compressZip(sources, dest, onProgress)
            ArchiveFormat.SEVEN_Z -> compressSevenZ(sources, dest, onProgress)
            // RAR / TAR / GZ 只支持读取。UI 层不会传，但类型系统需要穷尽。
            ArchiveFormat.RAR, ArchiveFormat.TAR, ArchiveFormat.GZ ->
                throw IllegalArgumentException("不支持写入：$format")
        }
    }

    // ---------- zip ----------

    private fun listZip(f: File): List<ArchiveEntry> =
        ZipFile.builder().setFile(f).get().use { zip ->
            zip.entries.asSequence()
                .map { ArchiveEntry(it.name, it.size, it.isDirectory, it.time) }
                .toList()
        }

    private fun extractZip(
        archive: File,
        destDir: File,
        entries: List<String>?,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        ZipFile.builder().setFile(archive).get().use { zip ->
            val allNames = mutableListOf<String>()
            val it = zip.entries
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                if (entries == null || e.name in entries) allNames.add(e.name)
            }
            val total = allNames.size
            var current = 0

            val it2 = zip.entries
            while (it2.hasMoreElements()) {
                val e = it2.nextElement()
                if (entries != null && e.name !in entries) continue
                val out = File(destDir, e.name)
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input ->
                        FileOutputStream(out).use { input.copyTo(it) }
                    }
                }
                current++
                onProgress?.invoke(current, total)
            }
        }
    }

    private fun compressZip(
        sources: List<File>,
        dest: File,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val total = countFiles(sources)
        val counter = intArrayOf(0)
        ZipArchiveOutputStream(dest).use { out ->
            sources.forEach {
                addToZip(it, it.name, out, counter, total, onProgress)
            }
        }
        // 收尾对齐，避免 total 与实际不符时进度条停在中途
        onProgress?.invoke(total, total)
    }

    private fun addToZip(
        file: File,
        entryName: String,
        out: ZipArchiveOutputStream,
        counter: IntArray,
        total: Int,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        if (file.isDirectory) {
            out.putArchiveEntry(ZipArchiveEntry("$entryName/"))
            out.closeArchiveEntry()
            file.listFiles()?.forEach {
                addToZip(it, "$entryName/${it.name}", out, counter, total, onProgress)
            }
        } else {
            out.putArchiveEntry(ZipArchiveEntry(entryName).apply { size = file.length() })
            FileInputStream(file).use { it.copyTo(out) }
            out.closeArchiveEntry()
            counter[0]++
            onProgress?.invoke(counter[0], total)
        }
    }

    // ---------- 7z ----------

    private fun listSevenZ(f: File): List<ArchiveEntry> =
        SevenZFile.builder().setFile(f).get().use { sz ->
            generateSequence { sz.nextEntry }
                .map { ArchiveEntry(it.name, it.size, it.isDirectory, it.lastModifiedDate?.time ?: 0L) }
                .toList()
        }

    private fun extractSevenZ(
        archive: File,
        destDir: File,
        entries: List<String>?,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        SevenZFile.builder().setFile(archive).get().use { sz ->
            val total = runCatching {
                listSevenZ(archive).count { entries == null || it.path in entries }
            }.getOrDefault(0)
            var current = 0

            var e: SevenZArchiveEntry? = sz.nextEntry
            while (e != null) {
                if (entries == null || e.name in entries) {
                    val out = File(destDir, e.name)
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(8192)
                            var n = sz.read(buf)
                            while (n > 0) {
                                fos.write(buf, 0, n)
                                n = sz.read(buf)
                            }
                        }
                    }
                    current++
                    onProgress?.invoke(current, total)
                }
                e = sz.nextEntry
            }
        }
    }

    private fun compressSevenZ(
        sources: List<File>,
        dest: File,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val total = countFiles(sources)
        val counter = intArrayOf(0)
        SevenZOutputFile(dest).use { out ->
            sources.forEach {
                addToSevenZ(it, it.name, out, counter, total, onProgress)
            }
        }
        onProgress?.invoke(total, total)
    }

    private fun addToSevenZ(
        file: File,
        entryName: String,
        out: SevenZOutputFile,
        counter: IntArray,
        total: Int,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                addToSevenZ(it, "$entryName/${it.name}", out, counter, total, onProgress)
            }
        } else {
            val e = SevenZArchiveEntry().apply {
                name = entryName
                size = file.length()
            }
            out.putArchiveEntry(e)
            FileInputStream(file).use { fis ->
                val buf = ByteArray(8192)
                var n = fis.read(buf)
                while (n > 0) {
                    out.write(buf, 0, n)
                    n = fis.read(buf)
                }
            }
            out.closeArchiveEntry()
            counter[0]++
            onProgress?.invoke(counter[0], total)
        }
    }

    // ---------- rar ----------

    private fun listRar(f: File): List<ArchiveEntry> {
        com.github.junrar.Archive(f).use { rar ->
            val entries = mutableListOf<ArchiveEntry>()
            var header = rar.nextFileHeader()
            while (header != null) {
                entries.add(
                    ArchiveEntry(
                        header.fileName,
                        header.fullUnpackSize,
                        header.isDirectory,
                        header.mTime.time
                    )
                )
                header = rar.nextFileHeader()
            }
            return entries
        }
    }

    private fun extractRar(
        archive: File,
        destDir: File,
        entries: List<String>?,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        com.github.junrar.Archive(archive).use { rar ->
            val total = runCatching {
                listRar(archive).count { entries == null || it.path in entries }
            }.getOrDefault(0)
            var current = 0

            var h = rar.nextFileHeader()
            while (h != null) {
                if (entries == null || h.fileName in entries) {
                    val out = File(destDir, h.fileName)
                    if (h.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { rar.extractFile(h, it) }
                    }
                    current++
                    onProgress?.invoke(current, total)
                }
                h = rar.nextFileHeader()
            }
        }
    }

    // ---------- 工具 ----------

    private fun countFiles(sources: List<File>): Int {
        var count = 0
        fun walk(f: File) {
            if (f.isDirectory) {
                f.listFiles()?.forEach { walk(it) }
            } else {
                count++
            }
        }
        sources.forEach { walk(it) }
        return count.coerceAtLeast(1)
    }
}
