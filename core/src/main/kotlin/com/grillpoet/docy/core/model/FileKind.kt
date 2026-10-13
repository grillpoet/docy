package com.grillpoet.docy.core.model

/**
 * 仅用于图标区分。不生成缩略图。
 */
enum class FileKind {
    FOLDER, TEXT, CODE, IMAGE, VIDEO, AUDIO, ARCHIVE, PDF, APK, UNKNOWN;

    companion object {
        private val textExt = setOf(
            "txt","md","log","ini","cfg","conf","json","xml","yaml","yml",
            "properties","csv"
        )
        private val codeExt = setOf(
            "kt","java","py","js","ts","c","cpp","h","hpp","rs","go","rb","php",
            "sh","ps1","gradle","kts","html","css","sql","lua"
        )
        private val imageExt = setOf("png","jpg","jpeg","gif","webp","bmp","svg","heic")
        private val videoExt = setOf("mp4","mkv","avi","mov","webm","flv","3gp")
        private val audioExt = setOf("mp3","flac","wav","ogg","m4a","aac","opus")
        private val archiveExt = setOf("zip","rar","7z","tar","gz","bz2","xz")

        fun of(ext: String, isDirectory: Boolean): FileKind = when {
            isDirectory -> FOLDER
            ext in imageExt -> IMAGE
            ext in videoExt -> VIDEO
            ext in audioExt -> AUDIO
            ext in archiveExt -> ARCHIVE
            ext == "pdf" -> PDF
            ext == "apk" -> APK
            ext in codeExt -> CODE
            ext in textExt -> TEXT
            else -> UNKNOWN
        }
    }
}