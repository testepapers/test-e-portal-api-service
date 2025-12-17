package com.testeportal.api.model

data class ValidationRequest(
    val input: ValidationInput
)

data class ValidationInput(
    val questionId: Long,
    val answer: Map<String, Any>
)

data class ValidationResponse(
    val isCorrect: Boolean?,
    val awardedMarks: Double,
    val totalMarks: Double,
    val solution: Map<String, Any>,
    val feedback: String
)

data class HealthResponse(
    val status: String,
    val timestamp: String,
    val service: String
)
