package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Real-time translation service powered by Firebase AI Logic (Gemini 3.8 Flash + App Check).
 * Strictly contains zero hardcoded mock translations.
 */
class AiTranslationService {

    private val translationCache = ConcurrentHashMap<String, String>()

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "translatedText" to Schema.string(description = "The natural translated text in the requested target language"),
            "detectedSourceLang" to Schema.string(description = "Detected ISO 639-1 source language code, e.g. en, ar, fr")
        )
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            }
        )
    }

    /**
     * Translates [text] into [targetLanguageName] (e.g. "Arabic" or "English") via Firebase AI Logic.
     */
    suspend fun translateText(
        text: String,
        targetLanguageName: String = "Arabic"
    ): Result<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("النص فارغ"))
        val cacheKey = "${targetLanguageName.lowercase()}::$clean"
        translationCache[cacheKey]?.let { return Result.success(it) }

        return try {
            val prompt = buildString {
                append("Translate the following live chat/call message accurately and naturally into ")
                append(targetLanguageName)
                append(". Preserve tone and emojis. Return JSON with translatedText and detectedSourceLang.\n\n")
                append("Message: ")
                append(clean)
            }
            val response = withTimeout(30_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("لم يُرجع نموذج الترجمة نصاً."))
            val json = JSONObject(rawJson)
            val translated = json.optString("translatedText", "").trim()
            if (translated.isBlank()) {
                Result.failure(IllegalStateException("النص المترجم فارغ."))
            } else {
                if (translationCache.size > 200) translationCache.clear()
                translationCache[cacheKey] = translated
                Result.success(translated)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI translation timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة الترجمة الفورية."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI translation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "AiTranslationService"
    }
}
