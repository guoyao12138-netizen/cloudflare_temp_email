package com.vertexchat.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class SearchTool(@Volatile var http: OkHttpClient, private val gson: Gson = Gson()) {
    fun updateHttp(http: OkHttpClient) { this.http = http }

    /** Brave Search API (free tier: 2000 queries/month). apiKey from api.search.brave.com */
    suspend fun search(query: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.search.brave.com/res/v1/web/search?q=$encoded&count=5"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("X-Subscription-Token", apiKey)
            .build()

        val response = http.newCall(request).execute()
        val body = response.body?.string().orEmpty()

        if (!response.isSuccessful) return@withContext "Search failed (${response.code})"

        runCatching {
            val parsed = gson.fromJson(body, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val results = parsed["web"] as? Map<String, Any?> ?: return@runCatching "No results"
            @Suppress("UNCHECKED_CAST")
            val items = results["results"] as? List<Map<String, Any?>> ?: return@runCatching "No results"
            items.take(5).joinToString("\n\n") { item ->
                val title = item["title"] as? String ?: ""
                val desc = item["description"] as? String ?: ""
                val url2 = item["url"] as? String ?: ""
                "**$title**\n$desc\n$url2"
            }
        }.getOrElse { "Search error: ${it.message}" }
    }

    companion object {
        val TOOL_DEFINITION = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf(
                    "type" to "string",
                    "description" to "The search query",
                )
            ),
            "required" to listOf("query"),
        )
    }
}
