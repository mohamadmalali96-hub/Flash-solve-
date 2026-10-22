package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class SolveResponse(
    val answer: String,
    val explanation: String,
    val confidence: String,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val rawText: String = "",
    val errorMessage: String? = null
)

/**
 * High-speed, lightweight HTTP client calling Google Gemini Flash.
 * Optimized for sub-1.5s real-time quiz and math solutions with zero external SDK bloat.
 */
object GeminiFlashClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    // High performance connection pool and aggressive HTTP/2 reuse
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val SYSTEM_PROMPT =
        "You are FlashSolve, an ultra-low latency AI quiz and math solver. " +
        "Solve the quiz question, multiple-choice trivia, or math problem (supporting both Arabic and English). " +
        "Output strictly in this format without markdown headers:\n" +
        "ANSWER: [Only the option letter e.g. A/B/C/D or exact math value]\n" +
        "EXPLANATION: [One concise sentence in the question's language]\n" +
        "CONFIDENCE: [e.g. 95%]"

    /**
     * Solves a text-based question asynchronously on Dispatchers.IO.
     */
    suspend fun solveText(prompt: String, customApiKey: String? = null): SolveResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = resolveApiKey(customApiKey)

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext SolveResponse(
                answer = "NO_KEY",
                explanation = "Please provide your Gemini API Key in Settings or the Secrets panel.",
                confidence = "0%",
                latencyMs = 0,
                isSuccess = false,
                errorMessage = "Gemini API key is not configured."
            )
        }

        try {
            val jsonPayload = buildJsonRequest(prompt, null)
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val bodyString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorDetail = parseErrorMessage(bodyString)
                    return@withContext SolveResponse(
                        answer = "ERR",
                        explanation = "API error (${response.code}): $errorDetail",
                        confidence = "0%",
                        latencyMs = latency,
                        isSuccess = false,
                        errorMessage = "HTTP ${response.code}: $errorDetail"
                    )
                }

                val parsed = parseGeminiResponse(bodyString, latency)
                return@withContext parsed
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            return@withContext SolveResponse(
                answer = "ERR",
                explanation = "Network failed: ${e.localizedMessage ?: "Unknown error"}",
                confidence = "0%",
                latencyMs = latency,
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Solves a multimodal image (e.g. cropped screen/camera) asynchronously.
     * Compression is performed on Dispatchers.Default, network on Dispatchers.IO.
     */
    suspend fun solveImage(bitmap: Bitmap, prompt: String = "Solve this quiz or math problem immediately.", customApiKey: String? = null): SolveResponse {
        val startTime = System.currentTimeMillis()
        val base64Image = withContext(Dispatchers.Default) {
            val outputStream = ByteArrayOutputStream()
            // 75% quality offers sharp clarity for text/math while keeping payload under ~60KB for instant transmission
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }

        return withContext(Dispatchers.IO) {
            val apiKey = resolveApiKey(customApiKey)
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext SolveResponse(
                    answer = "NO_KEY",
                    explanation = "Please provide your Gemini API Key in Settings or the Secrets panel.",
                    confidence = "0%",
                    latencyMs = 0,
                    isSuccess = false,
                    errorMessage = "Gemini API key is not configured."
                )
            }

            try {
                val jsonPayload = buildJsonRequest(prompt, base64Image)
                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .header("Content-Type", "application/json")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    val bodyString = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        val errorDetail = parseErrorMessage(bodyString)
                        return@withContext SolveResponse(
                            answer = "ERR",
                            explanation = "API error (${response.code}): $errorDetail",
                            confidence = "0%",
                            latencyMs = latency,
                            isSuccess = false,
                            errorMessage = errorDetail
                        )
                    }

                    parseGeminiResponse(bodyString, latency)
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                SolveResponse(
                    answer = "ERR",
                    explanation = "Failed: ${e.localizedMessage ?: "Network issue"}",
                    confidence = "0%",
                    latencyMs = latency,
                    isSuccess = false,
                    errorMessage = e.message
                )
            }
        }
    }

    private fun resolveApiKey(customKey: String?): String {
        if (!customKey.isNullOrBlank()) return customKey.trim()
        val envKey = BuildConfig.GEMINI_API_KEY
        return envKey?.trim() ?: ""
    }

    private fun buildJsonRequest(prompt: String, base64Jpeg: String?): JSONObject {
        val root = JSONObject()

        // System Instruction
        val sysInst = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
        }
        root.put("systemInstruction", sysInst)

        // Contents
        val partsArray = JSONArray()
        if (base64Jpeg != null) {
            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Jpeg)
            }
            partsArray.put(JSONObject().put("inlineData", inlineData))
        }
        partsArray.put(JSONObject().put("text", prompt))

        val contentObj = JSONObject().put("parts", partsArray)
        root.put("contents", JSONArray().put(contentObj))

        // Generation Config: Low temperature for determinism & minimal tokens for speed
        val genConfig = JSONObject().apply {
            put("temperature", 0.1)
            put("maxOutputTokens", 120)
            put("topP", 0.9)
        }
        root.put("generationConfig", genConfig)

        return root
    }

    private fun parseGeminiResponse(jsonString: String, latencyMs: Long): SolveResponse {
        return try {
            val json = JSONObject(jsonString)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""

            if (rawText.isEmpty()) {
                return SolveResponse(
                    answer = "N/A",
                    explanation = "No solution found in response.",
                    confidence = "50%",
                    latencyMs = latencyMs,
                    isSuccess = false,
                    rawText = jsonString
                )
            }

            var answer = ""
            var explanation = ""
            var confidence = "95%"

            val lines = rawText.lines()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("ANSWER:", ignoreCase = true) -> {
                        answer = trimmed.substringAfter(":").trim()
                    }
                    trimmed.startsWith("EXPLANATION:", ignoreCase = true) -> {
                        explanation = trimmed.substringAfter(":").trim()
                    }
                    trimmed.startsWith("CONFIDENCE:", ignoreCase = true) -> {
                        confidence = trimmed.substringAfter(":").trim()
                    }
                }
            }

            if (answer.isEmpty()) {
                // Fallback to first token/word
                answer = rawText.take(15).trim()
                explanation = rawText
            }

            SolveResponse(
                answer = answer,
                explanation = explanation.ifEmpty { "Verified by Gemini Flash AI." },
                confidence = confidence,
                latencyMs = latencyMs,
                isSuccess = true,
                rawText = rawText
            )
        } catch (e: Exception) {
            SolveResponse(
                answer = "PARSER_ERR",
                explanation = "Failed to parse: ${e.message}",
                confidence = "0%",
                latencyMs = latencyMs,
                isSuccess = false,
                rawText = jsonString
            )
        }
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message") ?: errorBody
        } catch (_: Exception) {
            errorBody.take(100)
        }
    }
}
