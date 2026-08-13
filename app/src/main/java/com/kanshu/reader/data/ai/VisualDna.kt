package com.kanshu.reader.data.ai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class VisualPerson(
    val id: String,
    val name: String,
    val gender: String,
    val lock: String
)

data class VisualDna(
    val artStyle: String,
    val seed: Long,
    val negative: String,
    val characters: List<VisualPerson>,
    val user: VisualPerson
) {
    fun toJson(): String {
        val root = JSONObject()
            .put("artStyle", artStyle)
            .put("seed", seed)
            .put("negative", negative)
        val chars = JSONArray()
        characters.forEach { p ->
            chars.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("gender", p.gender)
                    .put("lock", p.lock)
            )
        }
        root.put("characters", chars)
        root.put(
            "user",
            JSONObject()
                .put("id", user.id)
                .put("name", user.name)
                .put("gender", user.gender)
                .put("lock", user.lock)
        )
        return root.toString()
    }

    companion object {
        fun parse(raw: String): VisualDna {
            val obj = JSONObject(extractJson(raw))
            val charsArr = obj.optJSONArray("characters") ?: JSONArray()
            val characters = buildList {
                for (i in 0 until charsArr.length()) {
                    val c = charsArr.getJSONObject(i)
                    add(
                        VisualPerson(
                            id = c.optString("id").ifBlank { "npc$i" },
                            name = c.optString("name").ifBlank { "Character" },
                            gender = normalizeGender(c.optString("gender")),
                            lock = c.optString("lock").trim()
                        )
                    )
                }
            }.filter { it.lock.isNotBlank() }
            val userObj = obj.optJSONObject("user") ?: JSONObject()
            val user = VisualPerson(
                id = userObj.optString("id").ifBlank { "user" },
                name = userObj.optString("name").ifBlank { "User" },
                gender = normalizeGender(userObj.optString("gender")),
                lock = userObj.optString("lock").trim().ifBlank {
                    "East Asian young adult, consistent face, same gender every time"
                }
            )
            require(characters.isNotEmpty()) { "Visual DNA 缺少角色外貌锁" }
            val seed = obj.optLong("seed").takeIf { it > 0 } ?: Random.nextLong(100_000, 999_999_999)
            return VisualDna(
                artStyle = obj.optString("artStyle").ifBlank {
                    "cinematic anime illustration, detailed, consistent character design"
                },
                seed = seed,
                negative = obj.optString("negative").ifBlank {
                    "gender swap, different gender, face morph, identity change, deformed, bad anatomy, low quality"
                },
                characters = characters,
                user = user
            )
        }

        private fun normalizeGender(raw: String): String {
            val g = raw.trim().lowercase()
            return when {
                g.startsWith("f") || g.contains("女") -> "female"
                g.startsWith("m") || g.contains("男") -> "male"
                else -> g.ifBlank { "other" }
            }
        }

        private fun extractJson(raw: String): String {
            val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.getOrNull(1)?.trim()
            if (!fenced.isNullOrBlank() && fenced.startsWith("{")) return fenced
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "无法解析 Visual DNA" }
            return raw.substring(start, end + 1)
        }
    }
}

data class SceneImageSpec(
    val prompt: String,
    val width: Int = 768,
    val height: Int = 1024
)
