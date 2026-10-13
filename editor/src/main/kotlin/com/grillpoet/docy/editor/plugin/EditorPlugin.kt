package com.grillpoet.docy.editor.plugin

import com.grillpoet.docy.core.plugin.RunSpec
import java.io.File

interface Runner {
    suspend fun run(spec: RunSpec, ctx: RunContext): Result<String>
}

interface RunContext {
    val file: File
    val dir: File
    val allFilesInDir: List<File>
}
