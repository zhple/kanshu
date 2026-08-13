package com.kanshu.reader.data.ai

/**
 * 会话级 seed 保证不同聊天底色不同；消息级 seed 保证同会话不同轮次画面不同。
 * 角色一致性主要靠 Visual DNA 的 lock 原文复用，而不是跨会话共用同一个 seed。
 */
object ImageSeeds {
    private const val MAX = 9_999_999_999L

    fun sessionBase(sessionId: Long, preferred: Long = 0L): Long {
        val raw = if (preferred > 0L) {
            preferred xor (sessionId * 1_000_003L)
        } else {
            sessionId * 2_654_435_761L xor (sessionId shl 17)
        }
        return normalize(raw)
    }

    fun forMessage(sessionSeed: Long, messageId: Long, promptFingerprint: Int): Long {
        val raw = sessionSeed xor
            (messageId * 1_000_033L) xor
            ((promptFingerprint.toLong() and 0xffffffffL) * 97L)
        return normalize(raw)
    }

    private fun normalize(raw: Long): Long {
        var v = raw % MAX
        if (v < 0) v += MAX
        if (v == 0L) v = 1L
        return v
    }
}
