package com.example.demo.controller

import com.example.demo.entity.User
import com.example.demo.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getAllUsers(): List<User> = userRepository.findAll()

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<User> {
        return userRepository.findById(id)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.notFound().build())
    }

    @PostMapping
    fun createUser(@RequestBody user: User): User {
        return userRepository.save(user)
    }

    @GetMapping("/email/{email}")
    fun getUserByEmail(@PathVariable email: String): ResponseEntity<User> {
        return userRepository.findByEmail(email)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }
}