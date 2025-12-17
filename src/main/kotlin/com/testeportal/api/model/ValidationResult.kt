package com.testeportal.api.model

data class ValidationResult(
    val isCorrect: Boolean?,
    val awardedMarks: Double,
    val scoringDetails: Map<String, Any>? = null
)
