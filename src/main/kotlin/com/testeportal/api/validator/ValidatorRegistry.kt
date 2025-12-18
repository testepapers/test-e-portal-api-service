package com.testeportal.api.validator

import com.testeportal.api.util.NormalizeUtil
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * Validator Registry
 * Maps question types to their validator implementations
 */
@Component
class ValidatorRegistry(
    private val mcqValidator: McqValidator,
    private val matchValidator: MatchValidator,
    private val fillBlanksValidator: FillBlanksValidator,
    private val sequenceValidator: SequenceValidator,
    private val caseStudyValidator: CaseStudyValidator,
    private val subjectiveValidator: SubjectiveValidator
) {
    
    private val validators: Map<String, BaseValidator> = mapOf(
        // MCQ types
        "mcq" to mcqValidator,
        "true_false" to mcqValidator,
        "mcq_codes" to mcqValidator,
        "assertion_reason" to mcqValidator,
        
        // Other types
        "match" to matchValidator,
        "fill_blanks" to fillBlanksValidator,
        "sequence" to sequenceValidator,
        "case_study" to caseStudyValidator,
        "subjective" to subjectiveValidator,
        "long_answer" to subjectiveValidator
    )
    
    @PostConstruct
    fun initialize() {
        // Set self reference in CaseStudyValidator to enable nested case study validation
        // This breaks the circular dependency by doing it after both beans are constructed
        caseStudyValidator.setSelfReference(caseStudyValidator)
    }
    
    /**
     * Get validator for question type
     */
    fun getValidator(typeKey: String?): BaseValidator? {
        val normalizedType = NormalizeUtil.normalizeQuestionType(typeKey)
        return validators[normalizedType]
    }
    
    /**
     * Check if question type is supported
     */
    fun isSupported(typeKey: String?): Boolean {
        return getValidator(typeKey) != null
    }
    
    /**
     * Get all supported question types
     */
    fun getSupportedTypes(): Set<String> {
        return validators.keys
    }
}
