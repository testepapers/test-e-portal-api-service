package com.testeportal.api.util

object SolutionFormatter {
    
    fun formatSolution(
        typeKey: String,
        solution: Map<String, Any>?,
        spec: Map<String, Any>,
        scoringDetails: Map<String, Any>? = null
    ): Map<String, Any> {
        val baseSolution = solution ?: emptyMap()
        
        return when (typeKey) {
            "mcq", "true_false", "mcq_codes", "assertion_reason" -> 
                formatMCQSolution(baseSolution, spec)
            "match" -> 
                formatMatchSolution(baseSolution, spec)
            "fill_blanks" -> 
                formatFillBlanksSolution(baseSolution, spec)
            "sequence" -> 
                formatSequenceSolution(baseSolution, spec)
            "case_study" -> 
                formatCaseStudySolution(baseSolution, spec)
            "subjective", "long_answer" -> 
                formatSubjectiveSolution(baseSolution, spec, scoringDetails)
            else -> baseSolution
        }
    }
    
    private fun formatMCQSolution(solution: Map<String, Any>, spec: Map<String, Any>): Map<String, Any> {
        return solution.toMutableMap().apply {
            put("answerIndex", spec["answerIndex"] ?: "")
        }
    }
    
    private fun formatMatchSolution(solution: Map<String, Any>, spec: Map<String, Any>): Map<String, Any> {
        return solution.toMutableMap().apply {
            put("pairs", spec["pairs"] ?: emptyList<Any>())
        }
    }
    
    private fun formatFillBlanksSolution(solution: Map<String, Any>, spec: Map<String, Any>): Map<String, Any> {
        return solution.toMutableMap().apply {
            put("blanks", spec["blanks"] ?: emptyList<Any>())
        }
    }
    
    private fun formatSequenceSolution(solution: Map<String, Any>, spec: Map<String, Any>): Map<String, Any> {
        return solution.toMutableMap().apply {
            put("correctOrder", spec["correctOrder"] ?: emptyList<Any>())
        }
    }
    
    private fun formatCaseStudySolution(
        solution: Map<String, Any>,
        spec: Map<String, Any>
    ): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val subQuestions = ((spec["questions"] as? List<*>) ?: emptyList<Any>()).mapNotNull { subQ ->
            if (subQ !is Map<*, *>) return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val subQMap = subQ as Map<String, Any>
            
            val subQSolution = mutableMapOf<String, Any>(
                "type" to (subQMap["type"] ?: ""),
                "marks" to (subQMap["marks"] ?: 1),
                "prompt" to (subQMap["prompt"] ?: "")
            )
            
            // Add correct answer based on sub-question type
            val type = subQMap["type"] as? String
            when (type) {
                "mcq", "true_false", "mcq_codes", "assertion_reason" -> {
                    subQSolution["answerIndex"] = subQMap["answerIndex"] ?: ""
                }
                "fill_blanks" -> {
                    subQSolution["blanks"] = subQMap["blanks"] ?: emptyList<Any>()
                }
                "sequence" -> {
                    subQSolution["correctOrder"] = subQMap["correctOrder"] ?: emptyList<Any>()
                }
                "subjective", "long_answer" -> {
                    subQSolution["referenceAnswer"] = subQMap["referenceAnswer"] 
                        ?: subQMap["answer"] 
                        ?: ""
                }
            }
            
            subQSolution
        }
        
        return solution.toMutableMap().apply {
            put("subQuestions", subQuestions)
        }
    }
    
    private fun formatSubjectiveSolution(
        solution: Map<String, Any>,
        spec: Map<String, Any>,
        scoringDetails: Map<String, Any>?
    ): Map<String, Any> {
        if (scoringDetails == null) {
            return solution
        }
        
        @Suppress("UNCHECKED_CAST")
        val signals = scoringDetails["signals"] as? Map<String, Any> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val deviations = scoringDetails["deviations"] as? List<*> ?: emptyList<Any>()
        
        return solution.toMutableMap().apply {
            put("scoring", signals)
            put("referenceAnswer", solution["explanation"] 
                ?: solution["description"] 
                ?: solution["text"] 
                ?: "")
            put("deviations", deviations)
            put("candidate_text", scoringDetails["candidate_text"] ?: "")
            put("score_5", scoringDetails["score_5"] ?: 0.0)
        }
    }
}
