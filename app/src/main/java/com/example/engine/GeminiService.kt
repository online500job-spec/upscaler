package com.example.engine

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeImageUpscale(
        bitmap: Bitmap,
        customPrompt: String = "You are a professional digital photo restoration expert and AI detailer. Analyze this image. Give a brief description of its visible content (1 sentence), note what defects exist (noise, blur, pixelation, JPEG rings), and suggest which upscale settings (e.g. 2x, 4x, Standard, HQ, Ultra Detail with Face restoration) would bring out the best textures and micro-details."
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isKeyConfigured()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Add GEMINI_API_KEY to the Secrets panel."))
        }

        try {
            val maxDimension = 600
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newW, newH) = if (ratio > 1) {
                    Pair(maxDimension, (maxDimension / ratio).toInt())
                } else {
                    Pair((maxDimension * ratio).toInt(), maxDimension)
                }
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }

            val base64Data = scaledBitmap.toBase64()
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            // Construct REST request using raw robust org.json
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", customPrompt)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                })
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are a direct, concise, professional AI image restoration expert. Give highly actionable details, no fluff.")
                        })
                    })
                })
            }

            val url = "$BASE_URL?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val okRequest = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(okRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = "HTTP Error: ${response.code} - ${response.message}"
                    Log.e(TAG, errMsg)
                    return@withContext Result.failure(Exception(errMsg))
                }

                val responseBodyStr = response.body?.string() ?: throw Exception("Empty response body")
                val responseJson = JSONObject(responseBodyStr)
                
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val text = firstPart?.optString("text")

                if (!text.isNullOrEmpty()) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("Gemini returned successfully but no textual analytics candidate was found."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini image analysis failed: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
