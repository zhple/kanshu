package com.kanshu.reader.music

import android.util.Base64
import org.json.JSONObject
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 .ncm 解密（仅本地导入时转为 mp3/flac，不上传加密格式）。
 */
object NcmDecoder {
    private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val CORE_KEY = "hzHRAmso5kInbaxW".toByteArray(Charsets.US_ASCII)
    private val META_KEY = byteArrayOf(
        0x23, 0x31, 0x34, 0x6c, 0x6a, 0x6b, 0x5f, 0x21,
        0x5c, 0x5d, 0x26, 0x30, 0x55, 0x3c, 0x27, 0x28
    )
    private val KEY_PREFIX = "neteasecloudmusic".toByteArray(Charsets.US_ASCII)
    private val META_PREFIX = "music:".toByteArray(Charsets.US_ASCII)

    data class DecodedNcm(
        val audioBytes: ByteArray,
        val ext: String,
        val title: String?,
        val artist: String?,
        val album: String?
    )

    fun decode(input: InputStream): DecodedNcm {
        val magic = input.readNBytes(8)
        require(magic.contentEquals(MAGIC)) { "不是有效的 NCM 文件" }

        input.skip(2)

        val keyLen = readU32Le(input)
        require(keyLen in 1..4096) { "NCM 密钥段损坏" }
        val keyBlob = input.readNBytes(keyLen)
        for (i in keyBlob.indices) {
            keyBlob[i] = (keyBlob[i].toInt() xor 0x64).toByte()
        }
        val rc4KeyFull = aesDecrypt(CORE_KEY, keyBlob)
        require(rc4KeyFull.size > KEY_PREFIX.size) { "NCM 密钥解密失败" }
        require(
            rc4KeyFull.copyOfRange(0, KEY_PREFIX.size).contentEquals(KEY_PREFIX)
        ) { "NCM 密钥格式无效" }
        val rc4Key = rc4KeyFull.copyOfRange(KEY_PREFIX.size, rc4KeyFull.size)
        val keyBox = buildKeyBox(rc4Key)

        var title: String? = null
        var artist: String? = null
        var album: String? = null

        val metaLen = readU32Le(input)
        if (metaLen > 0) {
            val metaBlob = input.readNBytes(metaLen)
            for (i in metaBlob.indices) {
                metaBlob[i] = (metaBlob[i].toInt() xor 0x63).toByte()
            }
            require(metaBlob.size > 22) { "NCM 元数据损坏" }
            val b64Section = String(metaBlob.copyOfRange(22, metaBlob.size), Charsets.UTF_8)
            val decodedMeta = Base64.decode(b64Section, Base64.DEFAULT)
            val metaPlain = aesDecrypt(META_KEY, decodedMeta)
            val jsonStart = metaPlain.indexOfFirst { it == '{'.code.toByte() }
            require(jsonStart >= 0) { "NCM 元数据解析失败" }
            val json = JSONObject(String(metaPlain.copyOfRange(jsonStart, metaPlain.size), Charsets.UTF_8))
            title = json.optString("musicName").trim().ifBlank { null }
            album = json.optString("album").trim().ifBlank { null }
            artist = parseArtist(json)
        }

        input.skip(9)

        readU32Le(input) // cover mime/type
        val coverSize = readU32Le(input)
        if (coverSize > 0) input.skip(coverSize.toLong())

        val encrypted = input.readBytes()
        require(encrypted.isNotEmpty()) { "NCM 音频为空" }
        val audio = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            audio[i] = (encrypted[i].toInt() xor decryptMask(keyBox, i).toInt()).toByte()
        }

        val ext = detectFormat(audio)
        return DecodedNcm(
            audioBytes = audio,
            ext = ext,
            title = title,
            artist = artist,
            album = album
        )
    }

    private fun parseArtist(json: JSONObject): String? {
        val artistNode = json.opt("artist") ?: return null
        return when (artistNode) {
            is String -> artistNode.trim().ifBlank { null }
            is org.json.JSONArray -> {
                val names = buildList {
                    for (i in 0 until artistNode.length()) {
                        val item = artistNode.opt(i)
                        when (item) {
                            is org.json.JSONArray -> {
                                if (item.length() > 1) {
                                    item.optString(1).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                                }
                            }
                            is String -> item.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                        }
                    }
                }
                names.joinToString(" / ").ifBlank { null }
            }
            else -> null
        }
    }

    private fun detectFormat(audio: ByteArray): String {
        if (audio.size >= 4 && audio[0] == 'f'.code.toByte() && audio[1] == 'L'.code.toByte() &&
            audio[2] == 'a'.code.toByte() && audio[3] == 'C'.code.toByte()
        ) {
            return ".flac"
        }
        if (audio.size >= 3 &&
            audio[0] == 'I'.code.toByte() && audio[1] == 'D'.code.toByte() && audio[2] == '3'.code.toByte()
        ) {
            return ".mp3"
        }
        if (audio.size >= 2 && audio[0] == 0xFF.toByte() && (audio[1].toInt() and 0xE0) == 0xE0) {
            return ".mp3"
        }
        if (audio.size >= 8 && String(audio, 4, 4, Charsets.US_ASCII) == "ftyp") {
            return ".m4a"
        }
        return ".mp3"
    }

    private fun buildKeyBox(key: ByteArray): ByteArray {
        val box = ByteArray(256) { it.toByte() }
        var last = 0
        var keyOffset = 0
        for (i in 0 until 256) {
            val swap = box[i].toInt() and 0xff
            val keyByte = key[keyOffset].toInt() and 0xff
            keyOffset = (keyOffset + 1) % key.size
            val c = (swap + last + keyByte) and 0xff
            box[i] = box[c]
            box[c] = swap.toByte()
            last = c
        }
        return box
    }

    private fun decryptMask(keyBox: ByteArray, offset: Int): Byte {
        val i = (offset + 1) and 0xff
        val j = keyBox[i].toInt() and 0xff
        val k = keyBox[(i + j) and 0xff].toInt() and 0xff
        return keyBox[(j + k) and 0xff]
    }

    private fun aesDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun readU32Le(input: InputStream): Int {
        val b = input.readNBytes(4)
        require(b.size == 4) { "NCM 文件过短" }
        return (b[0].toInt() and 0xff) or
            ((b[1].toInt() and 0xff) shl 8) or
            ((b[2].toInt() and 0xff) shl 16) or
            ((b[3].toInt() and 0xff) shl 24)
    }
}
