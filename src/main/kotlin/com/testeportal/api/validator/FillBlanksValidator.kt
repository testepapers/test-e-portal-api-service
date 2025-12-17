package com.testeportal.api.validator

import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResult
import com.testeportal.api.util.NormalizeUtil
import org.springframework.stereotype.Component

/**
 * Fill Blanks Validator
 * Validates fill-in-the-blanks questions
 */
@Component
class FillBlanksValidator : BaseValidator {
    
    override suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: Question?
    ): ValidationResult {
        @Suppress("UNCHECKED_CAST")
        val blanks = (spec["blanks"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map { 
            it as Map<String, Any> 
        } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val answers = (userAnswer["answers"] as? List<*>)?.mapNotNull { 
            it as? String 
        } ?: emptyList()
        
        // If no answers provided, mark as incorrect
        if (answers.isEmpty()) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        // Compare each blank answer
        var correctCount = 0
        blanks.forEachIndexed { i, blank ->
            val expected = NormalizeUtil.normalizeText(blank["answer"] as? String)
            val user = NormalizeUtil.normalizeText(answers.getOrNull(i))
            
            if (expected == user && user.isNotEmpty()) {
                correctCount++
            }
        }
        
        val isCorrect = correctCount == blanks.size && blanks.isNotEmpty()
        val awardedMarks = totalMarks * (correctCount.toDouble() / blanks.size)
        
        return ValidationResult(
            isCorrect = isCorrect,
            awardedMarks = awardedMarks
        )
    }
}
