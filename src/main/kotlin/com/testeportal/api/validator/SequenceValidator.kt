package com.testeportal.api.validator

import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResult
import com.testeportal.api.util.NormalizeUtil
import org.springframework.stereotype.Component

/**
 * Sequence Validator
 * Validates sequencing/ordering questions
 */
@Component
class SequenceValidator : BaseValidator {
    
    override suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: Question?
    ): ValidationResult {
        val expectedOrder = NormalizeUtil.ensureArray<Any>(spec["correctOrder"])
        val userOrder = NormalizeUtil.ensureArray<Any>(userAnswer["order"])
        
        // Filter out null/undefined values to check if user made any selections
        val userOrderFiltered = userOrder.filter { it != null }
        
        // If no selections provided, mark as incorrect
        if (userOrderFiltered.isEmpty()) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        // Compare arrays - both must have same length and same values
        val userOrderValues = userOrder.filter { it != null }
        val expectedOrderValues = expectedOrder.filter { it != null }
        
        if (userOrderValues.size != expectedOrderValues.size) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        // Compare positions - each item must be in the correct position
        var allCorrect = true
        for (i in expectedOrder.indices) {
            if (userOrder.getOrNull(i) != expectedOrder.getOrNull(i)) {
                allCorrect = false
                break
            }
        }
        
        return ValidationResult(
            isCorrect = allCorrect,
            awardedMarks = if (allCorrect) totalMarks else 0.0
        )
    }
}
