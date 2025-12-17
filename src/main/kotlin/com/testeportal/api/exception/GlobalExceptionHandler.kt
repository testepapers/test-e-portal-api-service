package com.testeportal.api.exception

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> {
        logger.warn { "Validation error: ${e.code} - ${e.message}" }
        return ResponseEntity
            .status(e.statusCode)
            .body(ErrorResponse(message = e.message ?: "Validation error", code = e.code))
    }

    @ExceptionHandler(DatabaseException::class)
    fun handleDatabaseException(e: DatabaseException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Database error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(message = "Database error occurred", code = "DATABASE_ERROR"))
    }

    @ExceptionHandler(ScoringException::class)
    fun handleScoringException(e: ScoringException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Scoring error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(message = "Scoring service error", code = "SCORING_ERROR"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Unexpected error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(message = "Internal server error", code = "INTERNAL_ERROR"))
    }
}

data class ErrorResponse(
    val message: String,
    val code: String
)
