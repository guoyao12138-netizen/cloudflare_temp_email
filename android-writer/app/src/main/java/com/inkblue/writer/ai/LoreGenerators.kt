package com.inkblue.writer.ai

import com.inkblue.writer.data.LoreCategory
import org.json.JSONArray
import java.io.IOException

/** One AI-generated worldbuilding entry, parsed from the model's JSON output. */
data class GeneratedLore(
    val category: LoreCategory,
    val name: String,
    val content: String,
)

/**
 * One-click worldbuilding generators. Each generator has an optional
 * single-select option row plus a free-text author supplement.
 */
enum class LoreGenerator(
    val label: String,
    val description: String,
    val optionLabel: String?,
    val options: List<String>,
    val extraHint: String,
) {
    WORLD(
        label = "世界观框架",
        description = "选择世界类型，一键生成整套互相自洽的世界观设定",
        optionLabel = "世界类型",
        options = listOf("东方玄幻", "仙侠修真", "西方奇幻", "都市异能", "科幻未来", "末世废土", "历史架空", "悬疑灵异"),
        extraHint = "补充你的想法（可选）：核心创意、基调、必须出现的元素……",
    ),
    POWER(
        label = "修炼体系",
        description = "设定境界表现力，生成境界划分与突破方式完整的力量体系",
        optionLabel = "境界表现力",
        options = listOf("内敛写实", "热血张扬", "毁天灭地"),
        extraHint = "补充说明（可选）：体系名称、灵感来源、特殊规则……",
    ),
    VILLAGE(
        label = "新手村",
        description = "基于已有世界观，生成主角的开局之地与隐藏机缘",
        optionLabel = null,
        options = emptyList(),
        extraHint = "补充说明（可选）：你希望的开局氛围、地点类型……",
    ),
    CHARACTERS(
        label = "出场角色",
        description = "基于世界观生成开篇角色：性格、外貌、背景，命名风格统一",
        optionLabel = "生成数量",
        options = listOf("3 个", "5 个", "7 个"),
        extraHint = "补充说明（可选）：主角方向、角色关系、名称风格偏好……",
    ),
}

object LorePrompts {

    private const val JSON_FORMAT =
        "请严格按照以下 JSON 数组格式输出，不要输出任何解释文字或 Markdown 代码块标记：\n" +
            "[{\"category\":\"人物|地点|物品|势力|设定\",\"name\":\"条目名称\",\"content\":\"详细设定内容\"}]"

    fun buildUserPrompt(generator: LoreGenerator, option: String, extra: String): String {
        val supplement = if (extra.isBlank()) "" else "作者的补充想法：$extra\n"
        val body = when (generator) {
            LoreGenerator.WORLD -> buildString {
                appendLine("请为一部「$option」类型的网络小说生成一套世界观框架设定。")
                append(supplement)
                appendLine("要求生成 5-8 条设定，覆盖：世界总纲（category 用「设定」）、主要地域版图（地点）、核心势力 2-3 个（势力）、关键规则或特殊体系（设定）。")
                appendLine("每条 content 在 150-300 字之间，各条目之间相互呼应、逻辑自洽，并为后续剧情留出冲突与悬念的空间。")
            }

            LoreGenerator.POWER -> buildString {
                appendLine("请为本作品生成一套完整的修炼/力量体系。")
                appendLine("境界表现力要求：${powerStyleDetail(option)}")
                append(supplement)
                appendLine("要求输出 1 条「设定」类别的体系总纲条目，content 必须包含：体系名称、由低到高的境界划分（8-12 个境界，逐一命名）、每个境界的能力表现、典型的突破方式与瓶颈。")
                appendLine("可以再附加 1-2 条相关条目（如核心功法、修炼资源，category 用「物品」或「设定」）。")
                appendLine("境界命名要贴合作品已有的世界观，力量表现严格符合上面的表现力要求。")
            }

            LoreGenerator.VILLAGE -> buildString {
                appendLine("请基于已有世界观，为主角设计开局之地（“新手村”）。")
                append(supplement)
                appendLine("要求输出 1 条「地点」条目，content 包含：地理位置与环境、风土人情、与世界观中主要势力的关系、埋藏的机缘与潜在危机。")
                appendLine("可以再附加 1-2 条相关条目（如当地的小势力、关键场所或人物）。")
            }

            LoreGenerator.CHARACTERS -> buildString {
                val count = option.filter { it.isDigit() }.ifBlank { "3" }
                appendLine("请基于已有世界观与开局设定，生成 $count 个开篇出场角色（包含主角；若作者补充中另有要求则遵循补充）。")
                append(supplement)
                appendLine("每个角色输出 1 条「人物」条目，name 为角色姓名，content 必须依次包含：性格特点、外貌特征、背景来历、与主角或其他角色的关系、在开篇剧情中的作用。")
                appendLine("所有角色的姓名风格必须统一，并贴合世界观类型；性格之间要有差异与张力。")
            }
        }
        return body.trim() + "\n\n" + JSON_FORMAT
    }

    private fun powerStyleDetail(style: String) = when (style) {
        "内敛写实" -> "内敛写实——力量表现克制，境界差距体现在气息、细节与生死一线的博弈，避免夸张特效"
        "热血张扬" -> "热血张扬——招式华丽、战斗酣畅淋漓，境界突破伴随强烈的气势与视觉表现"
        "毁天灭地" -> "毁天灭地——高境界强者翻手覆雨、移山填海乃至破碎虚空，世界尺度宏大"
        else -> style
    }

    /** Parse the model's JSON array, tolerating stray prose / code fences around it. */
    fun parseGenerated(raw: String): List<GeneratedLore> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) throw IOException("AI 返回的内容无法解析，请重试")
        val array = try {
            JSONArray(raw.substring(start, end + 1))
        } catch (e: Exception) {
            throw IOException("AI 返回的 JSON 格式有误，请重试")
        }
        val result = mutableListOf<GeneratedLore>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val content = obj.optString("content").trim()
            if (name.isEmpty() || content.isEmpty()) continue
            val label = obj.optString("category").trim()
            val category = LoreCategory.entries.firstOrNull { it.label == label } ?: LoreCategory.OTHER
            result.add(GeneratedLore(category, name, content))
        }
        if (result.isEmpty()) throw IOException("AI 未返回有效的设定条目，请重试")
        return result
    }
}
