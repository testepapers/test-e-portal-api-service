package com.testeportal.api.scoring

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.testeportal.api.config.AppConfig
import com.testeportal.api.exception.ScoringException
import com.testeportal.api.model.Question
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class OpenAIScoringClient(
    private val appConfig: AppConfig
) {
    private val openai: OpenAI by lazy {
        val apiKey = appConfig.llm.openai.apiKey
        if (apiKey.isBlank()) {
            throw IllegalStateException("OPENAI_API_KEY environment variable is required")
        }
        OpenAI(token = apiKey)
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun scoreAnswer(
        candidateAnswer: String,
        question: Question,
        maxMarks: Double
    ): Map<String, Any> {
        val model = ModelId(appConfig.llm.openai.scoringModel)
        val questionPrompt = (question.spec["prompt"] as? String) ?: ""
        val solution = question.solution ?: emptyMap()
        val referenceAnswer = extractReferenceAnswer(solution)
        
        val systemPrompt = buildSystemPrompt(maxMarks)
        val userPrompt = buildUserPrompt(questionPrompt, referenceAnswer, maxMarks, candidateAnswer)
        
        try {
            val completion = openai.chatCompletion(
                ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = systemPrompt
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = userPrompt
                        )
                    ),
                    temperature = 0.3,
                    maxTokens = 800
                )
            )
            
            val text = completion.choices.firstOrNull()?.message?.content ?: "{}"
            val cleaned = sanitizeJsonish(text)
            val parsed = json.parseToJsonElement(cleaned).jsonObject
            
            return normalizeResponse(parsed, maxMarks)
        } catch (e: Exception) {
            logger.error(e) { "OpenAI scoring error: ${e.message}" }
            throw ScoringException("OpenAI scoring failed: ${e.message}", e)
        }
    }
    
    private fun buildSystemPrompt(maxMarks: Double): String {
        return """You are a professional grader. Score the Student Answer out of a maximum of $maxMarks points based on the provided Question and Reference Answer. Your output MUST be in JSON format.

SCORING RUBRIC GUIDELINES:
- Allocate points based on key concepts/requirements from the reference answer
- Award partial credit for partially correct answers
- Deduct points for missing key concepts or incorrect information
- If answer is just the question repeated → Score: 0 with deviation "Answer copied from question"
- If answer is too short or vague → Apply appropriate deduction
- Provide clear rationale explaining point allocation

OUTPUT FORMAT (JSON):
{
  "Score": number (0 to $maxMarks, can be fractional like 2.5),
  "Rationale": string (explanation of score with point breakdown),
  "deviations": array of strings (specific issues: missing concepts, inaccuracies, etc.),
  "signals": {
    "accuracy_score": number (0-1, proportion of correct content),
    "completeness_score": number (0-1, proportion of required points covered),
    "length_appropriate": boolean,
    "is_question_copy": boolean,
    "has_contradiction": boolean
  }
}"""
    }
    
    private fun buildUserPrompt(
        questionPrompt: String,
        referenceAnswer: String,
        maxMarks: Double,
        candidateAnswer: String
    ): String {
        val rubricSection = if (referenceAnswer.isNotEmpty()) {
            "\n\nREFERENCE RUBRIC (Max $maxMarks Points):\nThe reference answer should guide point allocation. Key concepts/requirements identified from the reference:\n$referenceAnswer"
        } else {
            "\n\nREFERENCE RUBRIC: Not provided. Score based on general correctness, completeness, and clarity of the answer."
        }
        
        return "QUESTION: $questionPrompt$rubricSection\n\nSTUDENT ANSWER: $candidateAnswer\n\nOUTPUT JSON:"
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
