package com.testeportal.api.repository

import com.testeportal.api.model.Question
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : JpaRepository<Question, Long> {
    
    @Query("""
        SELECT q FROM Question q 
        LEFT JOIN FETCH q.questionType qt 
        WHERE q.id = :questionId
    """)
    fun findByIdWithType(@Param("questionId") questionId: Long): Question?
}
