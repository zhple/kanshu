package com.kanshu.reader.data.remote

import org.json.JSONObject

data class RemoteBookSpec(
    val id: String,
    val title: String,
    val author: String,
    val file: String,
    val format: String,
    /** 远程分类名；空则视为「仓库书」 */
    val folder: String = DEFAULT_REMOTE_FOLDER,
    /** 阅读进度（更新前备份 / 同步后恢复） */
    val chapterIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastReadAt: Long = 0L
)

data class RemoteCatalog(
    val version: Int,
    val folders: List<String>,
    val books: List<RemoteBookSpec>
)

const val DEFAULT_REMOTE_FOLDER = "仓库书"

object CatalogParser {
    fun parse(json: String): RemoteCatalog {
        val root = JSONObject(json)
        val folderNames = linkedSetOf<String>()
        val foldersArr = root.optJSONArray("folders")
        if (foldersArr != null) {
            for (i in 0 until foldersArr.length()) {
                val name = foldersArr.optString(i).trim()
                if (name.isNotBlank()) folderNames += name
            }
        }

        val arr = root.optJSONArray("books")
        val books = mutableListOf<RemoteBookSpec>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.optString("id").trim()
                val file = item.optString("file").trim()
                if (id.isBlank() || file.isBlank()) continue
                val folder = item.optString("folder").trim().ifBlank { DEFAULT_REMOTE_FOLDER }
                folderNames += folder
                books += RemoteBookSpec(
                    id = id,
                    title = item.optString("title").ifBlank { id },
                    author = item.optString("author").ifBlank { "仓库默认" },
                    file = file,
                    format = item.optString("format").ifBlank { "EPUB" },
                    folder = folder,
                    chapterIndex = item.optInt("chapterIndex", 0).coerceAtLeast(0),
                    scrollOffset = item.optInt("scrollOffset", 0).coerceAtLeast(0),
                    lastReadAt = item.optLong("lastReadAt", 0L).coerceAtLeast(0L)
                )
            }
        }
        if (folderNames.isEmpty()) folderNames += DEFAULT_REMOTE_FOLDER
        return RemoteCatalog(
            version = root.optInt("version", 1),
            folders = folderNames.toList(),
            books = books
        )
    }
}
