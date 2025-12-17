package com.testeportal.api.validator

import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResult
import com.testeportal.api.util.NormalizeUtil
import org.springframework.stereotype.Component

/**
 * Case Study Validator
 * Validates case study questions by validating each sub-question
 */
@Component
class CaseStudyValidator(
    private val validatorRegistry: ValidatorRegistry
) : BaseValidator {
    
    override suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: Question?
    ): ValidationResult {
        @Suppress("UNCHECKED_CAST")
        val subQuestions = (spec["questions"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map {
            it as Map<String, Any>
        } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val userSubAnswers = (userAnswer["subQuestions"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map {
            it as Map<String, Any>
        } ?: emptyList()
        
        if (userSubAnswers.isEmpty()) {
            return ValidationResult(
                isCorrect = false,
                awardedMarks = 0.0
            )
        }
        
        var totalSubMarks = 0.0
        var awardedSubMarks = 0.0
        
        // Validate each sub-question
        for (index in subQuestions.indices) {
            val subQ = subQuestions[index]
            val subQMarks = NormalizeUtil.parseNumeric(subQ["marks"], 1.0)
            totalSubMarks += subQMarks
            
            val userSubAnswer = userSubAnswers.getOrNull(index)
            if (userSubAnswer == null) {
                // No answer for this sub-question
                continue
            }
            
            // Get appropriate validator for sub-question type
            val subQType = (subQ["type"] as? String) ?: continue
            val validator = validatorRegistry.getValidator(subQType)
            if (validator == null) {
                // Unknown sub-question type, skip
                continue
            }
            
            // Validate sub-question
            val subResult = validator.validate(subQ, userSubAnswer, subQMarks, null)
            awardedSubMarks += subResult.awardedMarks
        }
        
        // Case study is correct if all sub-questions are correct
        val isCorrect = awardedSubMarks == totalSubMarks && totalSubMarks > 0
        
        return ValidationResult(
            isCorrect = isCorrect,
            awardedMarks = awardedSubMarks
        )
    }
}
