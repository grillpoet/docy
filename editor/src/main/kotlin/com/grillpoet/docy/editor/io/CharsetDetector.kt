package com.grillpoet.docy.editor.io

import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * 简易编码检测：BOM 优先，无 BOM 试 UTF-8，失败回退 GBK。
 */
object CharsetDetector {
    data class Result(val charset: Charset, val hasBom: Boolean)

    fun detect(bytes: ByteArray): Result {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) return Result(Charsets.UTF_8, true)

        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return Result(Charsets.UTF_16LE, true)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return Result(Charsets.UTF_16BE, true)

        return runCatching {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            Result(Charsets.UTF_8, false)
        }.getOrElse {
            Result(Charset.forName("GBK"), false)
        }
    }
}