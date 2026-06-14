package com.inkblue.writer.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 纯纯写作-style word count: every non-whitespace character counts. */
fun countWords(text: String): Int = text.count { !it.isWhitespace() }

fun formatWordCount(count: Int): String =
    if (count >= 10000) {
        String.format(Locale.CHINA, "%.1f 万字", count / 10000.0)
    } else {
        "$count 字"
    }

fun formatTime(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "MM-dd HH:mm" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.CHINA).format(millis)
}
