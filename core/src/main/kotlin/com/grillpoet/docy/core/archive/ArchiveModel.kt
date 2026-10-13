package com.grillpoet.docy.core.archive

enum class ArchiveFormat { ZIP, RAR, SEVEN_Z, TAR, GZ }

data class ArchiveEntry(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long = 0L
)
