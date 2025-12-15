package com.example.demo.controller

import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
class InfoController(
    private val environment: Environment
) {
    
    @GetMapping("/info")
    fun getInfo(): Map<String, String> {
        val activeProfile = environment.activeProfiles.firstOrNull() ?: "default"
        val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        
        return mapOf(
            "activeProfile" to activeProfile,
            "currentTime" to currentTime
        )
    }
}
