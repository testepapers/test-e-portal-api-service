package com.testeportal.api.service

import com.testeportal.api.exception.DatabaseException
import com.testeportal.api.exception.QuestionNotFoundException
import com.testeportal.api.exception.UnknownQuestionTypeException
import com.testeportal.api.model.Question
import com.testeportal.api.model.ValidationResponse
import com.testeportal.api.repository.QuestionRepository
import com.testeportal.api.util.NormalizeUtil
import com.testeportal.api.util.SolutionFormatter
import com.testeportal.api.validator.ValidatorRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class ValidationService(
    private val questionRepository: QuestionRepository,
    private val validatorRegistry: ValidatorRegistry
) {
    
    @Transactional(readOnly = true)
    suspend fun validateAnswer(questionId: Long, userAnswer: Map<String, Any>): ValidationResponse {
        
        try {
            // Stage 1: Fetch question
            val question = fetchQuestion(questionId)
            
            // Stage 2: Normalize type
            val normalizedType = normalizeType(question)
            
            // Extract question data
            val spec = question.spec
            val totalMarks = NormalizeUtil.parseNumeric(question.marks, 0.0)
            
            // Stage 3: Validate answer
            val validator = validatorRegistry.getValidator(normalizedType)
                ?: throw UnknownQuestionTypeException(question.typeKey ?: "")
            
            val validationResult = validator.validate(spec, userAnswer, totalMarks, question)
            
            // Extract scoring details if available
            val scoringDetails = validationResult.scoringDetails ?: emptyMap()
            
            // Stage 4: Generate feedback
            val feedback = generateFeedback(normalizedType, validationResult.isCorrect, scoringDetails)
            
            // Stage 5: Format solution
            val solution = SolutionFormatter.formatSolution(
                normalizedType,
                question.solution,
                spec,
                scoringDetails
            )
            
            // Build result
            val result = ValidationResponse(
                isCorrect = validationResult.isCorrect,
                awardedMarks = validationResult.awardedMarks,
                totalMarks = totalMarks,
                solution = solution,
                feedback = feedback
            )
            
            logger.info { 
                "Validation completed successfully - questionId: $questionId, isCorrect: ${result.isCorrect}, " +
                "awardedMarks: ${result.awardedMarks}, totalMarks: ${result.totalMarks}"
            }
            
            return result
        } catch (e: Exception) {
            logger.error(e) { "Validation pipeline error - questionId: $questionId" }
            throw e
        }
    }
    
    private suspend fun fetchQuestion(questionId: Long): Question {
        logger.debug { "Fetching question from database: $questionId" }
        
        val question = questionRepository.findByIdWithType(questionId)
            ?: throw QuestionNotFoundException(questionId)
        
        logger.debug { 
            "Question fetched successfully - questionId: $questionId, typeKey: ${question.typeKey}" 
        }
        
        return question
    }
    
    private fun normalizeType(question: Question): String {
        val normalizedType = NormalizeUtil.normalizeQuestionType(question.typeKey)
        
        if (!validatorRegistry.isSupported(normalizedType)) {
            throw UnknownQuestionTypeException(question.typeKey ?: "")
        }
        
        logger.debug { 
            "Question type normalized - original: ${question.typeKey}, normalized: $normalizedType" 
        }
        
        return normalizedType
    }
    
    private fun generateFeedback(
        normalizedType: String,
        isCorrect: Boolean?,
        scoringDetails: Map<String, Any>
    ): String {
        if (isCorrect == null) {
            return "Answer submitted for teacher review"
        }
        
        if (normalizedType == "subjective" || normalizedType == "long_answer") {
            return (scoringDetails["feedback"] as? String) ?: "Answer evaluated"
        }
        
        return if (isCorrect) "Correct!" else "Incorrect"
    }
}
