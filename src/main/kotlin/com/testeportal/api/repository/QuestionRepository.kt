package com.testeportal.api.repository

import com.testeportal.api.model.Question
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : JpaRepository<Question, Long>
