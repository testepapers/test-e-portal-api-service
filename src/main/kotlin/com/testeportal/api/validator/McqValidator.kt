package com.testeportal.api.validator

import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResult
import org.springframework.stereotype.Component

/**
 * MCQ Validator
 * Validates multiple choice, true/false, MCQ codes, and assertion-reason questions
 */
@Component
class McqValidator : BaseValidator {
    
    override suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: Question?
    ): ValidationResult {
        val expectedIndex = (spec["answerIndex"] as? Number)?.toInt()
        val userIndex = (userAnswer["answerIndex"] as? Number)?.toInt()
        
        // -1 means no selection was made
        if (userIndex == null || userIndex == -1) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        val isCorrect = expectedIndex == userIndex
        return ValidationResult(
            isCorrect = isCorrect,
            awardedMarks = if (isCorrect) totalMarks else 0.0
        )
    }
}
