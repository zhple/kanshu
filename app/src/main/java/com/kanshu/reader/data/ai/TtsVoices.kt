package com.kanshu.reader.data.ai

/**
 * MiniMax 系统音色（精选中文向）。voiceId 为官方 voice_id。
 * 列表可后续扩展；无效 ID 会在合成时由 API 报错。
 */
data class TtsVoice(
    val id: String,
    val label: String,
    val gender: String, // 女 / 男
    val style: String
)

object TtsVoices {
    val all: List<TtsVoice> = listOf(
        // 女
        TtsVoice("female-shaonv", "少女", "女", "可爱清亮"),
        TtsVoice("female-tianmei", "甜美女生", "女", "甜柔软萌"),
        TtsVoice("female-yujie", "御姐", "女", "成熟冷感"),
        TtsVoice("female-chengshu", "成熟女性", "女", "稳重温柔"),
        TtsVoice("presenter_female", "女播音", "女", "清晰端庄"),
        TtsVoice("audiobook_female_1", "有声书女1", "女", "叙事柔和"),
        TtsVoice("audiobook_female_2", "有声书女2", "女", "叙事沉稳"),
        TtsVoice("Chinese (Mandarin)_Sweet_Lady", "甜美女声", "女", "软甜口语"),
        TtsVoice("Chinese (Mandarin)_Warm-Hearted_Girl", "暖心女孩", "女", "亲切温暖"),
        TtsVoice("Chinese (Mandarin)_Attractive_Girl", "魅力女声", "女", "活力俏皮"),
        TtsVoice("Chinese (Mandarin)_Wise_Women", "知性女声", "女", "成熟知性"),
        TtsVoice("Chinese (Mandarin)_News_Anchor", "女主播", "女", "新闻播报"),
        // 男
        TtsVoice("male-qn-qingse", "青涩少年", "男", "清爽少年"),
        TtsVoice("male-qn-jingying", "精英青年", "男", "成熟干练"),
        TtsVoice("male-qn-badao", "霸道青年", "男", "强势低沉"),
        TtsVoice("male-qn-daxuesheng", "大学生", "男", "阳光自然"),
        TtsVoice("presenter_male", "男播音", "男", "清晰稳重"),
        TtsVoice("audiobook_male_1", "有声书男1", "男", "叙事沉稳"),
        TtsVoice("audiobook_male_2", "有声书男2", "男", "叙事醇厚"),
        TtsVoice("clever_boy", "聪明男孩", "男", "少年感"),
        TtsVoice("cute_boy", "可爱男孩", "男", "稚气可爱"),
        TtsVoice("Chinese (Mandarin)_Gentleman", "绅士男声", "男", "温文尔雅"),
        TtsVoice("Chinese (Mandarin)_Reliable_Executive", "可靠高管", "男", "成熟稳重"),
        TtsVoice("Chinese (Mandarin)_Casual_Guy", "随性男生", "男", "轻松口语"),
        TtsVoice("Chinese (Mandarin)_Humorous_Elder", "幽默长辈", "男", "慈祥幽默"),
        TtsVoice("Chinese (Mandarin)_Unrestrained_Young_Man", "不羁青年", "男", "洒脱少年")
    )

    val defaultId: String = "female-tianmei"

    fun find(id: String): TtsVoice =
        all.firstOrNull { it.id == id } ?: all.first { it.id == defaultId }
}
