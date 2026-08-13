package com.kanshu.reader.data.remote

import org.json.JSONObject

data class RemoteBookSpec(
    val id: String,
    val title: String,
    val author: String,
    val file: String,
    val format: String
)

data class RemoteCatalog(
    val version: Int,
    val books: List<RemoteBookSpec>
)

object CatalogParser {
    fun parse(json: String): RemoteCatalog {
        val root = JSONObject(json)
        val arr = root.optJSONArray("books")
        val books = mutableListOf<RemoteBookSpec>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.optString("id").trim()
                val file = item.optString("file").trim()
                if (id.isBlank() || file.isBlank()) continue
                books += RemoteBookSpec(
                    id = id,
                    title = item.optString("title").ifBlank { id },
                    author = item.optString("author").ifBlank { "仓库默认" },
                    file = file,
                    format = item.optString("format").ifBlank { "EPUB" }
                )
            }
        }
        return RemoteCatalog(
            version = root.optInt("version", 1),
            books = books
        )
    }
}
