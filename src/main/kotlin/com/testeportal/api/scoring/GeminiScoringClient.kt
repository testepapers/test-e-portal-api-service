package com.testeportal.api.scoring

import com.testeportal.api.config.AppConfig
import com.testeportal.api.exception.ScoringException
import com.testeportal.api.model.Question
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Component
class GeminiScoringClient(
    private val appConfig: AppConfig,
    private val webClient: WebClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun scoreAnswer(
        candidateAnswer: String,
        question: Question,
        maxMarks: Double
    ): Map<String, Any> {
        val apiKey = appConfig.llm.gemini.apiKey
        if (apiKey.isBlank()) {
            throw IllegalStateException("GOOGLE_API_KEY environment variable is required")
        }
        
        val questionPrompt = (question.spec["prompt"] as? String) ?: ""
        val solution = question.solution ?: emptyMap()
        val referenceAnswer = extractReferenceAnswer(solution)
        
        val prompt = buildPrompt(questionPrompt, referenceAnswer, maxMarks, candidateAnswer)
        val modelName = appConfig.llm.gemini.scoringModel
        
        try {
            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to prompt)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.3,
                    "maxOutputTokens" to 800,
                    "responseMimeType" to "application/json"
                )
            )
            
            val response = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .timeout(Duration.ofSeconds(60))
                .block()
            
            val text = extractTextFromResponse(response) ?: "{}"
            val cleaned = sanitizeJsonish(text)
            val parsed = json.parseToJsonElement(cleaned).jsonObject
            
            return normalizeResponse(parsed, maxMarks)
        } catch (e: Exception) {
            logger.error(e) { "Gemini scoring error: ${e.message}" }
            throw ScoringException("Gemini scoring failed: ${e.message}", e)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun extractTextFromResponse(response: Map<String, Any>?): String? {
        if (response == null) return null
        
        val candidates = response["candidates"] as? List<*>
        val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
        val content = firstCandidate?.get("content") as? Map<*, *>
        val parts = content?.get("parts") as? List<*>
        val firstPart = parts?.firstOrNull() as? Map<*, *>
        return firstPart?.get("text") as? String
    }
    
    private fun buildPrompt(
        questionPrompt: String,
        referenceAnswer: String,
        maxMarks: Double,
        candidateAnswer: String
    ): String {
        val rubricSection = if (referenceAnswer.isNotEmpty()) {
            "\n\nREFERENCE RUBRIC (Max $maxMarks Points):\nThe reference answer should guide point allocation. Key concepts/requirements identified from the reference:\n$referenceAnswer"
        } else {
            "\n\nREFERENCE RUBRIC: Not provided. Score based on general correctness, completeness, and clarity."
        }
        
        return """You are a professional grader. Score the Student Answer out of a maximum of $maxMarks points based on the provided Question and Reference Rubric. Your output MUST be in JSON format.

QUESTION: $questionPrompt$rubricSection

STUDENT ANSWER: $candidateAnswer

OUTPUT JSON:
{
  "Score": number (0 to $maxMarks),
  "Rationale": string (explanation with point breakdown),
  "deviations": [string],
  "signals": {
    "accuracy_score": number (0-1),
    "completeness_score": number (0-1),
    "length_appropriate": boolean,
    "is_question_copy": boolean,
    "has_contradiction": boolean
  }
}"""
    }
    
    private fun extractReferenceAnswer(solution: Map<String, Any>): String {
        return when {
            solution["referenceAnswer"] != null -> {
                val ref = solution["referenceAnswer"]
                if (ref is List<*>) ref.joinToString(" ") else ref.toString()
            }
            solution["explanation"] != null -> {
                val exp = solution["explanation"]
                if (exp is List<*>) exp.joinToString(" ") else exp.toString()
            }
            solution["description"] != null -> {
                val desc = solution["description"]
                if (desc is List<*>) desc.joinToString(" ") else desc.toString()
            }
            solution["text"] != null -> solution["text"].toString()
            else -> ""
        }
    }
    
    private fun sanitizeJsonish(text: String): String {
        if (text.isBlank()) return "{}"
        var out = text.trim()
        // Remove code fences
        out = out.replace(Regex("^```(?:json|JSON)?\\s*", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("```\\s*$", RegexOption.IGNORE_CASE), "")
        out = out.replace("```", "")
        // Extract first JSON object
        val firstObj = out.indexOf('{')
        val firstArr = out.indexOf('[')
        val first = when {
            firstObj == -1 && firstArr == -1 -> 0
            firstObj == -1 -> firstArr
            firstArr == -1 -> firstObj
            else -> minOf(firstObj, firstArr)
        }
        out = out.substring(first)
        val lastBrace = out.lastIndexOf('}')
        val lastBracket = out.lastIndexOf(']')
        val last = maxOf(lastBrace, lastBracket)
        if (last >= 0) out = out.substring(0, last + 1)
        return out.trim()
    }
    
    private fun normalizeResponse(parsed: JsonObject, maxMarks: Double): Map<String, Any> {
        val score = parsed["Score"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val scoreOutOf5 = parsed["score_out_of_5"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val rationale = parsed["Rationale"]?.jsonPrimitive?.content ?: parsed["feedback"]?.jsonPrimitive?.content ?: ""
        val feedback = parsed["feedback"]?.jsonPrimitive?.content ?: rationale
        
        @Suppress("UNCHECKED_CAST")
        val deviations = (parsed["deviations"]?.let { 
            json.decodeFromString<List<String>>(it.toString())
        }) ?: emptyList()
        
        @Suppress("UNCHECKED_CAST")
        val signals = (parsed["signals"]?.jsonObject?.let { signalsObj ->
            mapOf(
                "accuracy_score" to (signalsObj["accuracy_score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5),
                "completeness_score" to (signalsObj["completeness_score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5),
                "length_appropriate" to (signalsObj["length_appropriate"]?.jsonPrimitive?.content?.toBoolean() ?: true),
                "is_question_copy" to (signalsObj["is_question_copy"]?.jsonPrimitive?.content?.toBoolean() ?: false),
                "has_contradiction" to (signalsObj["has_contradiction"]?.jsonPrimitive?.content?.toBoolean() ?: false)
            )
        }) ?: emptyMap<String, Any>()
        
        return mapOf(
            "score_out_of_5" to (score?.let { (it / maxMarks) * 5.0 } ?: scoreOutOf5 ?: 2.5),
            "Score" to (score ?: ((scoreOutOf5 ?: 2.5) / 5.0) * maxMarks),
            "feedback" to feedback,
            "Rationale" to rationale,
            "deviations" to deviations,
            "signals" to signals
        )
    }
}
