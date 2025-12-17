package com.testeportal.api.validator

import com.testeportal.api.config.AppConfig
import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResult
import com.testeportal.api.scoring.ScoringService
import mu.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Subjective Validator
 * Validates subjective/long_answer questions using LLM-based scoring
 */
@Component
class SubjectiveValidator(
    private val scoringService: ScoringService,
    private val appConfig: AppConfig
) : BaseValidator {
    
    override suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: Question?
    ): ValidationResult {
        try {
            val answerText = (userAnswer["text"] as? String)?.trim() ?: ""
            
            // Always score (even empty answers) to get deviations and feedback
            val scoringResult = scoringService.scoreSubjectiveAnswer(
                answerText = answerText,
                question = question ?: Question(
                    spec = spec,
                    marks = totalMarks.toBigDecimal()
                ),
                maxMarks = totalMarks
            )
            
            // If answer is empty, mark as incorrect
            if (answerText.isEmpty()) {
                return ValidationResult(
                    isCorrect = false,
                    awardedMarks = 0.0,
                    scoringDetails = scoringResult
                )
            }
            
            // Above threshold = correct
            val passingThreshold = totalMarks * appConfig.validation.subjective.passingScore
            val isCorrect = (scoringResult["score"] as? Number)?.toDouble() ?: 0.0 >= passingThreshold
            val awardedMarks = (scoringResult["score"] as? Number)?.toDouble() ?: 0.0
            
            return ValidationResult(
                isCorrect = isCorrect,
                awardedMarks = awardedMarks,
                scoringDetails = scoringResult
            )
        } catch (error: Exception) {
            logger.error(error) { "Error scoring subjective answer" }
            
            // Fallback: mark as needing review
            return ValidationResult(
                isCorrect = null,
                awardedMarks = 0.0,
                scoringDetails = mapOf("error" to (error.message ?: "Unknown error"))
            )
        }
    }
}
