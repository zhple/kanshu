package com.kanshu.reader.reader

/**
 * 写作草稿中的图片占位标记：[[IMG:相对路径或绝对路径]]
 */
object WriteMarkers {
    val imageRegex = Regex("""\[\[IMG:([^\]]+)]]""")

    fun imageMarker(path: String): String = "[[IMG:$path]]"

    fun stripImagesForPlainText(content: String): String {
        return imageRegex.replace(content) { match ->
            val name = match.groupValues[1].substringAfterLast('/').substringAfterLast('\\')
            "【图片：$name】"
        }
    }
}
