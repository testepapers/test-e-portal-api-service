package com.testeportal.api.exception

open class ValidationException(
    message: String,
    val code: String = "VALIDATION_ERROR",
    val statusCode: Int = 400
) : RuntimeException(message)

class QuestionNotFoundException(questionId: Long) : ValidationException(
    message = "Question not found: $questionId",
    code = "QUESTION_NOT_FOUND",
    statusCode = 404
)

class UnknownQuestionTypeException(typeKey: String) : ValidationException(
    message = "Unknown question type: $typeKey",
    code = "UNKNOWN_QUESTION_TYPE",
    statusCode = 400
)

class InvalidAnswerFormatException(
    message: String,
    val expectedFormat: String? = null
) : ValidationException(
    message = message,
    code = "INVALID_ANSWER_FORMAT",
    statusCode = 400
)

class DatabaseException(
    message: String,
    val originalError: Throwable? = null
) : RuntimeException(message, originalError)

class ScoringException(
    message: String,
    val originalError: Throwable? = null
) : RuntimeException(message, originalError)
