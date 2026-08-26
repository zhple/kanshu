package com.kanshu.reader.music

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 .ncm 容器解码（布局与 unlock-music / ncmdump 公开规范一致）。
 */
object NcmDecoder {
    private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val CORE_KEY = byteArrayOf(
        0x68, 0x7A, 0x48, 0x52, 0x41, 0x6D, 0x73, 0x6F,
        0x35, 0x6B, 0x49, 0x6E, 0x62, 0x61, 0x78, 0x57
    )
    private val META_KEY = byteArrayOf(
        0x23, 0x31, 0x34, 0x6C, 0x6A, 0x6B, 0x5F, 0x21,
        0x5C, 0x5D, 0x26, 0x30, 0x55, 0x3C, 0x27, 0x28
    )
    private val KEY_PREFIX = "neteasecloudmusic".toByteArray(Charsets.US_ASCII)
    private val META_PREFIX = "163 key(Don't modify):".toByteArray(Charsets.US_ASCII)
    private val MUSIC_PREFIX = "music:".toByteArray(Charsets.US_ASCII)

    data class DecodedNcm(
        val audioBytes: ByteArray,
        val ext: String,
        val title: String?,
        val artist: String?,
        val album: String?
    )

    fun isNcmMagic(data: ByteArray): Boolean {
        return data.size >= 8 && data.copyOfRange(0, 8).contentEquals(MAGIC)
    }

    fun isNcmMagic(input: InputStream): Boolean {
        input.mark(8)
        val header = readFully(input, 8)
        input.reset()
        return isNcmMagic(header)
    }

    fun decode(input: InputStream): DecodedNcm {
        val data = input.readBytes()
        return decode(data)
    }

    fun decode(data: ByteArray): DecodedNcm {
        NcmReader(data).use { reader ->
            val header = reader.readBytes(10)
            require(header.copyOfRange(0, 8).contentEquals(MAGIC)) { "不是有效的 NCM 文件" }

            val rc4Key = readKey(reader)
            val meta = readMetadata(reader)
            val coverLen = reader.readIntLE()
            if (coverLen > 0) reader.readBytes(coverLen)
            val audio = readAudio(reader, rc4Key)
            require(audio.isNotEmpty()) { "NCM 解密后音频为空" }
            require(isValidAudio(audio)) { "NCM 解密失败，音频格式无效" }

            val ext = normalizeExt(meta.format, audio)
            return DecodedNcm(
                audioBytes = audio,
                ext = ext,
                title = meta.title,
                artist = meta.artist,
                album = meta.album
            )
        }
    }

    private data class ParsedMeta(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val format: String? = null
    )

    private fun readKey(reader: NcmReader): ByteArray {
        val length = reader.readIntLE()
        require(length in 1..4096) { "NCM 密钥段损坏" }
        val block = reader.readBytes(length)
        for (i in block.indices) block[i] = (block[i].toInt() xor 0x64).toByte()
        val unwrapped = aesEcbDecrypt(block, CORE_KEY)
        require(unwrapped.size >= KEY_PREFIX.size) { "NCM 密钥解密失败" }
        require(unwrapped.copyOfRange(0, KEY_PREFIX.size).contentEquals(KEY_PREFIX)) {
            "NCM 密钥格式无效"
        }
        return unwrapped.copyOfRange(KEY_PREFIX.size, unwrapped.size)
    }

    private fun readMetadata(reader: NcmReader): ParsedMeta {
        val length = reader.readIntLE()
        if (length == 0) {
            reader.skip(9)
            return ParsedMeta()
        }
        val block = reader.readBytes(length)
        reader.skip(9)
        return runCatching { parseMetadataBlock(block) }.getOrElse { ParsedMeta() }
    }

    private fun parseMetadataBlock(block: ByteArray): ParsedMeta {
        for (i in block.indices) block[i] = (block[i].toInt() xor 0x63).toByte()
        require(block.size >= META_PREFIX.size) { "NCM 元数据损坏" }
        val encoded = block.copyOfRange(META_PREFIX.size, block.size)
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val unwrapped = aesEcbDecrypt(decoded, META_KEY)
        val jsonStart = unwrapped.indexOfFirst { it == '{'.code.toByte() }
        require(jsonStart >= 0) { "NCM 元数据解析失败" }
        val json = JSONObject(
            String(unwrapped.copyOfRange(jsonStart, unwrapped.size), Charsets.UTF_8)
        )
        return ParsedMeta(
            title = json.optString("musicName").trim().ifBlank { null },
            album = json.optString("album").trim().ifBlank { null },
            artist = parseArtist(json),
            format = json.optString("format").trim().ifBlank { null }
        )
    }

    private fun parseArtist(json: JSONObject): String? {
        val artistNode = json.opt("artist") ?: return null
        return when (artistNode) {
            is String -> artistNode.trim().ifBlank { null }
            is JSONArray -> {
                val names = buildList {
                    for (i in 0 until artistNode.length()) {
                        when (val item = artistNode.opt(i)) {
                            is JSONArray -> {
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

    private fun readAudio(reader: NcmReader, rc4Key: ByteArray): ByteArray {
        val cipher = NcmCipher(rc4Key)
        val audio = ByteArrayOutputStream()
        val buffer = ByteArray(0x8000)
        while (true) {
            val read = reader.read(buffer)
            if (read <= 0) break
            cipher.decrypt(buffer, 0, read)
            audio.write(buffer, 0, read)
        }
        return audio.toByteArray()
    }

    private fun normalizeExt(format: String?, audio: ByteArray): String {
        if (!format.isNullOrBlank()) {
            val ext = format.trim().removePrefix(".").lowercase()
            if (ext in setOf("mp3", "flac", "m4a", "ogg", "wav", "aac")) {
                return ".$ext"
            }
        }
        return when {
            looksLikeFlac(audio) -> ".flac"
            looksLikeMp3(audio) -> ".mp3"
            looksLikeM4a(audio) -> ".m4a"
            else -> ".mp3"
        }
    }

    private fun isValidAudio(audio: ByteArray): Boolean {
        return looksLikeMp3(audio) || looksLikeFlac(audio) || looksLikeM4a(audio)
    }

    private fun looksLikeFlac(audio: ByteArray): Boolean {
        return audio.size >= 4 &&
            audio[0] == 'f'.code.toByte() &&
            audio[1] == 'L'.code.toByte() &&
            audio[2] == 'a'.code.toByte() &&
            audio[3] == 'C'.code.toByte()
    }

    private fun looksLikeMp3(audio: ByteArray): Boolean {
        if (audio.size >= 3 &&
            audio[0] == 'I'.code.toByte() &&
            audio[1] == 'D'.code.toByte() &&
            audio[2] == '3'.code.toByte()
        ) {
            return true
        }
        return audio.size >= 2 &&
            audio[0] == 0xFF.toByte() &&
            (audio[1].toInt() and 0xE0) == 0xE0
    }

    private fun looksLikeM4a(audio: ByteArray): Boolean {
        return audio.size >= 8 && String(audio, 4, 4, Charsets.US_ASCII) == "ftyp"
    }

    private fun aesEcbDecrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(ciphertext)
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var off = 0
        while (off < length) {
            val read = input.read(bytes, off, length - off)
            if (read < 0) break
            off += read
        }
        return bytes.copyOf(off)
    }

    private class NcmReader(private val data: ByteArray) : AutoCloseable {
        private val input = ByteArrayInputStream(data)

        fun readIntLE(): Int {
            val b0 = input.read()
            val b1 = input.read()
            val b2 = input.read()
            val b3 = input.read()
            if (b0 or b1 or b2 or b3 < 0) throw EOFException("NCM 文件过短")
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun readBytes(length: Int): ByteArray {
            val bytes = ByteArray(length)
            var off = 0
            while (off < length) {
                val read = input.read(bytes, off, length - off)
                if (read < 0) throw EOFException("NCM 文件过短")
                off += read
            }
            return bytes
        }

        fun read(buffer: ByteArray): Int = input.read(buffer)

        fun skip(n: Int) {
            val skipped = input.skip(n.toLong())
            if (skipped < n) throw EOFException("NCM 文件过短")
        }

        override fun close() {
            input.close()
        }
    }
}
