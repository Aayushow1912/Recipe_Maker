package com.example.recipeapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LlmRecipeResponse(
    val title: String,
    val time: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val imageUrl: String
)

class OpenAiClient(private val apiKey: String) {

    fun getRecipeResponse(
        userRecipeText: String,
        onSuccess: (LlmRecipeResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onError("Missing OPENROUTER_API_KEY. Add it to local.properties.")
            return
        }

        try {
            val connection = URL("https://openrouter.ai/api/v1/chat/completions")
                .openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("HTTP-Referer", "https://recipeapp.local")
            connection.setRequestProperty("X-Title", "Recipe App")

            val payload = buildRequestBody(userRecipeText)
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode !in 200..299) {
                onError("LLM request failed ($responseCode): $body")
                return
            }

            val content = extractContent(body)
            val parsed = parseRecipeJson(content, userRecipeText)
            onSuccess(parsed)
        } catch (ex: Exception) {
            onError("LLM request error: ${ex.message ?: "unknown error"}")
        }
    }

    private fun buildRequestBody(userRecipeText: String): JSONObject {
        val systemPrompt = """
            You are a recipe formatter.
            Return ONLY a valid JSON object with this exact shape:
            {
              "title": "string",
              "time": "string",
              "ingredients": ["string"],
              "steps": ["string"],
              "imageUrl": "string"
            }
            Rules:
            - Always include every field.
            - ingredients must have at least 3 entries.
            - steps must have at least 3 entries.
            - imageUrl is REQUIRED and MUST be non-empty.
            - imageUrl MUST be a direct, publicly accessible HTTPS URL to a food photo for this dish (jpg/png/webp).
            - Never return an empty imageUrl.
            - No markdown and no extra text.
        """.trimIndent()

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", "Dish: $userRecipeText")
            )

        return JSONObject()
            .put("model", "openai/gpt-4o-mini")
            .put("messages", messages)
            .put("temperature", 0.3)
            .put("response_format", JSONObject().put("type", "json_object"))
    }

    private fun extractContent(rawResponse: String): String {
        val root = JSONObject(rawResponse)
        val choices = root.getJSONArray("choices")
        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.getJSONObject("message")
        return message.optString("content", "").trim()
    }

    private fun parseRecipeJson(content: String, dishQuery: String): LlmRecipeResponse {
        val json = JSONObject(content)
        val imageUrlRaw = json.optString("imageUrl", "").trim()
        val imageUrl = normalizeImageUrl(imageUrlRaw, dishQuery)
        return LlmRecipeResponse(
            title = json.optString("title", "").trim(),
            time = json.optString("time", "").trim(),
            ingredients = json.optJSONArray("ingredients").toStringList().ifEmpty {
                listOf("Ingredient details not provided")
            },
            steps = json.optJSONArray("steps").toStringList().ifEmpty {
                listOf("Step details not provided")
            },
            imageUrl = imageUrl
        )
    }

    private fun normalizeImageUrl(imageUrlRaw: String, dishQuery: String): String {
        val trimmed = imageUrlRaw.trim()
        if (isLikelyDirectImageUrl(trimmed)) return trimmed
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val httpsUrl = trimmed.replaceFirst("http://", "https://")
            if (isLikelyDirectImageUrl(httpsUrl)) return httpsUrl
        }
        return fallbackDishImageUrl(dishQuery)
    }

    private fun fallbackDishImageUrl(dishQuery: String): String {
        // LLM-generated image links are often invalid; use a reliable no-key food image endpoint.
        val q = URLEncoder.encode(dishQuery.trim().ifBlank { "food" }, "UTF-8")
        return "https://loremflickr.com/900/600/food,$q?lock=1"
    }

    private fun isLikelyDirectImageUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val lower = url.lowercase()
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.contains("images.unsplash.com") ||
            lower.contains("cdn.pixabay.com") ||
            lower.contains("pexels.com/photo")
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = optString(i).trim()
            if (item.isNotBlank()) {
                list.add(item)
            }
        }
        return list
    }
}
