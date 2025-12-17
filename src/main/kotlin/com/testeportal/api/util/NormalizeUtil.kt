package com.testeportal.api.util

object NormalizeUtil {
    /**
     * Normalize question type key
     * Converts hyphens to underscores for consistency (e.g., 'case-study' -> 'case_study')
     */
    fun normalizeQuestionType(typeKey: String?): String {
        if (typeKey.isNullOrBlank()) {
            return typeKey ?: ""
        }
        return typeKey.replace("-", "_")
    }

    /**
     * Normalize text for comparison (lowercase, trim)
     */
    fun normalizeText(text: String?): String {
        if (text == null) {
            return ""
        }
        return text.lowercase().trim()
    }

    /**
     * Parse numeric value safely
     */
    fun parseNumeric(value: Any?, defaultValue: Double = 0.0): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: defaultValue
            null -> defaultValue
            else -> defaultValue
        }
    }

    /**
     * Ensure array value
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> ensureArray(value: Any?): List<T> {
        return when (value) {
            is List<*> -> value.filterIsInstance<T>()
            null -> emptyList()
            else -> listOf(value as T)
        }
    }
}
