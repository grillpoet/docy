package com.grillpoet.docy.editor.love

import java.io.File

object LoveProject {
    fun isProject(dir: File): Boolean {
        val main = File(dir, "main.lua")
        return main.isFile && main.length() > 0
    }

    fun conf(dir: File): File? = File(dir, "conf.lua").takeIf { it.isFile }
}