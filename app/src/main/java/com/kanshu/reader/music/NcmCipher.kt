package com.kanshu.reader.music

/**
 * 网易云 .ncm 流密码（与 unlock-music 一致）。
 */
internal class NcmCipher(key: ByteArray) {
    private val box = IntArray(256)
    private var position = 0

    init {
        require(key.isNotEmpty()) { "NCM RC4 key must not be empty" }
        for (i in 0 until 256) box[i] = i
        var j = 0
        for (i in 0 until 256) {
            j = (j + box[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val swap = box[i]
            box[i] = box[j]
            box[j] = swap
        }
    }

    fun decrypt(data: ByteArray, offset: Int, length: Int) {
        val end = offset + length
        for (k in offset until end) {
            position = (position + 1) and 0xFF
            val i = position
            val j = (box[i] + i) and 0xFF
            data[k] = (data[k].toInt() xor box[(box[i] + box[j]) and 0xFF]).toByte()
        }
    }
}
