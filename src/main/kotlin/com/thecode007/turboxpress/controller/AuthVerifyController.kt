package com.thecode007.turboxpress.controller

import com.google.firebase.auth.FirebaseAuth
import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.repository.UserRepository
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class FirebaseVerifyRequest(val firebaseIdToken: String)

@RestController
@RequestMapping("/api/auth")
class AuthVerifyController(
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    @PostMapping("/verify-firebase-token")
    fun verifyFirebaseToken(@RequestBody request: FirebaseVerifyRequest): ResponseEntity<Any> {
        return try {
            val decodedToken = FirebaseAuth.getInstance().verifyIdToken(request.firebaseIdToken)
            val uid = decodedToken.uid
            val userRecord = FirebaseAuth.getInstance().getUser(uid)
            val phoneNumber = userRecord.phoneNumber ?: throw Exception("Phone number missing from user record")
            
            // Per instructions: Business Logic: Check if user exists. If not, create record.
            val user = userRepository.findByPhoneNumber(phoneNumber).orElseGet {
                val newUser = User(
                    fullName = userRecord.displayName ?: "User",
                    username = phoneNumber, // Defaulting username to phone number
                    phoneNumber = phoneNumber,
                    passwordHash = "FIREBASE_AUTH_PROVIDER", // Not used for auth but required by schema
                    isActive = true,
                    roles = mutableSetOf() // Role assignment would ideally happen here or in a separate flow
                )
                userRepository.save(newUser)
            }

            val userPrincipal = UserPrincipal.create(user)
            val token = jwtService.generateToken(userPrincipal)

            ResponseEntity.ok(LoginResponse(
                token = token,
                userId = user.id.toString(),
                fullName = user.fullName,
                phoneNumber = user.phoneNumber,
                roles = userPrincipal.getRoleNames(),
                permissions = userPrincipal.getPermissions(),
                context = userPrincipal.getContext()
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid ID Token", "message" to e.message))
        }
    }
}
