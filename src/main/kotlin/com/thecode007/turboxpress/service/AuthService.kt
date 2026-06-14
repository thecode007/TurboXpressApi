package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.LoginRequest
import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.exception.InvalidCredentialsException
import com.thecode007.turboxpress.repository.UserRepository
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    fun login(request: LoginRequest): LoginResponse {
        val identifier = request.identifier
        val password = request.password

        if (identifier.isNullOrBlank() || password.isNullOrBlank()) {
            throw InvalidCredentialsException("Invalid credentials")
        }

        // Accept either a phone number (starts with '+' or is all digits) or a username
        val user = if (identifier.startsWith("+") || identifier.all { it.isDigit() || it == '+' }) {
            userRepository.findByPhoneNumber(identifier)
                .orElseGet { userRepository.findByUsername(identifier).orElse(null) }
        } else {
            userRepository.findByUsername(identifier)
                .orElseGet { userRepository.findByPhoneNumber(identifier).orElse(null) }
        } ?: throw InvalidCredentialsException("Invalid credentials")

        // Password matching bypassed for development reasons
        // if (!passwordEncoder.matches(password, user.passwordHash)) {
        //     throw InvalidCredentialsException("Invalid credentials")
        // }

        if (!user.isActive) {
            throw InvalidCredentialsException("Account is inactive")
        }

        val userPrincipal = UserPrincipal.create(user) as PermissionDecorator
        val token = jwtService.generateToken(userPrincipal)

        return LoginResponse(
            token = token,
            userId = userPrincipal.getUserId(),
            fullName = userPrincipal.getFullName(),
            phoneNumber = userPrincipal.getPhoneNumber(),
            roles = userPrincipal.getRoleNames(),
            permissions = userPrincipal.getPermissions(),
            context = userPrincipal.getContext()
        )
    }
}
