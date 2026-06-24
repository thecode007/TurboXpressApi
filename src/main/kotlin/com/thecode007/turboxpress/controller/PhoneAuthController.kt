package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.dto.PhoneLoginRequest
import com.thecode007.turboxpress.dto.SetPasswordRequest
import com.thecode007.turboxpress.service.PhoneAuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/phone")
class PhoneAuthController(
    private val phoneAuthService: PhoneAuthService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: PhoneLoginRequest): ResponseEntity<Any> {
        return try {
            val response: LoginResponse = phoneAuthService.processPhoneLogin(request)
            ResponseEntity.ok(response)
        } catch (e: org.springframework.security.authentication.BadCredentialsException) {
            ResponseEntity.status(401).body(
                mapOf("error" to "INVALID_CREDENTIALS", "message" to e.message)
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf("error" to "BAD_REQUEST", "message" to e.message)
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to "INTERNAL_ERROR", "message" to e.message)
            )
        }
    }

    @PostMapping("/set-password")
    fun setPassword(@Valid @RequestBody request: SetPasswordRequest): ResponseEntity<Any> {
        return try {
            val response: LoginResponse = phoneAuthService.setPassword(request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf("error" to "BAD_REQUEST", "message" to e.message)
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to "INTERNAL_ERROR", "message" to e.message)
            )
        }
    }
}
