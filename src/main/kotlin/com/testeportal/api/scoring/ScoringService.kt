package com.testeportal.api.scoring

import com.testeportal.api.config.AppConfig
import com.testeportal.api.exception.ScoringException
import com.testeportal.api.model.Question
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Service
class ScoringService(
    private val appConfig: AppConfig,
    private val openAIScoringClient: OpenAIScoringClient,
    private val geminiScoringClient: GeminiScoringClient
) {
    
    suspend fun scoreSubjectiveAnswer(
        answerText: String,
        question: Question,
        maxMarks: Double
    ): Map<String, Any> {
        // Validate inputs
        if (answerText.isBlank()) {
            return createEmptyAnswerResult(maxMarks, answerText)
        }
        
        val trimmedAnswer = answerText.trim()
        if (trimmedAnswer.isEmpty()) {
            return createEmptyAnswerResult(maxMarks, answerText)
        }
        
        // Quick check: Is answer just the question?
        val questionPrompt = (question.spec["prompt"] as? String) ?: ""
        if (questionPrompt.isNotEmpty()) {
            val normalizedAnswer = trimmedAnswer.lowercase().replace(Regex("\\s+"), " ")
            val normalizedPrompt = questionPrompt.lowercase().replace(Regex("\\s+"), " ")
            
            // Exact match or high overlap
            if (normalizedAnswer == normalizedPrompt || 
                normalizedAnswer.contains(normalizedPrompt) || 
                normalizedPrompt.contains(normalizedAnswer)) {
                return createQuestionCopyResult(maxMarks, answerText, trimmedAnswer.length)
            }
        }
        
        // Try OpenAI first (primary)
        var llmResult: Map<String, Any>? = null
        try {
            llmResult = openAIScoringClient.scoreAnswer(trimmedAnswer, question, maxMarks)
            logger.info { "✓ Scored with OpenAI" }
        } catch (e: Exception) {
            logger.warn(e) { "OpenAI scoring failed, trying Gemini: ${e.message}" }
            
            // Fallback to Gemini
            try {
                llmResult = geminiScoringClient.scoreAnswer(trimmedAnswer, question, maxMarks)
                logger.info { "✓ Scored with Gemini (fallback)" }
            } catch (geminiError: Exception) {
                logger.error(geminiError) { "Both LLM providers failed: ${geminiError.message}" }
                
                // Return error result
                return createErrorResult(maxMarks, answerText, trimmedAnswer.length)
            }
        }
        
        return processLLMResult(llmResult, maxMarks, answerText, trimmedAnswer.length, question)
    }
    
    private fun createEmptyAnswerResult(maxMarks: Double, candidateText: String): Map<String, Any> {
        return mapOf(
            "score" to 0.0,
            "maxMarks" to maxMarks,
            "signals" to emptyMap<String, Any>(),
            "feedback" to "Answer is empty",
            "deviations" to listOf("Answer is empty"),
            "candidate_text" to candidateText,
            "score_5" to 0.0,
            "answer_length" to 0
        )
    }
    
    private fun createQuestionCopyResult(maxMarks: Double, candidateText: String, answerLength: Int): Map<String, Any> {
        return mapOf(
            "score" to 0.0,
            "maxMarks" to maxMarks,
            "signals" to mapOf("is_question_copy" to true),
            "feedback" to "Answer appears to be a copy of the question. Please provide your own answer.",
            "deviations" to listOf("Answer copied from question prompt"),
            "candidate_text" to candidateText,
            "score_5" to 0.0,
            "answer_length" to answerLength
        )
    }
    
    private fun createErrorResult(maxMarks: Double, candidateText: String, answerLength: Int): Map<String, Any> {
        return mapOf(
            "score" to (maxMarks * 0.5), // Neutral fallback
            "maxMarks" to maxMarks,
            "signals" to emptyMap<String, Any>(),
            "feedback" to "Scoring service unavailable. Answer submitted for manual review.",
            "deviations" to listOf("LLM scoring service error"),
            "candidate_text" to candidateText,
            "score_5" to 2.5,
            "answer_length" to answerLength
        )
    }
    
    private fun processLLMResult(
        llmResult: Map<String, Any>?,
        maxMarks: Double,
        candidateText: String,
        answerLength: Int,
        question: Question
    ): Map<String, Any> {
        if (llmResult == null) {
            return createErrorResult(maxMarks, candidateText, answerLength)
        }
        
        // Extract and validate LLM response
        val score = (llmResult["Score"] as? Number)?.toDouble()
        val score5 = if (score != null) {
            (score / maxMarks) * 5.0
        } else {
            ((llmResult["score_out_of_5"] as? Number)?.toDouble() ?: 2.5)
        }.coerceIn(0.0, 5.0)
        
        val finalScore = (score5 / 5.0) * maxMarks
        
        // Build signals object
        @Suppress("UNCHECKED_CAST")
        val signals = (llmResult["signals"] as? Map<String, Any>) ?: emptyMap()
        val finalSignals: Map<String, Any> = mapOf(
            "accuracy_score" to ((signals["accuracy_score"] as? Number)?.toDouble() ?: 0.5),
            "completeness_score" to ((signals["completeness_score"] as? Number)?.toDouble() ?: 0.5),
            "length_appropriate" to ((signals["length_appropriate"] as? Boolean) ?: true),
            "is_question_copy" to ((signals["is_question_copy"] as? Boolean) ?: false),
            "has_contradiction" to ((signals["has_contradiction"] as? Boolean) ?: false)
        )
        
        // Apply length penalty if flagged
        var adjustedScore = score5
        @Suppress("UNCHECKED_CAST")
        val deviations = (llmResult["deviations"] as? List<*>)?.filterIsInstance<String>()?.toMutableList() 
            ?: mutableListOf()
        
        if (finalSignals["length_appropriate"] == false) {
            val targetLength = 200
            val minLength = (targetLength * 0.8).toInt() // 160
            val maxLength = (targetLength * 1.2).toInt() // 240
            
            if (answerLength < minLength || answerLength > maxLength) {
                adjustedScore = (score5 - 0.5).coerceAtLeast(0.0) // Penalty
                if (answerLength < minLength) {
                    deviations.add("Too short ($answerLength chars, target: $targetLength±20%)")
                } else {
                    deviations.add("Too long ($answerLength chars, target: $targetLength±20%)")
                }
            }
        }
        
        val finalAdjustedScore = (adjustedScore / 5.0) * maxMarks
        
        return mapOf<String, Any>(
            "score" to finalAdjustedScore,
            "maxMarks" to maxMarks,
            "signals" to finalSignals,
            "feedback" to ((llmResult["Rationale"] as? String) ?: (llmResult["feedback"] as? String) ?: "Answer evaluated"),
            "rationale" to ((llmResult["Rationale"] as? String) ?: (llmResult["feedback"] as? String) ?: ""),
            "deviations" to deviations,
            "candidate_text" to candidateText,
            "score_5" to adjustedScore,
            "raw_score" to (score ?: finalAdjustedScore),
            "answer_length" to answerLength,
            "reference_answer_available" to (extractReferenceAnswer(question).isNotEmpty())
        )
    }
    
    private fun extractReferenceAnswer(question: Question): String {
        val solution = question.solution ?: return ""
        
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
}
