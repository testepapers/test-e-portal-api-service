package com.testeportal.api.controller

import com.testeportal.api.model.HealthResponse
import com.testeportal.api.model.ValidationRequest
import com.testeportal.api.model.ValidationResponse
import com.testeportal.api.service.ValidationService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping
class ValidationController(
    private val validationService: ValidationService
) {
    
    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "ok",
                timestamp = Instant.now().toString(),
                service = "validation"
            )
        )
    }
    
    @PostMapping("/validate")
    suspend fun validate(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        val requestId = generateRequestId()
        val startTime = System.currentTimeMillis()
        
        try {
            val input = request.input
            
            // Validate input
            if (input.questionId <= 0 || input.answer.isEmpty()) {
                logger.warn { 
                    "Missing required fields: questionId=${input.questionId}, answer provided=${input.answer.isNotEmpty()}" 
                }
                return ResponseEntity.badRequest().build()
            }
            
            logger.debug { 
                "Validation request received - questionId: ${input.questionId}, " +
                "answerType: ${input.answer::class.simpleName}"
            }
            
            // Execute validation
            val result = validationService.validateAnswer(input.questionId, input.answer)
            
            val duration = System.currentTimeMillis() - startTime
            logger.info { 
                "Validation completed - requestId: $requestId, questionId: ${input.questionId}, " +
                "duration: ${duration}ms, isCorrect: ${result.isCorrect}"
            }
            
            return ResponseEntity.ok(result)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { 
                "Validation error - requestId: $requestId, duration: ${duration}ms, error: ${e.message}" 
            }
            throw e // Let GlobalExceptionHandler handle it
        }
    }
    
    private fun generateRequestId(): String {
        return "req-${System.currentTimeMillis()}-${(0..999999).random().toString(36)}"
    }
}
