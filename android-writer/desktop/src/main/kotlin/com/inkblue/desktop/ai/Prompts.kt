package com.inkblue.desktop.ai

import com.inkblue.desktop.data.AppData
import com.inkblue.desktop.data.LoreCategory
import com.inkblue.desktop.data.StyleProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class GeneratedLore(val category: LoreCategory, val name: String, val content: String)

enum class LoreGenerator(
    val label: String,
    val description: String,
    val optionLabel: String?,
    val options: List<String>,
    val extraHint: String,
) {
    WORLD("世界观框架", "选择世界类型，一键生成整套互相自洽的世界观设定", "世界类型",
        listOf("东方玄幻", "仙侠修真", "西方奇幻", "都市异能", "科幻未来", "末世废土", "历史架空", "悬疑灵异"),
        "补充你的想法（可选）"),
    POWER("修炼体系", "设定境界表现力，生成完整力量体系", "境界表现力",
        listOf("内敛写实", "热血张扬", "毁天灭地"), "补充说明（可选）"),
    REGION("地区", "生成一处与已有世界观呼应的地域", "地区类型",
        listOf("城池", "宗门驻地", "秘境", "王朝国度", "荒野绝地"), "补充说明（可选）"),
    FACTION("势力", "生成一个有立场、有矛盾的势力组织", "势力类型",
        listOf("宗门", "王朝", "世家", "魔道组织", "商会"), "补充说明（可选）"),
    CHARACTER("单个角色", "生成一名角色：性格、外貌、背景", "角色定位",
        listOf("主角", "反派", "导师", "挚友/道侣", "配角"), "补充说明（可选）"),
    ITEM("物品法宝", "生成一件融入力量体系的关键物品", "物品类型",
        listOf("武器法宝", "丹药", "功法秘籍", "天材地宝"), "补充说明（可选）"),
    VILLAGE("新手村", "基于已有世界观，生成主角的开局之地", null, emptyList(), "补充说明（可选）"),
    CHARACTERS("出场角色", "批量生成开篇角色，命名风格统一", "生成数量",
        listOf("3 个", "5 个", "7 个"), "补充说明（可选）"),
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
                appendLine("每条 content 在 150-300 字之间，各条目相互呼应、逻辑自洽，并为后续剧情留出冲突与悬念。")
            }
            LoreGenerator.POWER -> buildString {
                appendLine("请为本作品生成一套完整的修炼/力量体系。")
                appendLine("境界表现力要求：${powerStyleDetail(option)}")
                append(supplement)
                appendLine("要求输出 1 条「设定」类别的体系总纲条目，content 包含：体系名称、由低到高的境界划分（8-12 个境界，逐一命名）、每个境界的能力表现、突破方式与瓶颈。可附加 1-2 条相关条目。")
            }
            LoreGenerator.REGION -> buildString {
                appendLine("请基于已有世界观，生成一处「$option」类型的地区。")
                append(supplement)
                appendLine("要求输出 1 条「地点」条目，content 包含：地理位置与环境风貌、人文与势力归属、与已有设定的关联、可供剧情展开的看点。可附加 1 条相关条目。")
            }
            LoreGenerator.FACTION -> buildString {
                appendLine("请基于已有世界观，生成一个「$option」类型的势力。")
                append(supplement)
                appendLine("要求输出 1 条「势力」条目，content 包含：名称由来与底蕴、规模与实力层级、核心人物与组织架构、立场主张、与其他势力的恩怨。可附加 1 条相关条目。")
            }
            LoreGenerator.CHARACTER -> buildString {
                appendLine("请基于已有世界观，生成一名定位为「$option」的角色。")
                append(supplement)
                appendLine("要求输出 1 条「人物」条目，name 为角色姓名（风格贴合世界观），content 依次包含：性格特点、外貌特征、背景来历、当前实力与所属势力、与主角或已有角色的关系、剧情潜力。")
            }
            LoreGenerator.ITEM -> buildString {
                appendLine("请基于已有世界观，生成一件「$option」类型的物品。")
                append(supplement)
                appendLine("要求输出 1 条「物品」条目，content 包含：名称由来与外观、品阶与效用、来历传承、当前下落、围绕它可能的争夺或机缘。")
            }
            LoreGenerator.VILLAGE -> buildString {
                appendLine("请基于已有世界观，为主角设计开局之地（“新手村”）。")
                append(supplement)
                appendLine("要求输出 1 条「地点」条目，content 包含：地理位置与环境、风土人情、与主要势力的关系、埋藏的机缘与潜在危机。可附加 1-2 条相关条目。")
            }
            LoreGenerator.CHARACTERS -> buildString {
                val count = option.filter { it.isDigit() }.ifBlank { "3" }
                appendLine("请基于已有世界观与开局设定，生成 $count 个开篇出场角色（包含主角；若作者补充另有要求则遵循补充）。")
                append(supplement)
                appendLine("每个角色输出 1 条「人物」条目，content 依次包含：性格特点、外貌特征、背景来历、与主角或其他角色的关系、开篇作用。所有角色姓名风格统一。")
            }
        }
        return body.trim() + "\n\n" + JSON_FORMAT
    }

    fun buildReviewPrompt(generator: LoreGenerator, option: String, extra: String, draftJson: String): String =
        buildString {
            appendLine("另一位 AI 作者为「${generator.label}」${if (option.isNotBlank()) "（要求：$option）" else ""}生成了以下设定草稿（JSON 数组）：")
            if (extra.isNotBlank()) appendLine("作者的补充要求：$extra")
            appendLine(draftJson)
            appendLine()
            appendLine("请以资深主编身份审校：修正与已有世界观的矛盾，补足薄弱细节，提升自洽性与可读性；可调整、合并或增删条目。")
            append(JSON_FORMAT)
        }.trim()

    fun toJson(entries: List<GeneratedLore>): String {
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().apply {
                put("category", it.category.label)
                put("name", it.name)
                put("content", it.content)
            })
        }
        return array.toString()
    }

    private fun powerStyleDetail(style: String) = when (style) {
        "内敛写实" -> "内敛写实——力量表现克制，境界差距体现在气息、细节与生死一线的博弈"
        "热血张扬" -> "热血张扬——招式华丽、战斗酣畅淋漓，突破伴随强烈气势表现"
        "毁天灭地" -> "毁天灭地——高境界强者移山填海乃至破碎虚空，世界尺度宏大"
        else -> style
    }

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
            val category = LoreCategory.entries.firstOrNull { it.label == obj.optString("category").trim() }
                ?: LoreCategory.OTHER
            result.add(GeneratedLore(category, name, content))
        }
        if (result.isEmpty()) throw IOException("AI 未返回有效的设定条目，请重试")
        return result
    }
}

object OutlinePrompts {
    private const val JSON_FORMAT =
        "请严格按照以下 JSON 数组格式输出，不要输出任何解释文字或 Markdown 代码块标记：\n" +
            "[{\"title\":\"阶段标题\",\"content\":\"剧情概要\"}]"

    fun buildPlanPrompt(count: Int, extra: String, hasExisting: Boolean): String = buildString {
        appendLine("请为本作品规划故事大纲。")
        if (hasExisting) appendLine("系统提示中已有部分大纲，请顺着已有大纲往后续排，不要重复已有阶段。")
        if (extra.isNotBlank()) appendLine("作者的补充想法：$extra")
        appendLine("生成 $count 个剧情阶段，按时间顺序排列。每个阶段 title 为简短标题；content 用 3-5 句话概括：核心冲突、关键事件、出场角色、结尾钩子。")
        appendLine("整体符合网文节奏：开局快、爽点密集、阶段性高潮逐级抬升，并与世界观设定一致。")
        append(JSON_FORMAT)
    }.trim()

    fun parsePlan(raw: String): List<Pair<String, String>> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) throw IOException("AI 返回的内容无法解析，请重试")
        val array = try {
            JSONArray(raw.substring(start, end + 1))
        } catch (e: Exception) {
            throw IOException("AI 返回的 JSON 格式有误，请重试")
        }
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            val content = obj.optString("content").trim()
            if (title.isNotEmpty() && content.isNotEmpty()) result.add(title to content)
        }
        if (result.isEmpty()) throw IOException("AI 未返回有效的大纲阶段，请重试")
        return result
    }
}

object StylePrompts {
    const val ANALYSIS_SYSTEM =
        "你是一位顶尖的文学风格分析师（文风 Agent）。你的任务是研读一段小说文本，提炼出一份可供另一位作者精确模仿的「文风指南」。指南必须基于文本证据。"

    fun buildAnalysisPrompt(sample: String): String = buildString {
        appendLine("请研读以下小说片段，输出一份文风指南，依次包含：")
        appendLine("1. 叙事视角与叙事距离；2. 句式与节奏；3. 用词特征；4. 对话风格；5. 描写偏好；6. 情绪基调与幽默感；7. 标志性技巧。")
        appendLine("每项给出具体特征描述并引用 1 个不超过 30 字的原文短例，最后总结「如何写得像这位作者」。直接输出指南正文。")
        appendLine()
        appendLine("小说片段：")
        append(sample)
    }.trim()
}

enum class EditorAction(val label: String, val description: String) {
    CONTINUE("续写", "结合世界观、大纲与上下文，自然续写约 300 字"),
    POLISH("润色", "润色选中的文字，使其更生动流畅"),
    MIMIC("文风仿写", "按文风档案续写，或改写选中文字"),
    IDEA("情节灵感", "给出 3 个后续情节走向建议"),
}

object EditorPrompts {

    fun buildSystem(data: AppData, bookId: Long, style: StyleProfile?): String {
        val book = data.books.firstOrNull { it.id == bookId }
        val lore = data.lore.filter { it.bookId == bookId }
        val outline = data.outline.filter { it.bookId == bookId }.sortedBy { it.sortOrder }
        return buildString {
            appendLine("你是一位资深的中文网络小说写作助手，文笔自然流畅，擅长贴合作品既有的文风与节奏。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("以下是作品的世界观设定，创作时必须与这些设定保持一致：")
                lore.take(20).forEach {
                    appendLine("- [${it.category.label}] ${it.name}：${it.content.take(200).replace('\n', ' ')}")
                }
            }
            if (outline.isNotEmpty()) {
                appendLine("故事大纲（按顺序推进，续写时注意当前进度并向下一阶段自然衔接）：")
                outline.take(30).forEachIndexed { i, n ->
                    appendLine("${i + 1}. ${n.title}：${n.content.take(150).replace('\n', ' ')}")
                }
            }
            if (style != null) {
                appendLine("【文风指南】你必须严格模仿以下文风创作，它的优先级高于你的默认文风：")
                appendLine(style.analysis.take(3000))
            }
        }.trim()
    }

    fun buildUser(action: EditorAction, title: String, content: String, selection: String): String =
        when (action) {
            EditorAction.CONTINUE -> buildString {
                appendLine("以下是当前章节《$title》的结尾部分：")
                appendLine(content.takeLast(1500).ifBlank { "（本章尚无内容，请直接开篇。）" })
                appendLine()
                append("请自然地续写约 300 字。直接输出续写的正文，不要任何解释、标题或前缀。")
            }
            EditorAction.POLISH -> buildString {
                appendLine("请润色以下网文片段，保持原意、人称与情节不变，使文字更生动流畅：")
                appendLine(selection)
                appendLine()
                append("直接输出润色后的文字，不要任何解释或前缀。")
            }
            EditorAction.MIMIC -> buildString {
                if (selection.isNotBlank()) {
                    appendLine("请将以下片段改写为系统提示中文风指南所描述的风格，保持情节、人称与信息不变：")
                    appendLine(selection)
                    appendLine()
                    append("直接输出改写后的文字，不要任何解释或前缀。")
                } else {
                    appendLine("以下是当前章节《$title》的结尾部分：")
                    appendLine(content.takeLast(1500).ifBlank { "（本章尚无内容，请直接开篇。）" })
                    appendLine()
                    append("请严格按照系统提示中的文风指南自然续写约 300 字。直接输出正文，不要任何解释或前缀。")
                }
            }
            EditorAction.IDEA -> buildString {
                appendLine("以下是当前章节《$title》的结尾部分：")
                appendLine(content.takeLast(1200).ifBlank { "（本章尚无内容。）" })
                appendLine()
                append("请给出 3 个后续情节发展方向的建议，每个用 2-3 句话概括，按 1. 2. 3. 列出。")
            }
        }

    fun buildLoreSystem(data: AppData, bookId: Long): String {
        val book = data.books.firstOrNull { it.id == bookId }
        val lore = data.lore.filter { it.bookId == bookId }
        return buildString {
            appendLine("你是一位资深的中文网络小说世界观架构师，擅长设计自洽、有冲突张力、可持续展开剧情的设定。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("以下是作品已有的世界观设定，新生成的内容必须与它们保持一致并相互呼应：")
                lore.take(30).forEach {
                    appendLine("- [${it.category.label}] ${it.name}：${it.content.take(200).replace('\n', ' ')}")
                }
            } else {
                appendLine("该作品目前还没有任何世界观设定，你生成的内容将成为它的基础。")
            }
        }.trim()
    }

    fun buildOutlineSystem(data: AppData, bookId: Long): String {
        val book = data.books.firstOrNull { it.id == bookId }
        val lore = data.lore.filter { it.bookId == bookId }
        val outline = data.outline.filter { it.bookId == bookId }.sortedBy { it.sortOrder }
        return buildString {
            appendLine("你是一位资深的中文网络小说策划编辑，擅长规划节奏明快、爽点密集且逻辑自洽的长篇大纲。")
            if (book != null) {
                appendLine("当前作品：《${book.title}》")
                if (book.description.isNotBlank()) appendLine("作品简介：${book.description}")
            }
            if (lore.isNotEmpty()) {
                appendLine("作品的世界观设定（大纲必须与其一致）：")
                lore.take(20).forEach {
                    appendLine("- [${it.category.label}] ${it.name}：${it.content.take(150).replace('\n', ' ')}")
                }
            }
            if (outline.isNotEmpty()) {
                appendLine("已有大纲（按顺序）：")
                outline.forEachIndexed { i, n ->
                    appendLine("${i + 1}. ${n.title}：${n.content.take(120).replace('\n', ' ')}")
                }
            }
        }.trim()
    }
}
