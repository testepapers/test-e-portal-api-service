package com.testeportal.api.validator

import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResult
import org.springframework.stereotype.Component

/**
 * Match Validator
 * Validates match-the-following questions
 */
@Component
class MatchValidator : BaseValidator {
    
    override suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: Question?
    ): ValidationResult {
        @Suppress("UNCHECKED_CAST")
        val expectedPairs = (spec["pairs"] as? List<*>)?.filterIsInstance<List<*>>() ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val userPairs = (userAnswer["pairs"] as? List<*>)?.filterIsInstance<List<*>>() ?: emptyList()
        
        // If no pairs provided, mark as incorrect
        if (userPairs.isEmpty()) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        if (expectedPairs.size != userPairs.size) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        // Count correct pairs
        var correctCount = 0
        expectedPairs.forEachIndexed { i, expectedPair ->
            val userPair = userPairs.getOrNull(i)
            if (userPair != null && 
                expectedPair.size >= 2 && userPair.size >= 2 &&
                expectedPair[0] == userPair[0] && 
                expectedPair[1] == userPair[1]) {
                correctCount++
            }
        }
        
        val isCorrect = correctCount == expectedPairs.size
        val awardedMarks = totalMarks * (correctCount.toDouble() / expectedPairs.size)
        
        return ValidationResult(
            isCorrect = isCorrect,
            awardedMarks = awardedMarks
        )
    }
}
