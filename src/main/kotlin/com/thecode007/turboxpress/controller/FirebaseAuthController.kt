package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.FirebaseLoginRequest
import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.service.FirebaseAuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Entry point for all 3 mobile apps.
 *
 * The client sends the Firebase ID token obtained after OTP verification
 * along with the app they are logging into (targetRole). This controller
 * delegates fully to [FirebaseAuthService.processUserLogin].
 *
 * Endpoint:
 *   POST /api/auth/firebase/login
 *   Body: { "firebaseToken": "...", "targetRole": "CUSTOMER" }
 *   Response: LoginResponse with a role-scoped JWT
 *
 * Note: The legacy /api/auth/verify-firebase-token endpoint in
 * [AuthVerifyController] is still active for backward compatibility
 * but should be migrated to this endpoint gradually.
 */
@RestController
@RequestMapping("/api/auth/firebase")
class FirebaseAuthController(
    private val firebaseAuthService: FirebaseAuthService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: FirebaseLoginRequest): ResponseEntity<Any> {
        return try {
            val response: LoginResponse = firebaseAuthService.processUserLogin(request)
            ResponseEntity.ok(response)
        } catch (e: org.springframework.security.authentication.BadCredentialsException) {
            ResponseEntity.status(401).body(
                mapOf("error" to "INVALID_FIREBASE_TOKEN", "message" to e.message)
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
}
