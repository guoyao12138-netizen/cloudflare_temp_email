package com.vertexchat.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vertexchat.data.model.McpServerConfig
import com.vertexchat.data.model.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class McpClient(@Volatile var http: OkHttpClient, private val gson: Gson = Gson()) {
    fun updateHttp(http: OkHttpClient) { this.http = http }

    suspend fun listTools(server: McpServerConfig): List<McpTool> = withContext(Dispatchers.IO) {
        val resp = rpc(server, "tools/list", emptyMap<String, Any>())
        val result = resp["result"]?.asJsonObject ?: return@withContext emptyList()
        val tools = result["tools"]?.asJsonArray ?: return@withContext emptyList()
        tools.map { el ->
            val obj = el.asJsonObject
            val schema = runCatching {
                gson.fromJson(obj["inputSchema"], Map::class.java) as Map<String, Any?>
            }.getOrElse { emptyMap() }
            McpTool(
                name = obj["name"]?.asString ?: "",
                description = obj["description"]?.asString ?: "",
                inputSchema = schema,
                serverId = server.id,
                serverName = server.name,
            )
        }.filter { it.name.isNotBlank() }
    }

    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        arguments: Map<String, Any?>,
    ): String = withContext(Dispatchers.IO) {
        val resp = rpc(server, "tools/call", mapOf("name" to toolName, "arguments" to arguments))
        val result = resp["result"]?.asJsonObject
        if (resp.containsKey("error")) {
            val err = resp["error"]?.asJsonObject
            throw IOException("MCP error: ${err?.get("message")?.asString ?: "unknown"}")
        }
        // Extract text from content array
        val content = result?.get("content")?.asJsonArray
        content?.joinToString("\n") { el ->
            val obj = el.asJsonObject
            when (obj["type"]?.asString) {
                "text" -> obj["text"]?.asString ?: ""
                "resource" -> obj["resource"]?.asJsonObject?.get("text")?.asString ?: ""
                else -> ""
            }
        } ?: result?.toString() ?: "No result"
    }

    private fun rpc(server: McpServerConfig, method: String, params: Any): Map<String, JsonElement> {
        val payload = gson.toJson(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to method,
                "params" to params,
                "id" to 1,
            )
        ).toRequestBody(JSON)

        val reqBuilder = Request.Builder()
            .url(server.url.trimEnd('/'))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(payload)

        server.headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        val response = http.newCall(reqBuilder.build()).execute()
        val body = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw IOException("MCP server error (${response.code}): $body")
        }

        // Handle SSE response: look for first "data: {...}" line
        val json = if (body.startsWith("data:")) {
            body.lines()
                .firstOrNull { it.startsWith("data:") }
                ?.removePrefix("data:")
                ?.trim()
                ?: body
        } else {
            body
        }

        val parsed = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: throw IOException("Invalid MCP response")
        return parsed.entrySet().associate { it.key to it.value }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
