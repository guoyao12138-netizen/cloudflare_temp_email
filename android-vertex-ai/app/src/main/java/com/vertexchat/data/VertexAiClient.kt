package com.vertexchat.data

import com.google.gson.Gson
import com.vertexchat.data.model.ApiErrorEnvelope
import com.vertexchat.data.model.GenerateContentRequest
import com.vertexchat.data.model.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin OkHttp wrapper around the Vertex AI `generateContent` REST endpoint.
 *
 * The host is region-specific (`{location}-aiplatform.googleapis.com`), so we
 * build the full URL per request rather than using a fixed Retrofit base URL.
 */
class VertexAiClient(
    private val gson: Gson = Gson(),
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(
        accessToken: String,
        projectId: String,
        location: String,
        model: String,
        request: GenerateContentRequest,
    ): GenerateContentResponse = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://").append(location).append("-aiplatform.googleapis.com")
            append("/v1/projects/").append(projectId)
            append("/locations/").append(location)
            append("/publishers/google/models/").append(model)
            append(":generateContent")
        }

        val body = gson.toJson(request).toRequestBody(JSON)
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        http.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(extractError(text, response.code))
            }
            gson.fromJson(text, GenerateContentResponse::class.java)
        }
    }

    private fun extractError(body: String, code: Int): String {
        val parsed = runCatching {
            gson.fromJson(body, ApiErrorEnvelope::class.java)?.error?.message
        }.getOrNull()
        return parsed?.let { "Vertex AI error ($code): $it" }
            ?: "Vertex AI request failed (HTTP $code)"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
