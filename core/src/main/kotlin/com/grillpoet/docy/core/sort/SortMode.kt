package com.grillpoet.docy.core.sort

import com.grillpoet.docy.core.model.FileItem

enum class SortMode { NAME, SIZE, MODIFIED, TYPE, EXTENSION }
enum class SortOrder { ASC, DESC }
enum class ViewMode { LIST, GRID }

data class SortSpec(
    val mode: SortMode = SortMode.NAME,
    val order: SortOrder = SortOrder.ASC,
    val foldersFirst: Boolean = true
)

object FileSorter {
    fun sort(items: List<FileItem>, spec: SortSpec): List<FileItem> {
        val cmp: Comparator<FileItem> = when (spec.mode) {
            SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.SIZE -> compareBy { it.size }
            SortMode.MODIFIED -> compareBy { it.lastModified }
            SortMode.TYPE -> compareBy<FileItem> { it.kind.ordinal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.extension }
            SortMode.EXTENSION -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.extension }
        }
        val ordered = if (spec.order == SortOrder.ASC) cmp else cmp.reversed()
        return if (!spec.foldersFirst) items.sortedWith(ordered)
        else items.sortedWith(
            compareByDescending<FileItem> { it.isDirectory }.then(ordered)
        )
    }
}
