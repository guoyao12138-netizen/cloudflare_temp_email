package com.inkblue.writer.ai

import org.json.JSONArray
import java.io.IOException

/** Prompts for the outline-planning generator. */
object OutlinePrompts {

    private const val JSON_FORMAT =
        "请严格按照以下 JSON 数组格式输出，不要输出任何解释文字或 Markdown 代码块标记：\n" +
            "[{\"title\":\"阶段标题\",\"content\":\"剧情概要\"}]"

    fun buildPlanPrompt(count: Int, extra: String, hasExisting: Boolean): String = buildString {
        appendLine("请为本作品规划故事大纲。")
        if (hasExisting) {
            appendLine("系统提示中已有部分大纲，请顺着已有大纲往后续排，不要重复已有阶段。")
        }
        if (extra.isNotBlank()) appendLine("作者的补充想法：$extra")
        appendLine("生成 $count 个剧情阶段，按时间顺序排列。每个阶段 title 为简短标题；content 用 3-5 句话概括：核心冲突、关键事件、出场角色、结尾钩子。")
        appendLine("整体要符合网文节奏：开局快、爽点密集、阶段性高潮逐级抬升，且与系统提示中的世界观设定保持一致。")
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
            if (title.isEmpty() || content.isEmpty()) continue
            result.add(title to content)
        }
        if (result.isEmpty()) throw IOException("AI 未返回有效的大纲阶段，请重试")
        return result
    }
}

/** Prompts for the style-imitation agent: read sample text, distill a style guide. */
object StylePrompts {

    const val ANALYSIS_SYSTEM =
        "你是一位顶尖的文学风格分析师（文风 Agent）。你的任务是研读一段小说文本，" +
            "提炼出一份可供另一位作者精确模仿的「文风指南」。指南必须基于文本证据，而非泛泛而谈。"

    fun buildAnalysisPrompt(sample: String): String = buildString {
        appendLine("请研读以下小说片段，输出一份文风指南，依次包含：")
        appendLine("1. 叙事视角与叙事距离")
        appendLine("2. 句式与节奏（长短句配比、断句与分段习惯）")
        appendLine("3. 用词特征（口语/书面倾向、常用意象、特色词汇）")
        appendLine("4. 对话风格（对白密度、语气、称谓习惯）")
        appendLine("5. 描写偏好（环境/动作/心理的比重与写法）")
        appendLine("6. 情绪基调与幽默感")
        appendLine("7. 标志性技巧（重复、留白、比喻习惯等）")
        appendLine("每一项给出具体特征描述，并引用 1 个不超过 30 字的原文短例。")
        appendLine("最后用一段话总结「如何写得像这位作者」。直接输出指南正文，不要任何解释或前缀。")
        appendLine()
        appendLine("小说片段：")
        append(sample)
    }.trim()
}
