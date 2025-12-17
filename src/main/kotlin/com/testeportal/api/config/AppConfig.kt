package com.testeportal.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
data class AppConfig(
    var cors: CorsConfig = CorsConfig(),
    var llm: LlmConfig = LlmConfig(),
    var validation: ValidationConfig = ValidationConfig()
)

data class CorsConfig(
    var enabled: Boolean = true
)

data class LlmConfig(
    var openai: OpenAIConfig = OpenAIConfig(),
    var gemini: GeminiConfig = GeminiConfig()
)

data class OpenAIConfig(
    var apiKey: String = "",
    var model: String = "gpt-4o-mini",
    var scoringModel: String = "gpt-4o-mini",
    var timeout: Long = 60000
)

data class GeminiConfig(
    var apiKey: String = "",
    var model: String = "gemini-1.5-flash",
    var scoringModel: String = "gemini-1.5-flash",
    var timeout: Long = 60000
)

data class ValidationConfig(
    var subjective: SubjectiveConfig = SubjectiveConfig()
)

data class SubjectiveConfig(
    var passingScore: Double = 0.5
)
