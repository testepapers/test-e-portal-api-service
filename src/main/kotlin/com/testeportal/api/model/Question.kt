package com.testeportal.api.model

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "question")
data class Question(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "type_id")
    val typeId: Long = 0,
    
    @Column(name = "spec", columnDefinition = "jsonb")
    @JvmField
    val spec: Map<String, Any> = emptyMap(),
    
    @Column(name = "solution", columnDefinition = "jsonb")
    @JvmField
    val solution: Map<String, Any>? = null,
    
    @Column(name = "marks")
    val marks: BigDecimal? = null,
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", insertable = false, updatable = false)
    val questionType: QuestionType? = null
) {
    val typeKey: String?
        get() = questionType?.key
}

@Entity
@Table(name = "question_type")
data class QuestionType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "key")
    val key: String = ""
)
