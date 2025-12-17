package com.testeportal.api.validator

import com.testeportal.api.model.ValidationResult

/**
 * Base validator interface for question type validators
 */
interface BaseValidator {
    /**
     * Validate answer against question spec
     * 
     * @param spec Question specification
     * @param userAnswer User's answer
     * @param totalMarks Total marks for the question
     * @param question Optional question object for additional context
     * @return Validation result
     */
    suspend fun validate(
        spec: Map<String, Any>,
        userAnswer: Map<String, Any>,
        totalMarks: Double,
        question: com.testeportal.api.model.Question? = null
    ): ValidationResult
    
    /**
     * Check if answer is provided (not empty)
     */
    fun hasAnswer(userAnswer: Map<String, Any>): Boolean {
        return userAnswer.isNotEmpty()
    }
}
